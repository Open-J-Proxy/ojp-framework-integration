"""Mirror of OrderAndOrderItemControllerIT."""

import pytest

from conftest import new_product, new_user


@pytest.fixture
def setup(repos):
    repos.order_items.delete_all()
    repos.orders.delete_all()
    repos.reviews.delete_all()  # reviews reference users/products (see test_product_controller_it)
    repos.users.delete_all()
    repos.products.delete_all()
    user = repos.users.save(new_user("bob", "bob@example.com"))
    product1 = repos.products.save(new_product("Thing", "10.00"))
    product2 = repos.products.save(new_product("Gadget", "5.50"))
    return user, product1, product2


def _order_json(user, product1, product2):
    return {
        "userId": user.id,
        "orderItems": [
            {"productId": product1.id, "quantity": 2},
            {"productId": product2.id, "quantity": 1},
        ],
    }


def test_create_order_with_items_and_get_items(client, setup):
    response = client.post("/orders", json=_order_json(*setup))
    assert response.status_code == 200
    order_id = response.json()["id"]
    assert order_id is not None

    response = client.get(f"/orders/{order_id}")
    assert response.status_code == 200
    assert len(response.json()["orderItems"]) == 2

    response = client.get(f"/orders/{order_id}/items")
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_delete_order(client, setup):
    response = client.post("/orders", json=_order_json(*setup))
    assert response.status_code == 200
    order_id = response.json()["id"]
    assert order_id is not None

    assert client.delete(f"/orders/{order_id}").status_code == 204
    assert client.get(f"/orders/{order_id}").status_code == 404
