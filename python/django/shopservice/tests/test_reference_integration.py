"""The same 30 repository and controller scenarios as the reference service."""

import pytest

from conftest import new_order, new_order_item, new_product, new_review, new_user

pytestmark = pytest.mark.integration


@pytest.fixture(autouse=True)
def isolated(repos):
    pass


def entities(kind, repos):
    if kind == "users":
        return [new_user("alice", "alice@example.com"), new_user("bob", "bob@example.com")]
    if kind == "products":
        return [new_product("Widget", "9.99"), new_product("Gadget", "19.99")]
    if kind == "orders":
        return [new_order(1), new_order(2)]
    if kind == "reviews":
        user = repos.users.save(new_user("alice", "alice@example.com"))
        product = repos.products.save(new_product("Widget", "9.99"))
        return [new_review(user, product, 5, "Excellent!"), new_review(user, product, 3, "Average")]
    order = repos.orders.save(new_order(1))
    return [new_order_item(order, 10, 2), new_order_item(order, 20, 1)]


@pytest.mark.parametrize("kind", ["users", "products", "reviews", "orders", "order_items"])
@pytest.mark.parametrize("operation", ["save_all", "find_all", "delete_all"])
def test_repository_crud(kind, operation, repos):
    repo = getattr(repos, kind)
    saved = repo.save_all(entities(kind, repos))
    if operation == "save_all":
        assert len(saved) == 2
        assert all(entity.pk is not None for entity in saved)
    elif operation == "find_all":
        assert [entity.pk for entity in repo.find_all()] == [entity.pk for entity in saved]
    else:
        assert repo.count() == 2
        repo.delete_all()
        assert repo.find_all() == []


def test_create_and_get_user(client, repos):
    response = client.post("/users", json={
        "username": "alice", "email": "alice@example.com", "createdAt": "2024-01-15T10:30:00",
    })
    assert response.status_code == 200
    user = repos.users.find_by_id(response.json()["id"])
    assert user.created_at.isoformat() == "2024-01-15T10:30:00"
    assert client.get(f"/users/{user.pk}").json() == response.json()


def test_update_user(client, repos):
    user = repos.users.save(new_user("bob", "bob@example.com"))
    response = client.put(f"/users/{user.pk}", json={"username": "updated", "email": "updated@example.com"})
    assert response.status_code == 200
    assert response.json()["username"] == "updated"
    assert repos.users.find_by_id(user.pk).email == "updated@example.com"


def test_delete_user(client, repos):
    user = repos.users.save(new_user("charlie", "charlie@example.com"))
    assert client.delete(f"/users/{user.pk}").status_code == 204
    assert client.get(f"/users/{user.pk}").status_code == 404


def test_create_and_get_product(client, repos):
    response = client.post("/products", json={"name": "Widget", "price": 19.99})
    assert response.status_code == 200
    product = repos.products.find_by_id(response.json()["id"])
    assert str(product.price) == "19.99"
    assert client.get(f"/products/{product.pk}").json() == response.json()


def test_update_product(client, repos):
    product = repos.products.save(new_product("Gadget", "10.50"))
    response = client.put(f"/products/{product.pk}", json={"name": "Updated", "price": 15.75})
    assert response.status_code == 200
    assert response.json()["name"] == "Updated"
    assert response.json()["price"] == 15.75


def test_delete_product(client, repos):
    product = repos.products.save(new_product("ToDelete", "5.00"))
    assert client.delete(f"/products/{product.pk}").status_code == 204
    assert client.get(f"/products/{product.pk}").status_code == 404


def test_create_and_get_review(client, catalog):
    user, product = catalog
    response = client.post("/reviews", json={
        "user": {"id": user.pk}, "product": {"id": product.pk}, "rating": 5, "comment": "Awesome!",
    })
    assert response.status_code == 200
    review = response.json()
    assert review["rating"] == 5
    assert review["user"]["username"] == user.username
    assert review["product"]["price"] == 9.99
    assert client.get(f"/reviews/{review['id']}").json() == review
    assert client.get("/reviews").json()["content"] == [review]


def test_delete_review(client, repos, catalog):
    user, product = catalog
    review = repos.reviews.save(new_review(user, product, 3, "Not great"))
    assert client.delete(f"/reviews/{review.pk}").status_code == 204
    assert client.get(f"/reviews/{review.pk}").status_code == 404


def create_item(client, checkout, quantity=2):
    _, product, order = checkout
    response = client.post(f"/orders/{order.pk}/items", json={"productId": product.pk, "quantity": quantity})
    assert response.status_code == 200
    return response.json()


def test_create_order_item(client, checkout):
    item = create_item(client, checkout, 3)
    assert item["id"] is not None
    assert item == {"id": item["id"], "productId": checkout[1].pk, "quantity": 3}


def test_get_order_items(client, checkout):
    item = create_item(client, checkout)
    assert client.get(f"/orders/{checkout[2].pk}/items").json() == [item]


def test_get_order_item(client, checkout):
    item = create_item(client, checkout, 4)
    response = client.get(f"/orders/{checkout[2].pk}/items/{item['id']}")
    assert response.status_code == 200
    assert response.json() == item


def test_update_order_item(client, checkout):
    item = create_item(client, checkout, 1)
    response = client.put(f"/orders/{checkout[2].pk}/items/{item['id']}", json={
        "productId": checkout[1].pk, "quantity": 5,
    })
    assert response.status_code == 200
    assert response.json()["quantity"] == 5


def test_delete_order_item(client, checkout):
    item = create_item(client, checkout)
    path = f"/orders/{checkout[2].pk}/items/{item['id']}"
    assert client.delete(path).status_code == 204
    assert client.get(path).status_code == 404


def test_create_order_with_items_and_get_items(client, repos, catalog):
    user, product = catalog
    other = repos.products.save(new_product("Gadget", "5.50"))
    response = client.post("/orders", json={
        "userId": user.pk, "orderItems": [
            {"productId": product.pk, "quantity": 2}, {"productId": other.pk, "quantity": 1},
        ],
    })
    assert response.status_code == 200
    order = response.json()
    assert order["orderDate"] is not None
    assert len(order["orderItems"]) == 2
    assert client.get(f"/orders/{order['id']}").json() == order
    assert client.get(f"/orders/{order['id']}/items").json() == order["orderItems"]


def test_delete_order(client, repos, checkout):
    _, product, order = checkout
    item = repos.order_items.save(new_order_item(order, product.pk, 1))
    assert client.delete(f"/orders/{order.pk}").status_code == 204
    assert client.get(f"/orders/{order.pk}").status_code == 404
    assert repos.order_items.find_by_id(item.pk) is None
