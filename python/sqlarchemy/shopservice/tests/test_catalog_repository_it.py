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
    assert all(type(u.id) is int for u in saved)
    assert len({u.id for u in saved}) == len(saved)
    assert [repos.users.find_by_id(u.id).username for u in saved] == ["alice", "bob"]


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
    assert all(type(p.id) is int for p in saved)
    assert len({p.id for p in saved}) == len(saved)
    assert [repos.products.find_by_id(p.id).name for p in saved] == ["Widget", "Gadget"]


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
    assert all(type(r.id) is int for r in saved)
    assert len({r.id for r in saved}) == len(saved)
    loaded = [repos.reviews.find_by_id(r.id) for r in saved]
    assert [r.comment for r in loaded] == ["Excellent!", "Average"]
    for review in loaded:
        assert type(review.user_id) is int
        assert type(review.product_id) is int
        assert review.user_id == review.user.id == u.id
        assert review.product_id == review.product.id == p.id


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
