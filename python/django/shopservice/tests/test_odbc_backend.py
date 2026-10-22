"""Database-free contracts for the PostgreSQL adapter and OJP cursor transport."""

from copy import deepcopy
from datetime import datetime
from decimal import Decimal
from types import SimpleNamespace
from unittest.mock import MagicMock, Mock, patch

import pyodbc
import pytest
from django.conf import settings
from django.db import connections
from django.db.migrations.loader import MigrationLoader
from django.db.migrations.state import ProjectState

from shopservice.odbc_backend.base import CursorWrapper, DatabaseWrapper
from shopservice.odbc_backend.compiler import BaseSQLInsertCompiler, SQLCompiler, SQLInsertCompiler


@pytest.fixture
def backend():
    return DatabaseWrapper(deepcopy(settings.DATABASES["default"]), alias="default")


def test_new_connection_disables_pooling_and_sets_mode_before_connect(backend):
    raw_connection = Mock()
    params = backend.get_connection_params()
    with patch("shopservice.odbc_backend.base.pyodbc.connect", return_value=raw_connection) as connect:
        assert backend.get_new_connection(params) is raw_connection
    connect.assert_called_once_with(
        params["connection_string"], autocommit=True, attrs_before={}, timeout=15,
    )
    assert backend._odbc_autocommit is True
    assert pyodbc.pooling is False


def test_autocommit_transitions_reconnect_with_preconnect_attributes(backend):
    old_connection, transactional, restored = Mock(), Mock(), Mock()
    backend.connection = old_connection
    backend._odbc_autocommit = True
    with patch("shopservice.odbc_backend.base.pyodbc.connect", side_effect=[transactional, restored]) as connect:
        backend._set_autocommit(False)
        assert backend.connection is transactional
        assert backend._odbc_autocommit is False
        old_connection.close.assert_called_once_with()
        assert connect.call_args.kwargs == {
            "autocommit": True, "attrs_before": {pyodbc.SQL_ATTR_AUTOCOMMIT: 0}, "timeout": 15,
        }
        backend._set_autocommit(False)
        assert connect.call_count == 1
        backend._set_autocommit(True)
        assert backend.connection is restored
        assert backend._odbc_autocommit is True
        transactional.close.assert_called_once_with()
        assert connect.call_args.kwargs["attrs_before"] == {}
        assert connect.call_count == 2
    assert "autocommit" not in old_connection.__dict__
    assert "autocommit" not in transactional.__dict__


def test_failed_autocommit_reconnect_preserves_existing_connection(backend):
    old_connection = Mock()
    backend.connection = old_connection
    backend._odbc_autocommit = True
    with patch("shopservice.odbc_backend.base.pyodbc.connect", side_effect=pyodbc.Error("connect failed")):
        with pytest.raises(pyodbc.Error):
            backend._set_autocommit(False)
    assert backend.connection is old_connection
    assert backend._odbc_autocommit is True
    old_connection.close.assert_not_called()


@pytest.mark.parametrize("method,prefix", [
    ("savepoint_create_sql", "SAVEPOINT"),
    ("savepoint_rollback_sql", "ROLLBACK TO SAVEPOINT"),
    ("savepoint_commit_sql", "RELEASE SAVEPOINT"),
])
def test_savepoint_sql_uses_ojp_unquoted_identifiers(backend, method, prefix):
    sid = "s140380232854400_x1"
    sql = getattr(backend.ops, method)(sid)
    assert sql == f"{prefix} {sid}"
    raw_cursor = Mock()
    CursorWrapper(raw_cursor, backend).execute(sql)
    raw_cursor.execute.assert_called_once_with(sql, ())


