"""Mirror of ProductControllerIT."""

import pytest


@pytest.fixture(autouse=True)
def setup(repos):
    # Reviews reference products, so they are removed first (the Spring suite relies on class ordering).
    repos.reviews.delete_all()
    repos.products.delete_all()


def test_create_and_get_product(client, repos):
    response = client.post("/products", json={"name": "Widget", "price": 19.99})
    assert response.status_code == 200
    assert response.json()["id"] is not None
    assert response.json()["name"] == "Widget"

    product = repos.products.find_all()[0]
    response = client.get(f"/products/{product.id}")
    assert response.status_code == 200
    assert response.json()["price"] == 19.99


def test_update_product(client, repos):
    assert client.post("/products", json={"name": "Gadget", "price": 10.50}).status_code == 200

    product = repos.products.find_all()[0]

    response = client.put(f"/products/{product.id}", json={"name": "Gadget Updated", "price": 15.75})
    assert response.status_code == 200
    assert response.json()["name"] == "Gadget Updated"
    assert response.json()["price"] == 15.75


def test_delete_product(client, repos):
    assert client.post("/products", json={"name": "ToDelete", "price": 5.00}).status_code == 200

    product = repos.products.find_all()[0]

    assert client.delete(f"/products/{product.id}").status_code == 204
    assert client.get(f"/products/{product.id}").status_code == 404
