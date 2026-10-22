"""Integration fixtures use the actual two OJP databases, never test doubles."""

import os
from decimal import Decimal

import django
import pytest

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "shopservice.settings")
django.setup()

from django.core.management import call_command
from django.db import connections
from django.test import Client

from shopservice.models import Order, OrderItem, Product, Review, User
from shopservice.repositories import Repositories


class JsonClient(Client):
    def post(self, path, *, json=None, **kwargs):
        return super().post(path, data=json, content_type="application/json", **kwargs)

    def put(self, path, *, json=None, **kwargs):
        return super().put(path, data=json, content_type="application/json", **kwargs)


@pytest.fixture(scope="session")
def databases():
    for alias in ("default", "checkout"):
        call_command("migrate", database=alias, interactive=False, verbosity=0)
    yield
    connections.close_all()


@pytest.fixture
def repos(databases):
    repositories = Repositories.create()

    def clean():
        for repository in (
            repositories.order_items, repositories.orders, repositories.reviews,
            repositories.users, repositories.products,
        ):
            repository.delete_all()

    clean()
    yield repositories
    clean()
    connections.close_all()


@pytest.fixture
def client():
    return JsonClient()


@pytest.fixture
def catalog(repos):
    user = repos.users.save(new_user("bob", "bob@example.com"))
    product = repos.products.save(new_product("Widget", "9.99"))
    return user, product


@pytest.fixture
def checkout(repos, catalog):
    user, product = catalog
    order = repos.orders.save(new_order(user.id))
    return user, product, order


def new_user(username, email, created_at=None):
    return User(username=username, email=email, created_at=created_at)


def new_product(name, price):
    return Product(name=name, price=Decimal(price))


def new_review(user, product, rating, comment):
    return Review(user=user, product=product, rating=rating, comment=comment)


def new_order(user_id):
    return Order(user_id=user_id)


def new_order_item(order, product_id, quantity):
    return OrderItem(order_id=order.id, product_id=product_id, quantity=quantity)