@pytest.mark.parametrize("sid", [
    "", 's1"; DROP TABLE django_users; --', '"s1"', "s1 s2", "s1;", "1s", "s-é", None,
])
@pytest.mark.parametrize("method", [
    "savepoint_create_sql", "savepoint_rollback_sql", "savepoint_commit_sql",
])
def test_savepoint_sql_rejects_invalid_identifiers(backend, method, sid):
    with pytest.raises(ValueError, match="Invalid OJP savepoint identifier"):
        getattr(backend.ops, method)(sid)


@pytest.mark.parametrize("field_type,value,expected", [
    ("BigAutoField", "9223372036854775807", 9223372036854775807),
    ("AutoField", "42", 42),
    ("IntegerField", "-3", -3),
    ("BigIntegerField", None, None),
    ("DecimalField", "19.99", Decimal("19.99")),
    ("DecimalField", None, None),
    ("DateTimeField", "2024-01-15 10:30:00.123456", datetime(2024, 1, 15, 10, 30, 0, 123456)),
    ("DateTimeField", datetime(2024, 1, 15), datetime(2024, 1, 15)),
    ("DateTimeField", None, None),
    ("BooleanField", "t", True),
    ("BooleanField", "false", False),
    ("BooleanField", None, None),
])
def test_text_result_conversion(backend, field_type, value, expected):
    expression = SimpleNamespace(output_field=SimpleNamespace(get_internal_type=lambda: field_type))
    converters = backend.ops.get_db_converters(expression)
    assert converters
    for converter in converters:
        value = converter(value, expression, backend)
    assert value == expected


@pytest.mark.parametrize("table", ["django_users", "django_orders", "django_migrations"])
def test_generated_id_uses_parameterized_currval(backend, table):
    cursor = Mock()
    cursor.fetchone.return_value = ("123",)
    assert backend.ops.last_insert_id(cursor, table, "id") == 123
    cursor.execute.assert_called_once_with(
        "SELECT currval(pg_get_serial_sequence(%s, %s))", [table, "id"],
    )
    assert backend.features.can_return_columns_from_insert is False
    assert backend.features.can_return_rows_from_bulk_insert is False


@pytest.mark.parametrize("alias", ["default", "checkout"])
def test_insert_compiler_encloses_insert_and_generated_id_in_atomic(alias):
    compiler = object.__new__(SQLInsertCompiler)
    compiler.using = alias
    atomic = MagicMock()
    events = []
    atomic.__enter__.side_effect = lambda: events.append("enter")
    atomic.__exit__.side_effect = lambda *args: events.append("exit")
    fields = [object()]

    def execute(returning_fields):
        assert events == ["enter"]
        assert returning_fields is fields
        events.append("insert-and-currval")
        return [(123,)]

    with patch("shopservice.odbc_backend.compiler.transaction.atomic", return_value=atomic) as transaction:
        with patch.object(BaseSQLInsertCompiler, "execute_sql", side_effect=execute):
            assert compiler.execute_sql(fields) == [(123,)]
    transaction.assert_called_once_with(using=alias)
    assert events == ["enter", "insert-and-currval", "exit"]
    atomic.__exit__.assert_called_once_with(None, None, None)


def test_insert_compiler_propagates_failure_to_atomic_rollback():
    compiler = object.__new__(SQLInsertCompiler)
    compiler.using = "checkout"
    atomic = MagicMock()
    atomic.__exit__.return_value = False
    error = RuntimeError("insert failed")
    with patch("shopservice.odbc_backend.compiler.transaction.atomic", return_value=atomic):
        with patch.object(BaseSQLInsertCompiler, "execute_sql", side_effect=error):
            with pytest.raises(RuntimeError, match="insert failed"):
                compiler.execute_sql()
    assert atomic.__exit__.call_args.args[0] is RuntimeError
    assert atomic.__exit__.call_args.args[1] is error


