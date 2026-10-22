"""Database-free parsing, serialization, routing and connection configuration."""

from decimal import Decimal
from types import SimpleNamespace

import pyodbc
import pytest
from django.conf import settings

from shopservice.config import brace, database_settings
from shopservice.models import Order, OrderItem, Product, Review, User
from shopservice.repositories import PageRequest
from shopservice.routers import ShopRouter
from shopservice.serialization import (
    BadRequest, as_object, nested_id, opt_datetime, opt_int, page_json,
    page_request, required_decimal,
)


@pytest.mark.parametrize("value,expected", [(None, None), (1, 1), ("42", 42), (3.0, 3), (-3, -3)])
def test_integer_parsing(value, expected):
    assert opt_int({"id": value}, "id") == expected


@pytest.mark.parametrize("value", [True, False, 1.5, [], {}, "bad", 2**63, -(2**63) - 1])
def test_invalid_integer_parsing(value):
    with pytest.raises(BadRequest):
        opt_int({"id": value}, "id")


@pytest.mark.parametrize("value", [None, True, [], "NaN", "Infinity", "-Infinity", "bad", "1e100000"])
def test_invalid_decimal_parsing(value):
    with pytest.raises(BadRequest):
        required_decimal({"price": value}, "price")


def test_decimal_and_datetime_parsing():
    assert required_decimal({"price": "19.99"}, "price") == Decimal("19.99")
    assert opt_datetime({"date": "2024-01-15T10:30:00"}, "date").isoformat() == "2024-01-15T10:30:00"
    with pytest.raises(BadRequest):
        opt_datetime({"date": "bad"}, "date")
    with pytest.raises(BadRequest):
        as_object([])
    assert nested_id({"user": {"id": "42"}}, "user") == 42
    assert nested_id({"user": 42}, "user") is None


@pytest.mark.parametrize("page,size,expected", [
    (None, None, PageRequest()), ("bad", "0", PageRequest()), ("-1", "-2", PageRequest()),
    ("1", "2", PageRequest(1, 2)), ("0", "9999", PageRequest(0, 2000)),
])
def test_page_input_defaults_and_limits(page, size, expected):
    assert page_request(page, size) == expected


def test_empty_page_envelope():
    page = page_json([], 0, PageRequest())
    assert page["content"] == []
    assert page["first"] and page["last"] and page["empty"]
    assert page["totalElements"] == page["totalPages"] == page["numberOfElements"] == 0
    assert page["pageable"]["sort"] == {"sorted": False, "unsorted": True, "empty": True}


def test_database_routing_and_migrations():
    router = ShopRouter()
    for model in (User, Product, Review):
        assert router.db_for_read(model) == router.db_for_write(model) == "default"
    for model in (Order, OrderItem):
        assert router.db_for_read(model) == router.db_for_write(model) == "checkout"
    assert router.allow_migrate("default", "catalog")
    assert not router.allow_migrate("checkout", "catalog")
    assert router.allow_migrate("checkout", "checkout")
    assert not router.allow_migrate("default", "checkout")
    assert router.allow_relation(User(), Product())
    assert router.allow_relation(Order(), OrderItem())
    assert not router.allow_relation(User(), Order())
    unknown = SimpleNamespace(_meta=SimpleNamespace(app_label="unknown"))
    assert router.allow_relation(unknown, unknown) is None


def test_no_cross_database_foreign_keys():
    assert not Order._meta.get_field("user_id").is_relation
    assert not OrderItem._meta.get_field("product_id").is_relation
    assert Review._meta.get_field("user").remote_field.model is User
    assert OrderItem._meta.get_field("order").remote_field.model is Order


def test_connection_string_escaping_and_no_pooling(monkeypatch):
    for key in ("DB_HOST", "DB_PORT", "CATALOG_DB", "CHECKOUT_DB", "ODBC_DRIVER", "OJP_SERVER"):
        monkeypatch.delenv(key, raising=False)
    monkeypatch.setenv("DB_USER", "user")
    monkeypatch.setenv("DB_PASSWORD", "a};injected=value")
    config = database_settings("catalog")
    assert config["ENGINE"] == "shopservice.odbc_backend"
    assert config["CONN_MAX_AGE"] == 0
    assert not pyodbc.pooling
    connection_string = config["OPTIONS"]["connection_string"]
    assert "DATABASE={jdbc:postgresql://localhost:5432/django_catalog_db};" in connection_string
    assert "******;injected=value};" in connection_string
    assert brace("a}b") == "{a}}b}"
    assert set(settings.DATABASES) == {"default", "checkout"}


@pytest.mark.parametrize("key,value", [
    ("DB_PORT", "0"), ("DB_PORT", "65536"), ("DB_PORT", "x"),
    ("DB_HOST", "host;injected=x"), ("CATALOG_DB", "name;injected=x"),
])
def test_invalid_database_configuration(monkeypatch, key, value):
    monkeypatch.setenv(key, value)
    with pytest.raises(ValueError):
        database_settings("catalog")
