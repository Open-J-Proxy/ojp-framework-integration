"""Mirror of CatalogRepositoryIT."""

import pytest

from conftest import new_product, new_review, new_user


@pytest.fixture(autouse=True)
def setup(repos):
    repos.reviews.delete_all()
    repos.users.delete_all()
    repos.products.delete_all()


# ── User ────────────────────────────────────────────────────────────────────


def test_user_save_all(repos):
    saved = repos.users.save_all([new_user("alice", "alice@example.com"), new_user("bob", "bob@example.com")])

    assert len(saved) == 2
    assert all(u.id is not None for u in saved)


def test_user_find_all(repos):
    repos.users.save_all([
        new_user("alice", "alice@example.com"),
        new_user("bob", "bob@example.com"),
        new_user("charlie", "charlie@example.com"),
    ])

    assert len(repos.users.find_all()) == 3


def test_user_delete_all(repos):
    repos.users.save_all([new_user("alice", "alice@example.com"), new_user("bob", "bob@example.com")])
    assert repos.users.count() == 2

    repos.users.delete_all()

    assert repos.users.find_all() == []


# ── Product ─────────────────────────────────────────────────────────────────


def test_product_save_all(repos):
    saved = repos.products.save_all([new_product("Widget", "9.99"), new_product("Gadget", "19.99")])

    assert len(saved) == 2
    assert all(p.id is not None for p in saved)


def test_product_find_all(repos):
    repos.products.save_all([
        new_product("Widget", "9.99"),
        new_product("Gadget", "19.99"),
        new_product("Doohickey", "4.50"),
    ])

    assert len(repos.products.find_all()) == 3


def test_product_delete_all(repos):
    repos.products.save_all([new_product("Widget", "9.99"), new_product("Gadget", "19.99")])
    assert repos.products.count() == 2

    repos.products.delete_all()

    assert repos.products.find_all() == []


# ── Review ──────────────────────────────────────────────────────────────────


def test_review_save_all(repos):
    u = repos.users.save(new_user("alice", "alice@example.com"))
    p = repos.products.save(new_product("Widget", "9.99"))

    saved = repos.reviews.save_all([new_review(u, p, 5, "Excellent!"), new_review(u, p, 3, "Average")])

    assert len(saved) == 2
    assert all(r.id is not None for r in saved)


def test_review_find_all(repos):
    u = repos.users.save(new_user("alice", "alice@example.com"))
    p = repos.products.save(new_product("Widget", "9.99"))
    repos.reviews.save_all([
        new_review(u, p, 5, "Excellent!"),
        new_review(u, p, 4, "Good"),
        new_review(u, p, 2, "Disappointing"),
    ])

    assert len(repos.reviews.find_all()) == 3


def test_review_delete_all(repos):
    u = repos.users.save(new_user("alice", "alice@example.com"))
    p = repos.products.save(new_product("Widget", "9.99"))
    repos.reviews.save_all([new_review(u, p, 5, "Excellent!"), new_review(u, p, 3, "Average")])
    assert repos.reviews.count() == 2

    repos.reviews.delete_all()

    assert repos.reviews.find_all() == []
