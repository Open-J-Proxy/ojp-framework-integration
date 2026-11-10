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
    assert all(type(o.id) is int for o in saved)
    assert len({o.id for o in saved}) == len(saved)
    assert [repos.orders.find_by_id(o.id).user_id for o in saved] == [1, 2]


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
    assert all(type(i.id) is int for i in saved)
    assert len({i.id for i in saved}) == len(saved)
    loaded = repos.orders.find_by_id(saved_order.id)
    assert [i.product_id for i in loaded.order_items] == [10, 20]
    assert all(type(i.order_id) is int and i.order_id == loaded.id for i in loaded.order_items)


def test_cascaded_items_keep_integer_identity_relationships(repos):
    from shopservice.models import Order, OrderItem

    order = Order(user_id=1, order_items=[
        OrderItem(product_id=10, quantity=2),
        OrderItem(product_id=20, quantity=1),
    ])
    with repos.orders._sessions.begin() as session:
        session.add(order)
        session.flush()
        assert type(order.id) is int
        assert len({item.id for item in order.order_items}) == 2
        for item in order.order_items:
            assert type(item.id) is int
            assert type(item.order_id) is int
            assert item.order_id == order.id
            assert item.order is order
        session.expire_all()
        for item in order.order_items:
            assert item.order is order


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
