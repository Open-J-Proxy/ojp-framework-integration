"""Mirror of ReviewControllerIT."""

import pytest

from conftest import new_product, new_user


@pytest.fixture
def setup(repos):
    repos.reviews.delete_all()
    repos.users.delete_all()
    repos.products.delete_all()
    user = repos.users.save(new_user("bob", "bob@example.com"))
    product = repos.products.save(new_product("SuperToy", "99.99"))
    return user, product


def test_create_and_get_review(client, setup):
    user, product = setup
    review_json = {"user": {"id": user.id}, "product": {"id": product.id}, "rating": 5, "comment": "Awesome!"}

    response = client.post("/reviews", json=review_json)
    assert response.status_code == 200
    assert response.json()["id"] is not None
    assert response.json()["rating"] == 5

    response = client.get("/reviews")
    assert response.status_code == 200
    assert response.json()["content"] != []


def test_delete_review(client, setup):
    user, product = setup
    review_json = {"user": {"id": user.id}, "product": {"id": product.id}, "rating": 3, "comment": "Not great"}

    response = client.post("/reviews", json=review_json)
    assert response.status_code == 200
    review_id = response.json()["id"]
    assert review_id is not None

    assert client.delete(f"/reviews/{review_id}").status_code == 204
    assert client.get(f"/reviews/{review_id}").status_code == 404
