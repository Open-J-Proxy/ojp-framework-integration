"""Multi-database correctness, error contracts and atomic aggregate changes."""

import json
from datetime import datetime

import pytest
from django.db import connections, transaction

from conftest import new_order, new_order_item, new_product, new_review, new_user
from shopservice.models import OrderItem

pytestmark = pytest.mark.integration


@pytest.fixture(autouse=True)
def isolated(repos):
    pass


@pytest.mark.parametrize("resource", ["users", "products", "reviews", "orders"])
def test_missing_resources(client, resource):
    for method in (client.get, client.delete):
        assert method(f"/{resource}/9223372036854775807").status_code == 404
    assert client.put(f"/{resource}/9223372036854775807", json={}).status_code == 404


@pytest.mark.parametrize("data", [[], None, {"name": "x"}, {"name": "x", "price": True},
                                      {"name": "x", "price": "NaN"}, {"name": "x", "price": "Infinity"}])
def test_invalid_product_body(client, data):
    response = client.generic("POST", "/products", data=json.dumps(data),
                              content_type="application/json")
    assert response.status_code == 400


def test_malformed_json(client):
    assert client.generic("POST", "/users", data="{", content_type="application/json").status_code == 400


def test_product_collection_put_is_nonpersisting(client, repos):
    response = client.put("/products", json={"id": 42, "name": "ignored", "price": 1})
    assert response.status_code == 201
    assert response["Location"] == "/products/42"
    assert not response.content
    assert repos.products.count() == 0
    assert client.put("/products", json={})["Location"] == "/products/null"


def test_user_update_preserves_creation_time(client, repos):
    user = repos.users.save(new_user("alice", "alice@example.com", datetime(2024, 1, 15, 10, 30)))
    response = client.put(f"/users/{user.pk}", json={
        "username": "new", "email": "new@example.com", "createdAt": "2025-02-01T00:00:00",
    })
    assert response.json()["createdAt"] == "2024-01-15T10:30:00"


def test_pages_are_ordered_and_spring_compatible(client, repos):
    saved = repos.products.save_all([new_product(str(i), "1.00") for i in range(5)])
    response = client.get("/products?page=1&size=2")
    assert response.status_code == 200
    page = response.json()
    assert [item["id"] for item in page["content"]] == [item.pk for item in saved[2:4]]
    assert (page["totalElements"], page["totalPages"], page["numberOfElements"]) == (5, 3, 2)
    assert page["pageable"]["offset"] == 2
    assert not page["first"] and not page["last"]
    assert client.get("/products?page=99&size=2").json()["empty"]
    defaults = client.get("/products?page=bad&size=0").json()
    assert (defaults["number"], defaults["size"]) == (0, 20)
    assert client.get("/products?size=9999").json()["size"] == 2000


def test_reviews_filter_user_takes_precedence(client, repos, catalog):
    user, product = catalog
    other_user = repos.users.save(new_user("other", "other@example.com"))
    other_product = repos.products.save(new_product("other", "2.00"))
    first = repos.reviews.save(new_review(user, product, 5, "a"))
    repos.reviews.save(new_review(other_user, other_product, 2, "b"))
    assert client.get(f"/reviews?userId={user.pk}&productId={other_product.pk}").json()["totalElements"] == 1
    assert client.get(f"/reviews?productId={product.pk}").json()["content"][0]["id"] == first.pk
    assert client.get("/reviews?userId=not-an-id").status_code == 400


def test_review_update_ignores_invalid_references(client, repos, catalog):
    user, product = catalog
    review = repos.reviews.save(new_review(user, product, 5, "old"))
    response = client.put(f"/reviews/{review.pk}", json={
        "user": {"id": -1}, "product": {"id": -1}, "comment": "new",
    })
    assert response.status_code == 200
    assert response.json()["user"]["id"] == user.pk
    assert response.json()["product"]["id"] == product.pk
    assert response.json()["rating"] == 0
    assert response.json()["comment"] == "new"


