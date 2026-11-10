"""Offline regressions for native SQL Server returning through OJP."""

from types import SimpleNamespace
from unittest.mock import Mock

import pyodbc
import pytest
from sqlalchemy import delete, insert, update
from sqlalchemy.dialects.mssql.pyodbc import MSDialect_pyodbc
from sqlalchemy.orm import Session
from sqlalchemy.pool import NullPool

from shopservice.config import DatabaseConfig
from shopservice.database import _engine
from shopservice.models import CatalogBase, CheckoutBase, Identifier, Order, OrderItem, Product, Review, User


@pytest.fixture(params=["py_catalog_db", "py_checkout_db"])
def engine(request):
    engine = _engine(DatabaseConfig(), request.param)
    yield engine
    engine.dispose()


def test_engine_keeps_native_returning_defaults(engine):
    defaults = MSDialect_pyodbc(dbapi=pyodbc)
    for capability in (
        "insert_returning",
        "update_returning",
        "delete_returning",
        "use_insertmanyvalues",
        "insert_executemany_returning",
        "insert_executemany_returning_sort_by_parameter_order",
        "insertmanyvalues_implicit_sentinel",
        "use_scope_identity",
    ):
        assert getattr(engine.dialect, capability) == getattr(defaults, capability)
    assert engine.dialect.insert_returning
    assert engine.dialect.use_insertmanyvalues
    assert isinstance(engine.pool, NullPool)


@pytest.mark.parametrize("autocommit", [False, True])
def test_preconnect_transaction_attributes_are_preserved(autocommit):
    engine = _engine(DatabaseConfig(), "master", autocommit=autocommit)
    try:
        expected = {} if autocommit else {pyodbc.SQL_ATTR_AUTOCOMMIT: 0}
        with pytest.MonkeyPatch.context() as patch:
            connect = Mock(side_effect=RuntimeError("offline connection"))
            patch.setattr(engine.dialect, "connect", connect)
            with pytest.raises(RuntimeError, match="offline connection"):
                engine.raw_connection()
            assert connect.call_args.kwargs == {"autocommit": True, "attrs_before": expected}
    finally:
        engine.dispose()


@pytest.mark.parametrize(
    "model,values",
    [
        (User, {"username": "alice", "email": "alice@example.com"}),
        (Product, {"name": "Widget", "price": 10}),
        (Review, {"user_id": 1, "product_id": 2, "rating": 5}),
        (Order, {"user_id": 1}),
        (OrderItem, {"order_id": 1, "product_id": 2, "quantity": 3}),
    ],
)
def test_single_insert_implicitly_returns_generated_identity(engine, model, values):
    compiled = insert(model).values(**values).compile(dialect=engine.dialect)
    sql = str(compiled)
    assert "OUTPUT inserted.id" in sql
    assert compiled.implicit_returning == [model.__table__.c.id]
    assert "@@identity" not in sql.lower()
    assert "scope_identity" not in sql.lower()
    assert "RETURNING" not in sql


def test_multirow_insert_returning(engine):
    compiled = (
        insert(User)
        .values([
            {"username": "alice", "email": "alice@example.com"},
            {"username": "bob", "email": "bob@example.com"},
        ])
        .returning(User.id)
        .compile(dialect=engine.dialect)
    )
    assert "OUTPUT inserted.id VALUES (?, ?), (?, ?)" in str(compiled)


def test_ordered_batch_returning_sorts_text_identities_numerically(engine):
    compiled = insert(User).returning(User.id, sort_by_parameter_order=True).compile(
        dialect=engine.dialect,
        column_keys=["username", "email"],
        for_executemany=True,
    )
    assert "OUTPUT inserted.id, inserted.id AS id__1" in str(compiled)
    assert "ORDER BY sen_counter" in str(compiled)
    context = engine.dialect.execution_ctx_cls()
    context.compiled = compiled
    context.execution_options = {}
    context.compiled_parameters = [
        {"username": "alice", "email": "alice@example.com"},
        {"username": "bob", "email": "bob@example.com"},
    ]
    cursor = SimpleNamespace(fetchall=lambda: [("10", "10"), ("9", "9")])
    batches = list(engine.dialect._deliver_insertmanyvalues_batches(
        None,
        cursor,
        str(compiled),
        [("alice", "alice@example.com"), ("bob", "bob@example.com")],
        None,
        context,
    ))
    assert len(batches) == 1
    assert not batches[0].is_downgraded
    assert "VALUES (?, ?, 0), (?, ?, 1)" in batches[0].replaced_statement
    assert context._insertmanyvalues_rows == [("9", 9), ("10", 10)]
    processor = User.__table__.c.id.type.dialect_impl(engine.dialect).result_processor(
        engine.dialect, str
    )
    assert [processor(row[0]) for row in context._insertmanyvalues_rows] == [9, 10]


