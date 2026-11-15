"""Shared fixtures: the API runs in-process (the equivalent of Spring's MockMvc) against a real SQL Server.

The database connection is configured with the same environment variables as the application
(``DB_HOST``, ``DB_PORT``, ``DB_USER``, ``DB_PASSWORD``, ``ODBC_DRIVER``, ``CATALOG_DB``, ``CHECKOUT_DB``...).
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Iterator, Optional

import pytest
from fastapi.testclient import TestClient

from shopservice.app import create_app
from shopservice.models import Order, OrderItem, Product, Review, User
from shopservice.repositories import Repositories


@pytest.fixture(scope="session")
def client() -> Iterator[TestClient]:
    with TestClient(create_app()) as test_client:
        yield test_client


@pytest.fixture(scope="session")
def repos(client: TestClient) -> Repositories:
    return client.app.state.repositories


def new_user(username: str, email: str, created_at: Optional[datetime] = None) -> User:
    return User(username=username, email=email, created_at=created_at)


def new_product(name: str, price: str) -> Product:
    return Product(name=name, price=Decimal(price))


def new_review(user: User, product: Product, rating: int, comment: str) -> Review:
    return Review(user=user, product=product, rating=rating, comment=comment)


def new_order(user_id: int) -> Order:
    return Order(user_id=user_id)


def new_order_item(order: Order, product_id: int, quantity: int) -> OrderItem:
    return OrderItem(order_id=order.id, product_id=product_id, quantity=quantity)
