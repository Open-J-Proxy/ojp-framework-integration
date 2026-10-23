"""Mirror of UserControllerIT."""

import pytest

DATE_TIME_FORMAT = "%Y-%m-%dT%H:%M:%S"


@pytest.fixture(autouse=True)
def setup(repos):
    repos.reviews.delete_all()
    repos.users.delete_all()


def test_create_and_get_user(client, repos):
    json = {"username": "alice", "email": "alice@example.com", "createdAt": "2024-01-15T10:30:00"}
    response = client.post("/users", json=json)
    assert response.status_code == 200
    body = response.json()
    assert body["id"] is not None
    assert body["username"] == "alice"
    assert body["createdAt"] is not None

    user = repos.users.find_all()[0]
    assert user.created_at.strftime(DATE_TIME_FORMAT) == "2024-01-15T10:30:00"

    response = client.get(f"/users/{user.id}")
    assert response.status_code == 200
    assert response.json()["email"] == "alice@example.com"
    assert response.json()["createdAt"] is not None

    retrieved = repos.users.find_by_id(user.id)
    assert retrieved is not None
    assert retrieved.created_at.strftime(DATE_TIME_FORMAT) == "2024-01-15T10:30:00"


def test_update_user(client, repos):
    response = client.post("/users", json={"username": "bob", "email": "bob@example.com"})
    assert response.status_code == 200

    user = repos.users.find_all()[0]

    response = client.put(f"/users/{user.id}", json={"username": "bob_updated", "email": "bob_updated@example.com"})
    assert response.status_code == 200
    assert response.json()["username"] == "bob_updated"
    assert response.json()["email"] == "bob_updated@example.com"


def test_delete_user(client, repos):
    response = client.post("/users", json={"username": "charlie", "email": "charlie@example.com"})
    assert response.status_code == 200

    user = repos.users.find_all()[0]

    assert client.delete(f"/users/{user.id}").status_code == 204
    assert client.get(f"/users/{user.id}").status_code == 404