@pytest.mark.parametrize("result_type", [str, int])
def test_orm_flush_maps_out_of_order_batch_identities(engine, monkeypatch, result_type):
    cursor = Mock()
    cursor.description = [
        ("id", result_type, None, None, None, None, None),
        ("id__1", result_type, None, None, None, None, None),
    ]
    # OUTPUT order is not guaranteed, and text IDs straddle a decimal boundary.
    cursor.fetchall.return_value = [
        (result_type(identity), result_type(identity)) for identity in reversed(range(9, 21))
    ]
    cursor.rowcount = 12
    connection = Mock()
    connection.cursor.return_value = cursor
    monkeypatch.setattr(engine.dialect, "connect", Mock(return_value=connection))
    monkeypatch.setattr(engine.dialect, "initialize", Mock())

    with Session(engine) as session:
        users = [
            User(username=f"user{i}", email=f"user{i}@example.com") for i in range(12)
        ]
        session.add_all(users)
        session.flush()

        assert [user.id for user in users] == list(range(9, 21))
        assert all(type(user.id) is int for user in users)
        assert all(session.get(User, user.id) is user for user in users)
        assert len(session.identity_map) == 12
        cursor.execute.assert_called_once()
        cursor.executemany.assert_not_called()
        sql, parameters = cursor.execute.call_args.args
        assert "OUTPUT inserted.id, inserted.id AS id__1" in sql
        assert "ORDER BY sen_counter" in sql
        assert parameters[::3] == tuple(user.username for user in users)


@pytest.mark.parametrize(
    "statement,output",
    [
        (update(User).where(User.id == 1).values(username="updated").returning(User.id), "inserted"),
        (delete(User).where(User.id == 1).returning(User.id), "deleted"),
    ],
)
def test_update_delete_returning_uses_native_output(engine, statement, output):
    sql = str(statement.compile(dialect=engine.dialect))
    assert f"OUTPUT {output}.id" in sql
    assert "RETURNING" not in sql


@pytest.mark.parametrize(
    "statement,output",
    [
        (update(User.__table__).where(User.id == 9).values(username="updated").returning(User.id), "inserted"),
        (delete(User.__table__).where(User.id == 9).returning(User.id), "deleted"),
    ],
)
def test_update_delete_returning_execution_preserves_rows_and_rowcount(
    engine, monkeypatch, statement, output
):
    cursor = Mock()
    cursor.description = [("id", str, None, None, None, None, None)]
    cursor.fetchall.return_value = [("9",)]
    cursor.rowcount = 1
    connection = Mock()
    connection.cursor.return_value = cursor
    monkeypatch.setattr(engine.dialect, "connect", Mock(return_value=connection))
    monkeypatch.setattr(engine.dialect, "initialize", Mock())

    assert issubclass(engine.dialect.execution_ctx_cls, MSDialect_pyodbc.execution_ctx_cls)
    with engine.begin() as conn:
        result = conn.execute(statement)
        assert result.context.compiled._insertmanyvalues is None
        assert result.rowcount == 1
        assert result.scalars().all() == [9]
        assert f"OUTPUT {output}.id" in cursor.execute.call_args.args[0]
        rows = [("9",)]
        assert result.context.fetchall_for_returning(SimpleNamespace(fetchall=lambda: rows)) is rows
    connection.commit.assert_called_once()


@pytest.mark.parametrize("metadata", [CatalogBase.metadata, CheckoutBase.metadata])
@pytest.mark.parametrize("value", [None, 9, "9", "10", "9223372036854775807"])
def test_all_identifier_results_are_integers(engine, metadata, value):
    for table in metadata.tables.values():
        for column in table.columns:
            if column.name != "id" and not column.name.endswith("_id"):
                continue
            assert isinstance(column.type, Identifier)
            processor = column.type.dialect_impl(engine.dialect).result_processor(engine.dialect, str)
            result = processor(value)
            assert result == (None if value is None else int(value))
            assert result is None or type(result) is int


def test_nonbatch_returning_rows_are_not_rewritten(engine):
    context = engine.dialect.execution_ctx_cls()
    context.compiled = insert(User).values(username="alice", email="alice@example.com").compile(
        dialect=engine.dialect
    )
    rows = [("9",)]
    assert context.fetchall_for_returning(SimpleNamespace(fetchall=lambda: rows)) is rows