def test_cursor_keeps_postgresql_group_by_sql_without_tsql_rewrite(backend):
    raw_cursor = Mock()
    cursor = CursorWrapper(raw_cursor, backend)
    sql = "SELECT user_id, COUNT(*) FROM django_orders WHERE user_id > %s GROUP BY user_id"
    cursor.execute(sql, [5])
    raw_cursor.execute.assert_called_once_with(
        "SELECT user_id, COUNT(*) FROM django_orders WHERE user_id > ? GROUP BY user_id", (5,),
    )
    assert cursor.last_sql == sql
    assert cursor.last_params == (5,)
    assert "DECLARE" not in raw_cursor.execute.call_args.args[0]


def test_cursor_fetchone_never_calls_more_results(backend):
    raw_cursor = Mock()
    raw_cursor.fetchone.side_effect = [("42", "text"), None]
    cursor = CursorWrapper(raw_cursor, backend)
    assert cursor.fetchone() == ("42", "text")
    assert cursor.fetchone() is None
    raw_cursor.nextset.assert_not_called()


def test_cursor_formats_booleans_as_postgresql_text_without_changing_other_values(backend):
    cursor = CursorWrapper(Mock(), backend)
    assert cursor.format_params([True, False, 1, 0, None, "text", Decimal("2.50")]) == (
        "true", "false", 1, 0, None, "text", Decimal("2.50"),
    )
    assert cursor.format_params(None) == ()


def test_cursor_executes_boolean_parameters_without_sqlserver_integer_coercion(backend):
    raw_cursor = Mock()
    cursor = CursorWrapper(raw_cursor, backend)
    cursor.execute("SELECT %s::boolean, %s::boolean", [True, False])
    raw_cursor.execute.assert_called_once_with("SELECT ?::boolean, ?::boolean", ("true", "false"))


def test_chunked_iterator_buffers_results_before_reconnect_can_invalidate_cursor(backend):
    assert backend.features.can_use_chunked_reads is False
    compiler = object.__new__(SQLCompiler)
    compiler.connection = backend
    compiler.as_sql = Mock(return_value=("SELECT id FROM django_users", []))
    compiler.has_extra_select = False
    compiler.col_count = 1
    raw_cursor = Mock()
    raw_cursor.fetchmany.side_effect = [[("first",)], [("second",)], []]
    with patch.object(backend, "chunked_cursor", return_value=raw_cursor):
        rows = compiler.execute_sql(chunked_fetch=True, chunk_size=1)
    assert rows == [[("first",)], [("second",)]]
    assert isinstance(rows, list)
    assert raw_cursor.fetchmany.call_count == 3
    raw_cursor.close.assert_called_once_with()


def test_introspection_lists_postgresql_tables_for_migration_recorder(backend):
    cursor = Mock()
    cursor.fetchall.return_value = [
        ("django_users", "BASE TABLE"), ("django_migrations", "BASE TABLE"), ("sample_view", "VIEW"),
    ]
    assert backend.introspection.table_names(cursor) == ["django_migrations", "django_users"]
    assert backend.introspection.table_names(cursor, include_views=True) == [
        "django_migrations", "django_users", "sample_view",
    ]
    assert "information_schema.tables" in cursor.execute.call_args.args[0]
    assert "current_schema()" in cursor.execute.call_args.args[0]


@pytest.mark.parametrize("alias,label,count", [("default", "catalog", 3), ("checkout", "checkout", 2)])
def test_initial_migration_postgresql_ddl_without_connecting(alias, label, count):
    loader = MigrationLoader(None)
    migration = loader.disk_migrations[(label, "0001_initial")]
    connection = connections[alias]
    with patch.object(connection, "ensure_connection", side_effect=AssertionError("must remain offline")):
        with connection.schema_editor(collect_sql=True, atomic=False) as editor:
            migration.apply(ProjectState(), editor)
    creates = [sql for sql in editor.collected_sql if sql.startswith("CREATE TABLE")]
    assert len(creates) == count
    assert all("bigserial" in sql for sql in creates)
    assert all("RETURNING" not in sql and "IDENTITY" not in sql for sql in editor.collected_sql)