def test_invalid_review_references_are_rejected(client):
    assert client.post("/reviews", json={"user": {"id": -1}, "product": {"id": -1}}).status_code == 400
    assert client.post("/reviews", json={"user": 1, "product": {}}).status_code == 400


def test_order_creation_validates_catalog_references(client, repos, catalog):
    user, product = catalog
    assert client.post("/orders", json={"userId": -1}).status_code == 400
    assert client.post("/orders", json={"userId": user.pk, "orderItems": []}).status_code == 400
    assert client.post("/orders", json={
        "userId": user.pk, "orderItems": [{"productId": -1}],
    }).status_code == 400
    assert repos.orders.count() == 0
    response = client.post("/orders", json={"userId": user.pk})
    assert response.status_code == 200
    assert response.json()["orderItems"] == []


def test_order_replacement_removes_orphans(client, repos, checkout):
    user, product, order = checkout
    old = repos.order_items.save(new_order_item(order, product.pk, 3))
    response = client.put(f"/orders/{order.pk}", json={
        "userId": user.pk, "orderDate": "2024-03-01T12:30:00",
        "orderItems": [{"productId": product.pk, "quantity": 9}],
    })
    assert response.status_code == 200
    assert response.json()["orderDate"] == "2024-03-01T12:30:00"
    assert response.json()["orderItems"][0]["quantity"] == 9
    assert repos.order_items.find_by_id(old.pk) is None
    assert repos.order_items.count() == 1


@pytest.mark.parametrize("items", [[], [{"productId": -1, "quantity": 1}], None])
def test_order_update_preserves_invalid_or_missing_items(client, repos, checkout, items):
    user, product, order = checkout
    old = repos.order_items.save(new_order_item(order, product.pk, 3))
    response = client.put(f"/orders/{order.pk}", json={"userId": -1, "orderItems": items})
    assert response.status_code == 200
    assert response.json()["userId"] == user.pk
    assert response.json()["orderItems"][0]["id"] == old.pk


def test_order_replacement_rolls_back_all_checkout_changes(repos, checkout, monkeypatch):
    _, product, order = checkout
    old = repos.order_items.save(new_order_item(order, product.pk, 3))
    original_save = OrderItem.save
    calls = 0

    def fail_second_insert(self, *args, **kwargs):
        nonlocal calls
        calls += 1
        if calls == 2:
            raise RuntimeError("injected write failure")
        return original_save(self, *args, **kwargs)

    monkeypatch.setattr(OrderItem, "save", fail_second_insert)
    order.user_id = -1
    with pytest.raises(RuntimeError, match="injected"):
        repos.orders.save_with_items(order, [
            OrderItem(product_id=product.pk, quantity=1), OrderItem(product_id=product.pk, quantity=2),
        ])
    assert repos.orders.find_by_id(order.pk).user_id != -1
    assert [item.pk for item in repos.order_items.find_by_order_id(order.pk)] == [old.pk]


@pytest.mark.parametrize("kind", ["products", "orders"])
def test_repository_save_all_rolls_back_each_database(repos, monkeypatch, kind):
    repository = getattr(repos, kind)
    original_save = repository.model.save
    calls = 0

    def fail_second_save(self, *args, **kwargs):
        nonlocal calls
        calls += 1
        if calls == 2:
            raise RuntimeError("injected batch failure")
        return original_save(self, *args, **kwargs)

    monkeypatch.setattr(repository.model, "save", fail_second_save)
    entities = (
        [new_product("first", "1.00"), new_product("second", "2.00")]
        if kind == "products" else [new_order(1), new_order(2)]
    )
    with pytest.raises(RuntimeError, match="injected batch"):
        repository.save_all(entities)
    assert repository.count() == 0


