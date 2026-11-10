"""Mirror of OrderItemControllerIT."""

import pytest

from conftest import new_order, new_product, new_user


@pytest.fixture
def setup(repos):
    repos.order_items.delete_all()
    repos.orders.delete_all()
    user = repos.users.save(new_user("testuser", "testuser@example.com"))
    product = repos.products.save(new_product("TestProduct", "25.99"))
    order = repos.orders.save(new_order(user.id))
    return product, order


def _create_item(client, order, product, quantity):
    response = client.post(f"/orders/{order.id}/items", json={"productId": product.id, "quantity": quantity})
    assert response.status_code == 200
    return response.json()


def test_create_order_item(client, setup):
    product, order = setup

    response = client.post(f"/orders/{order.id}/items", json={"productId": product.id, "quantity": 3})
    assert response.status_code == 200
    body = response.json()
    assert body["id"] is not None
    assert body["quantity"] == 3
    assert body["productId"] == product.id


def test_get_order_items(client, setup):
    product, order = setup
    _create_item(client, order, product, 2)

    response = client.get(f"/orders/{order.id}/items")
    assert response.status_code == 200
    assert len(response.json()) == 1
    assert response.json()[0]["quantity"] == 2


def test_get_order_item(client, setup):
    product, order = setup
    item = _create_item(client, order, product, 4)

    response = client.get(f"/orders/{order.id}/items/{item['id']}")
    assert response.status_code == 200
    assert response.json()["id"] == item["id"]
    assert response.json()["quantity"] == 4


def test_update_order_item(client, setup):
    product, order = setup
    item = _create_item(client, order, product, 1)

    response = client.put(f"/orders/{order.id}/items/{item['id']}", json={"productId": product.id, "quantity": 5})
    assert response.status_code == 200
    assert response.json()["quantity"] == 5


def test_delete_order_item(client, setup):
    product, order = setup
    item = _create_item(client, order, product, 1)

    assert client.delete(f"/orders/{order.id}/items/{item['id']}").status_code == 204
    assert client.get(f"/orders/{order.id}/items/{item['id']}").status_code == 404
