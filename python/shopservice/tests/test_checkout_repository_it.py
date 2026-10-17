"""Mirror of CheckoutRepositoryIT."""

import pytest

from conftest import new_order, new_order_item


@pytest.fixture(autouse=True)
def setup(repos):
    repos.order_items.delete_all()
    repos.orders.delete_all()


# ── Order ───────────────────────────────────────────────────────────────────


def test_order_save_all(repos):
    saved = repos.orders.save_all([new_order(1), new_order(2)])

    assert len(saved) == 2
    assert all(o.id is not None for o in saved)


def test_order_find_all(repos):
    repos.orders.save_all([new_order(1), new_order(2), new_order(3)])

    assert len(repos.orders.find_all()) == 3


def test_order_delete_all(repos):
    repos.orders.save_all([new_order(1), new_order(2)])
    assert repos.orders.count() == 2

    repos.orders.delete_all()

    assert repos.orders.find_all() == []


# ── OrderItem ───────────────────────────────────────────────────────────────


def test_order_item_save_all(repos):
    saved_order = repos.orders.save(new_order(1))

    saved = repos.order_items.save_all([new_order_item(saved_order, 10, 2), new_order_item(saved_order, 20, 1)])

    assert len(saved) == 2
    assert all(i.id is not None for i in saved)


def test_order_item_find_all(repos):
    saved_order = repos.orders.save(new_order(1))
    repos.order_items.save_all([
        new_order_item(saved_order, 10, 2),
        new_order_item(saved_order, 20, 1),
        new_order_item(saved_order, 30, 5),
    ])

    assert len(repos.order_items.find_all()) == 3


def test_order_item_delete_all(repos):
    saved_order = repos.orders.save(new_order(1))
    repos.order_items.save_all([new_order_item(saved_order, 10, 2), new_order_item(saved_order, 20, 1)])
    assert repos.order_items.count() == 2

    repos.order_items.delete_all()

    assert repos.order_items.find_all() == []