@pytest.mark.parametrize("kind", ["products", "orders"])
def test_nested_atomic_savepoint_rollback_preserves_outer_transaction(repos, kind):
    repository = getattr(repos, kind)

    def entity(name):
        return new_product(name, "1.00") if kind == "products" else new_order(1)

    with transaction.atomic(using=repository.alias):
        first = repository.save(entity("first"))
        with pytest.raises(RuntimeError, match="rollback inner"):
            with transaction.atomic(using=repository.alias):
                repository.save(entity("discarded"))
                raise RuntimeError("rollback inner")
        last = repository.save(entity("last"))
        assert repository.count() == 2
    assert [item.pk for item in repository.find_all()] == [first.pk, last.pk]


@pytest.mark.parametrize("kind", ["products", "orders"])
def test_model_save_returns_generated_id_in_one_statement(repos, kind):
    repository = getattr(repos, kind)
    entity = new_product("single", "1.25") if kind == "products" else new_order(11)
    statements = []

    def record(execute, sql, params, many, context):
        statements.append(sql)
        return execute(sql, params, many, context)

    with connections[repository.alias].execute_wrapper(record):
        entity.save(using=repository.alias)
    assert isinstance(entity.pk, int)
    assert len(statements) == 1
    assert statements[0].startswith("INSERT") and "RETURNING" in statements[0]
    assert repository.find_by_id(entity.pk) is not None


@pytest.mark.parametrize("kind", ["products", "orders"])
def test_bulk_create_returns_generated_ids_without_identity_queries(repos, kind):
    repository = getattr(repos, kind)
    entities = (
        [new_product("first", "1.25"), new_product("second", "2.50")]
        if kind == "products" else [new_order(11), new_order(22)]
    )
    statements = []

    def record(execute, sql, params, many, context):
        statements.append(sql)
        return execute(sql, params, many, context)

    with connections[repository.alias].execute_wrapper(record):
        saved = repository.model.objects.using(repository.alias).bulk_create(entities)
    assert all(isinstance(entity.pk, int) for entity in saved)
    assert len({entity.pk for entity in saved}) == 2
    assert [entity.pk for entity in repository.find_all()] == [entity.pk for entity in saved]
    inserts = [sql for sql in statements if sql.startswith("INSERT")]
    assert len(inserts) == 1 and "RETURNING" in inserts[0]
    assert not any("currval" in sql.lower() for sql in statements)
    if kind == "products":
        assert [entity.name for entity in repository.find_all()] == ["first", "second"]
    else:
        assert [entity.user_id for entity in repository.find_all()] == [11, 22]


def test_nested_item_ownership_and_defaults(client, repos, checkout):
    user, product, order = checkout
    other = repos.orders.save(new_order(user.pk))
    item = repos.order_items.save(new_order_item(order, product.pk, 2))
    path = f"/orders/{other.pk}/items/{item.pk}"
    assert client.get(path).status_code == 404
    assert client.put(path, json={}).status_code == 404
    assert client.delete(path).status_code == 404
    assert client.post("/orders/-1/items", json={"productId": product.pk}).status_code == 400
    assert client.post(f"/orders/{order.pk}/items", json={"productId": -1}).status_code == 400
    response = client.put(f"/orders/{order.pk}/items/{item.pk}", json={})
    assert response.json()["quantity"] == 0
    assert response.json()["productId"] == product.pk
    assert client.get("/orders/-1/items").json() == []


def test_tables_are_isolated_and_catalog_ids_are_not_foreign_keys(repos):
    for alias, expected, excluded in [
        ("default", {"django_users", "django_products", "django_reviews"}, {"django_orders", "django_order_items"}),
        ("checkout", {"django_orders", "django_order_items"}, {"django_users", "django_products", "django_reviews"}),
    ]:
        names = set(connections[alias].introspection.table_names())
        assert expected <= names
        assert names.isdisjoint(excluded)
    order = repos.orders.save(new_order(-123))
    item = repos.order_items.save(new_order_item(order, -456, 1))
    assert repos.orders.find_by_id(order.pk).user_id == -123
    assert repos.order_items.find_by_id(item.pk).product_id == -456
