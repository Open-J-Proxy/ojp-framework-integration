"""Database-free contracts for the PostgreSQL adapter and OJP cursor transport."""

from copy import deepcopy
from datetime import date, datetime, time, timedelta
from decimal import Decimal
from types import SimpleNamespace
from unittest.mock import MagicMock, Mock, patch
from uuid import UUID

import pyodbc
import pytest
from django.conf import settings
from django.db import NotSupportedError, connections
from django.db.backends.postgresql.base import DatabaseWrapper as PostgreSQLWrapper
from django.db.backends.postgresql.client import DatabaseClient as PostgreSQLClient
from django.db.backends.postgresql.creation import DatabaseCreation as PostgreSQLCreation
from django.db.backends.postgresql.compiler import SQLInsertCompiler as PostgreSQLInsertCompiler
from django.db.backends.postgresql.features import DatabaseFeatures as PostgreSQLFeatures
from django.db.backends.postgresql.introspection import DatabaseIntrospection as PostgreSQLIntrospection
from django.db.backends.postgresql.operations import DatabaseOperations as PostgreSQLOperations
from django.db.backends.postgresql.schema import DatabaseSchemaEditor as PostgreSQLSchemaEditor
from django.db.backends.utils import CursorDebugWrapper
from django.db.migrations.loader import MigrationLoader
from django.db.migrations.state import ProjectState
from django.db.models import Max, Min, Value
from django.db.models.functions import Greatest, Least
from django.db.models.sql.compiler import SQLCompiler, SQLInsertCompiler
from django.db.models.sql.subqueries import InsertQuery

from postgresql_odbc.base import CursorWrapper, DatabaseWrapper
from postgresql_odbc.introspection import parse_pg_array
from shopservice.models import Order, Product, User


@pytest.fixture
def backend():
    return DatabaseWrapper(deepcopy(settings.DATABASES["default"]), alias="default")


def test_backend_reuses_postgresql_components_and_type_maps(backend):
    assert isinstance(backend, PostgreSQLWrapper)
    for component, parent in [
        (backend.features, PostgreSQLFeatures),
        (backend.ops, PostgreSQLOperations),
        (backend.introspection, PostgreSQLIntrospection),
        (backend.client, PostgreSQLClient),
        (backend.creation, PostgreSQLCreation),
    ]:
        assert isinstance(component, parent)
    assert issubclass(backend.SchemaEditorClass, PostgreSQLSchemaEditor)
    assert backend.data_types == PostgreSQLWrapper.data_types
    assert backend.data_types_suffix == PostgreSQLWrapper.data_types_suffix
    for method in ("date_trunc_sql", "lookup_cast", "bulk_insert_sql", "sql_flush"):
        assert getattr(type(backend.ops), method) is getattr(PostgreSQLOperations, method)
    for method in ("get_table_list", "get_relations", "get_sequences"):
        assert getattr(type(backend.introspection), method) is getattr(PostgreSQLIntrospection, method)
    assert backend.SchemaEditorClass.sql_create_index == PostgreSQLSchemaEditor.sql_create_index
    assert backend.SchemaEditorClass.sql_alter_sequence_type == PostgreSQLSchemaEditor.sql_alter_sequence_type


def test_postgresql_lookup_distinct_and_date_sql_are_inherited(backend):
    query = Product.objects.filter(name__icontains="O'Reilly%").order_by("name").distinct("name").query
    sql, params = query.get_compiler(connection=backend).as_sql()
    assert 'DISTINCT ON ("django_products"."name")' in sql
    assert 'UPPER("django_products"."name"::text) LIKE UPPER(%s)' in sql
    assert params == ("%O'Reilly\\%%",)
    date_sql, date_params = backend.ops.date_trunc_sql("month", '"created_at"', [], None)
    assert date_sql == 'DATE_TRUNC(%s, "created_at")'
    assert date_params == ("month",)
    assert "TOP " not in sql and "DECLARE" not in sql


def test_server_binding_reports_upstream_nested_grouping_limitation(backend):
    assert backend.features.uses_server_side_binding is True
    assert (
        "aggregation.tests.AggregateTestCase.test_group_by_nested_expression_with_params"
        in backend.features.django_test_expected_failures
    )
    query = (
        Order.objects.annotate(greatest_id=Greatest("user_id", Value(600)))
        .values("greatest_id")
        .annotate(min_id=Min("user_id"), least_id=Least("min_id", "greatest_id"))
        .values_list("least_id", flat=True)
        .query
    )
    sql, params = query.get_compiler(connection=backend).as_sql()
    # As with Django's psycopg server binding, these distinct bind positions
    # aren't the same PostgreSQL expression despite having equal values.
    assert 'LEAST(MIN("django_orders"."user_id"), GREATEST("django_orders"."user_id", %s))' in sql
    assert 'GROUP BY GREATEST("django_orders"."user_id", %s)' in sql
    assert params == (600, 600)
    raw_cursor = Mock()
    CursorWrapper(raw_cursor, backend).execute(sql, params)
    assert raw_cursor.execute.call_args.args[0].count("?") == 2
    assert raw_cursor.execute.call_args.args[1] == (600, 600)


def test_nested_grouping_can_aggregate_the_grouped_expression(backend):
    query = (
        Order.objects.annotate(greatest_id=Greatest("user_id", Value(600)))
        .values("greatest_id")
        .annotate(min_id=Min("user_id"))
        .annotate(least_id=Least("min_id", Max("greatest_id")))
        .values_list("least_id", flat=True)
        .query
    )
    sql, params = query.get_compiler(connection=backend).as_sql()
    # GREATEST is constant within each group. MAX preserves its value while
    # keeping the repeated SELECT parameter inside an aggregate.
    assert 'LEAST(MIN("django_orders"."user_id"), MAX(GREATEST("django_orders"."user_id", %s)))' in sql
    assert 'GROUP BY GREATEST("django_orders"."user_id", %s)' in sql
    assert params == (600, 600)


@pytest.mark.parametrize("value,expected", [
    ("f", False), ("false", False), (False, False),
    ("t", True), ("true", True), (True, True),
])
def test_collation_determinism_normalizes_odbc_boolean_text(backend, value, expected):
    cursor = MagicMock()
    cursor.__enter__.return_value = cursor
    cursor.fetchone.return_value = (value,)
    with patch.object(backend, "cursor", return_value=cursor):
        with backend.schema_editor(collect_sql=True, atomic=False) as editor:
            assert editor._is_collation_deterministic("case_insensitive") is expected
    assert "SELECT collisdeterministic" in cursor.execute.call_args.args[0]
    assert cursor.execute.call_args.args[1] == ["case_insensitive"]


@pytest.mark.parametrize("value,has_index", [("f", False), ("t", True)])
def test_only_deterministic_collation_creates_pattern_ops_index(backend, value, has_index):
    cursor = MagicMock()
    cursor.__enter__.return_value = cursor
    cursor.fetchone.return_value = (value,)
    field = Product._meta.get_field("name")
    with patch.object(backend, "cursor", return_value=cursor):
        with patch.object(field, "db_collation", "case_insensitive"):
            with patch.object(field, "db_index", True):
                with backend.schema_editor(collect_sql=True, atomic=False) as editor:
                    statement = editor._create_like_index_sql(Product, field)
                    assert (statement is not None) is has_index
                    if has_index:
                        assert "varchar_pattern_ops" in str(statement)


def test_unknown_collation_preserves_none_result(backend):
    cursor = MagicMock()
    cursor.__enter__.return_value = cursor
    cursor.fetchone.return_value = None
    with patch.object(backend, "cursor", return_value=cursor):
        with backend.schema_editor(collect_sql=True, atomic=False) as editor:
            assert editor._is_collation_deterministic("missing") is None


def test_connection_uses_odbc_and_never_opens_a_psycopg_socket(backend):
    with patch("psycopg.connect", side_effect=AssertionError("not an ODBC connection")):
        with patch("postgresql_odbc.base.pyodbc.connect") as connect:
            backend.get_new_connection(backend.get_connection_params())
    connect.assert_called_once()
    assert backend.Database is pyodbc


def test_debug_cursor_uses_generic_django_wrapper_not_psycopg_copy_hooks(backend):
    raw_cursor = Mock()
    assert type(backend.make_debug_cursor(raw_cursor)) is CursorDebugWrapper


@pytest.mark.parametrize("value", [
    "O'Reilly", "x'); DROP TABLE django_users; --", "\\path\\quote'", "%s ? %%", "é☃",
])
def test_hostile_values_remain_bound_parameters(backend, value):
    raw_cursor = Mock()
    cursor = CursorWrapper(raw_cursor, backend)
    cursor.execute("SELECT %s::text", [value])
    raw_cursor.execute.assert_called_once_with("SELECT ?::text", (value,))
    assert cursor.last_params == [value]
    assert value not in raw_cursor.execute.call_args.args[0]


@pytest.mark.parametrize("value", [
    "O'Reilly", "x'); DROP TABLE django_users; --", "\\path\\quote'", "%s ? %%", "é☃",
])
def test_schema_literal_quoting_and_default_composition_stay_offline(backend, value):
    with patch.object(backend, "ensure_connection", side_effect=AssertionError("must remain offline")):
        with backend.schema_editor(collect_sql=True, atomic=False) as editor:
            quoted = editor.quote_value(value)
            composed = backend.ops.compose_sql("ALTER TABLE probe ADD COLUMN value text DEFAULT %s", [value])
            editor.execute("ALTER TABLE probe ADD COLUMN value text DEFAULT %s", [value])
    assert isinstance(quoted, str)
    assert value.replace("'", "''").replace("\\", "\\\\") in quoted or value.replace("'", "''") in quoted
    assert composed == "ALTER TABLE probe ADD COLUMN value text DEFAULT " + quoted
    assert editor.collected_sql == [composed + ";"]


@pytest.mark.parametrize("value,expected", [
    (None, "NULL"), (True, "true"), (False, "false"),
    (Decimal("12.50"), "12.50"), (42, "42"),
])
def test_schema_quotes_typed_defaults_without_a_driver_connection(backend, value, expected):
    with backend.schema_editor(collect_sql=True, atomic=False) as editor:
        assert editor.quote_value(value) == expected
        assert backend.ops.compose_sql("SELECT %s", [value]) == f"SELECT {expected}"


def test_named_parameters_and_escaped_percent_preserve_bind_order(backend):
    raw_cursor = Mock()
    cursor = CursorWrapper(raw_cursor, backend)
    cursor.execute("SELECT %(value)s, '100%%', %(value)s", {"value": "x' %s ?"})
    raw_cursor.execute.assert_called_once_with(
        "SELECT ?, '100%', ?", ("x' %s ?", "x' %s ?"),
    )


@pytest.mark.parametrize("sql,params", [
    ("SELECT %s", []), ("SELECT %s", [1, 2]), ("SELECT %d", [1]),
])
def test_cursor_rejects_invalid_placeholder_contracts_before_execute(backend, sql, params):
    raw_cursor = Mock()
    with pytest.raises(ValueError):
        CursorWrapper(raw_cursor, backend).execute(sql, params)
    raw_cursor.execute.assert_not_called()


@pytest.mark.parametrize("operator", ["?", "?|", "?&"])
@pytest.mark.parametrize("many", [False, True])
def test_json_existence_operators_fail_before_odbc_can_misbind(backend, operator, many):
    raw_cursor = Mock()
    cursor = CursorWrapper(raw_cursor, backend)
    sql = f"SELECT payload {operator} %s FROM probe"
    with pytest.raises(NotSupportedError, match="ODBC parameter markers"):
        if many:
            cursor.executemany(sql, [("key",)])
        else:
            cursor.execute(sql, ["key"])
    assert backend.features.has_json_operators is False
    raw_cursor.execute.assert_not_called()
    raw_cursor.executemany.assert_not_called()


@pytest.mark.parametrize("sql", [
    "SELECT '?'",
    "SELECT 'a''?b'",
    'SELECT "column?name" FROM probe',
    'SELECT "a""?b" FROM probe',
    "SELECT E'escaped\\' ? still literal'",
    "SELECT $$ ? ?| ?& $$",
    "SELECT $body$ ? ?| ?& $body$",
    "SELECT 1 -- ? ?| ?&\n",
    "SELECT 1 /* ? /* nested ? */ ? */",
])
def test_question_marks_inside_postgresql_literals_identifiers_comments_are_not_operators(backend, sql):
    raw_cursor = Mock()
    CursorWrapper(raw_cursor, backend).execute(sql)
    raw_cursor.execute.assert_called_once_with(sql, ())


def test_json_operator_after_quoted_question_mark_is_still_rejected(backend):
    raw_cursor = Mock()
    with pytest.raises(NotSupportedError):
        CursorWrapper(raw_cursor, backend).execute("SELECT '?'::jsonb ? %s", ["key"])
    raw_cursor.execute.assert_not_called()


def test_new_connection_disables_pooling_and_sets_mode_before_connect(backend):
    raw_connection = Mock()
    params = backend.get_connection_params()
    with patch("postgresql_odbc.base.pyodbc.connect", return_value=raw_connection) as connect:
        assert backend.get_new_connection(params) is raw_connection
    connect.assert_called_once_with(
        params["connection_string"], autocommit=True, attrs_before={}, timeout=15,
    )
    assert backend._odbc_autocommit is True
    assert pyodbc.pooling is False


def test_autocommit_transitions_reconnect_with_preconnect_attributes(backend):
    old_connection, transactional, restored = Mock(), Mock(), Mock()
    transactional.cursor.return_value.fetchone.return_value = (backend.timezone_name,)
    restored.cursor.return_value.fetchone.return_value = (backend.timezone_name,)
    backend.connection = old_connection
    backend._odbc_autocommit = True
    with patch("postgresql_odbc.base.pyodbc.connect", side_effect=[transactional, restored]) as connect:
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
    with patch("postgresql_odbc.base.pyodbc.connect", side_effect=pyodbc.Error("connect failed")):
        with pytest.raises(pyodbc.Error):
            backend._set_autocommit(False)
    assert backend.connection is old_connection
    assert backend._odbc_autocommit is True
    old_connection.close.assert_not_called()


def test_failed_transaction_timezone_setup_closes_candidate_and_preserves_state(backend):
    old_connection, candidate = Mock(), Mock()
    backend.connection = old_connection
    backend._odbc_autocommit = True
    with patch.object(backend, "_connect", return_value=candidate):
        with patch.object(backend, "_configure_timezone", side_effect=pyodbc.Error("timezone failed")):
            with pytest.raises(pyodbc.Error, match="timezone failed"):
                backend._set_autocommit(False)
    candidate.close.assert_called_once_with()
    old_connection.close.assert_not_called()
    assert backend.connection is old_connection
    assert backend._odbc_autocommit is True


@pytest.mark.parametrize("autocommit", [False, True])
def test_failed_old_connection_close_discards_both_handles_without_switching_mode(backend, autocommit):
    old_connection, candidate = Mock(), Mock()
    old_connection.close.side_effect = pyodbc.Error("old connection close failed")
    backend.connection = old_connection
    backend._odbc_autocommit = not autocommit
    with patch.object(backend, "_connect", return_value=candidate):
        with patch.object(backend, "_configure_timezone") as configure:
            with pytest.raises(pyodbc.Error, match="old connection close failed"):
                backend._set_autocommit(autocommit)
    configure.assert_called_once_with(candidate)
    candidate.close.assert_called_once_with()
    assert backend.connection is None
    assert backend._odbc_autocommit is not autocommit


def test_commit_and_rollback_use_odbc_and_reset_django_transaction_state(backend):
    raw_connection = Mock()
    backend.connection = raw_connection
    backend._odbc_autocommit = False
    backend.errors_occurred = True
    backend.commit()
    raw_connection.commit.assert_called_once_with()
    assert backend.errors_occurred is False
    assert backend.run_commit_hooks_on_set_autocommit_on is True
    backend.needs_rollback = True
    backend.run_on_commit = [(set(), Mock(), False)]
    backend.rollback()
    raw_connection.rollback.assert_called_once_with()
    assert backend.needs_rollback is False
    assert backend.run_on_commit == []
    assert backend.connection is raw_connection
    assert backend._odbc_autocommit is False
    assert "autocommit" not in raw_connection.__dict__


def test_version_is_read_through_odbc_and_cached(backend):
    cursor = MagicMock()
    cursor.fetchone.return_value = ("150008",)
    with patch.object(backend, "temporary_connection") as temporary:
        temporary.return_value.__enter__.return_value = cursor
        assert backend.pg_version == 150008
        assert backend.get_database_version() == (15, 8)
        assert backend.pg_version == 150008
    cursor.execute.assert_called_once_with("SHOW server_version_num")
    temporary.assert_called_once()


def test_version_query_error_is_not_replaced_with_a_guessed_version(backend):
    cursor = MagicMock()
    cursor.execute.side_effect = pyodbc.Error("version query failed")
    with patch.object(backend, "temporary_connection") as temporary:
        temporary.return_value.__enter__.return_value = cursor
        with pytest.raises(pyodbc.Error, match="version query failed"):
            _ = backend.pg_version
    assert "pg_version" not in backend.__dict__


def test_initialization_rejects_unsupported_postgresql_before_session_setup(backend):
    backend.pg_version = 130012
    with patch("django.db.backends.base.base.RAN_DB_VERSION_CHECK", set()):
        with patch.object(backend, "_configure_timezone") as configure:
            with pytest.raises(NotSupportedError, match="14.*required"):
                backend.init_connection_state()
    configure.assert_not_called()


def test_initialization_propagates_odbc_session_errors(backend):
    backend.pg_version = 150008
    backend.connection = Mock()
    with patch("django.db.backends.base.base.RAN_DB_VERSION_CHECK", set()):
        with patch.object(backend, "_configure_timezone", side_effect=pyodbc.Error("initialization failed")):
            with pytest.raises(pyodbc.Error, match="initialization failed"):
                backend.init_connection_state()


def test_initialization_warns_about_reconnect_session_state(backend):
    backend.pg_version = 150008
    backend.connection = Mock()
    with patch("django.db.backends.base.base.RAN_DB_VERSION_CHECK", set()):
        with patch.object(backend, "_configure_timezone") as configure:
            with pytest.warns(RuntimeWarning, match="session state|session SET"):
                backend.init_connection_state()
    configure.assert_called_once_with(backend.connection)


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
    ("FloatField", "1.25", 1.25),
    ("FloatField", None, None),
    ("DateField", "2024-01-15", date(2024, 1, 15)),
    ("TimeField", "10:30:00.123456", time(10, 30, 0, 123456)),
    ("UUIDField", "12345678-1234-5678-1234-567812345678", UUID("12345678-1234-5678-1234-567812345678")),
    ("BinaryField", "\\x0041ff", b"\x00A\xff"),
    ("DurationField", "2 days 03:04:05", timedelta(days=2, hours=3, minutes=4, seconds=5)),
    ("DateField", None, None),
    ("TimeField", None, None),
    ("UUIDField", None, None),
    ("BinaryField", None, None),
    ("DurationField", None, None),
])
def test_text_result_conversion(backend, field_type, value, expected):
    expression = SimpleNamespace(output_field=SimpleNamespace(get_internal_type=lambda: field_type))
    converters = backend.ops.get_db_converters(expression)
    assert converters
    for converter in converters:
        value = converter(value, expression, backend)
    assert value == expected


def test_boolean_decoder_does_not_silently_turn_invalid_text_into_false(backend):
    expression = SimpleNamespace(output_field=SimpleNamespace(get_internal_type=lambda: "BooleanField"))
    converter = backend.ops.get_db_converters(expression)[-1]
    with pytest.raises(ValueError, match="boolean"):
        converter("not-a-boolean", expression, backend)


@pytest.mark.parametrize("model,values", [
    (User, {"username": "alice", "email": "alice@example.com"}),
    (Product, {"name": "Widget", "price": Decimal("19.99")}),
    (Order, {"user_id": 42}),
])
@pytest.mark.parametrize("count", [1, 2])
def test_insert_returning_reuses_postgresql_compiler_and_converts_text_ids(backend, model, values, count):
    query = InsertQuery(model)
    fields = [model._meta.get_field(name) for name in values]
    query.insert_values(fields, [model(**values) for _ in range(count)])
    compiler = query.get_compiler(connection=backend)
    assert isinstance(compiler, (SQLInsertCompiler, PostgreSQLInsertCompiler))
    assert isinstance(compiler, PostgreSQLInsertCompiler)
    cursor = MagicMock()
    cursor.__enter__.return_value = cursor
    cursor.fetchone.return_value = ("123",)
    cursor.fetchall.return_value = [("123",), ("124",)]
    with patch.object(backend, "cursor", return_value=cursor):
        with patch.object(backend, "set_autocommit", side_effect=AssertionError("no insert-only transaction")):
            result = compiler.execute_sql([model._meta.pk])
    assert result == ([[123]] if count == 1 else [[123], [124]])
    cursor.execute.assert_called_once()
    sql = cursor.execute.call_args.args[0]
    assert sql.endswith(f'RETURNING "{model._meta.db_table}"."id"')
    assert "currval" not in sql
    if count == 1:
        cursor.fetchone.assert_called_once()
        cursor.fetchall.assert_not_called()
    else:
        assert "), (" in sql
        cursor.fetchall.assert_called_once()


def test_returning_multiple_columns_uses_field_converters(backend):
    query = InsertQuery(Product)
    fields = [Product._meta.get_field("name"), Product._meta.get_field("price")]
    query.insert_values(fields, [Product(name="Widget", price=Decimal("19.99"))])
    cursor = MagicMock()
    cursor.__enter__.return_value = cursor
    cursor.fetchone.return_value = ("123", "19.99")
    with patch.object(backend, "cursor", return_value=cursor):
        rows = query.get_compiler(connection=backend).execute_sql([
            Product._meta.pk, Product._meta.get_field("price"),
        ])
    assert rows == [[123, Decimal("19.99")]]
    assert cursor.execute.call_args.args[0].endswith(
        'RETURNING "django_products"."id", "django_products"."price"'
    )


def test_return_insert_columns_handles_empty_fields(backend):
    assert backend.ops.return_insert_columns([]) == ("", ())


def test_returning_timestamp_uses_field_converter(backend):
    query = InsertQuery(User)
    query.insert_values(
        [User._meta.get_field("username"), User._meta.get_field("email")],
        [User(username="alice", email="alice@example.com")],
    )
    cursor = MagicMock()
    cursor.__enter__.return_value = cursor
    cursor.fetchone.return_value = ("123", "2024-01-15 10:30:00.123456")
    with patch.object(backend, "cursor", return_value=cursor):
        rows = query.get_compiler(connection=backend).execute_sql([
            User._meta.pk, User._meta.get_field("created_at"),
        ])
    assert rows == [[123, datetime(2024, 1, 15, 10, 30, 0, 123456)]]


def test_cursor_keeps_postgresql_group_by_sql_without_tsql_rewrite(backend):
    raw_cursor = Mock()
    cursor = CursorWrapper(raw_cursor, backend)
    sql = "SELECT user_id, COUNT(*) FROM django_orders WHERE user_id > %s GROUP BY user_id"
    cursor.execute(sql, [5])
    raw_cursor.execute.assert_called_once_with(
        "SELECT user_id, COUNT(*) FROM django_orders WHERE user_id > ? GROUP BY user_id", (5,),
    )
    assert cursor.last_sql == sql
    assert cursor.last_params == [5]
    assert "DECLARE" not in raw_cursor.execute.call_args.args[0]


def test_cursor_fetchone_never_calls_more_results(backend):
    raw_cursor = Mock()
    raw_cursor.fetchone.side_effect = [("42", "text"), None]
    cursor = CursorWrapper(raw_cursor, backend)
    assert cursor.fetchone() == ("42", "text")
    assert cursor.fetchone() is None
    raw_cursor.nextset.assert_not_called()


def test_cursor_formats_booleans_as_postgresql_text_without_changing_other_values(backend):
    raw_cursor = Mock()
    cursor = CursorWrapper(raw_cursor, backend)
    cursor.execute("SELECT %s, %s, %s, %s, %s, %s, %s", [
        True, False, 1, 0, None, "text", Decimal("2.50"),
    ])
    assert raw_cursor.execute.call_args.args[1] == (
        "true", "false", 1, 0, None, "text", "2.50",
    )
    cursor.execute("SELECT 1")
    raw_cursor.execute.assert_called_with("SELECT 1", ())


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
        ("django_users", "t", None), ("django_migrations", "t", None), ("sample_view", "v", None),
    ]
    assert backend.introspection.table_names(cursor) == ["django_migrations", "django_users"]
    assert backend.introspection.table_names(cursor, include_views=True) == [
        "django_migrations", "django_users", "sample_view",
    ]
    assert "pg_class" in cursor.execute.call_args.args[0]
    assert "pg_table_is_visible" in cursor.execute.call_args.args[0]


@pytest.mark.parametrize("value,expected", [
    (None, None), ("{}", []),
    ('{"a,b","a\\"b","a\\\\b",NULL,"NULL"}', ["a,b", 'a"b', "a\\b", None, "NULL"]),
    ("{first,second}", ["first", "second"]),
])
def test_catalog_array_decoding_preserves_postgresql_escaping(value, expected):
    assert parse_pg_array(value) == expected


def test_catalog_metadata_resolves_postgresql_types_without_odbc_type_codes(backend):
    cursor = Mock()
    cursor.fetchall.return_value = [
        ("id", "20", "8", "-1", "f", None, None, "t", None),
        ("label", "1043", "-1", "36", "t", "'quoted'::character varying", None, "f", "label comment"),
        ("amount", "1700", "-1", str(4 + (10 << 16) + 2), "f", None, None, "f", None),
        ("enabled", "16", "1", "-1", "f", "true", None, "f", None),
    ]
    fields = backend.introspection.get_table_description(cursor, 'odd"table')
    assert [backend.introspection.get_field_type(field.type_code, field) for field in fields] == [
        "BigAutoField", "CharField", "DecimalField", "BooleanField",
    ]
    assert fields[0].is_autofield is True and fields[0].null_ok is False
    assert fields[1].internal_size == 32 and fields[1].null_ok is True
    assert fields[1].comment == "label comment"
    assert (fields[2].precision, fields[2].scale) == (10, 2)
    assert cursor.execute.call_args.args[1] == ['"odd""table"']
    assert cursor.execute.call_count == 1


def test_catalog_constraints_decode_text_arrays_and_booleans(backend):
    cursor = Mock()
    cursor.fetchall.side_effect = [
        [("probe_pk", "{id}", "p", None, None),
         ("probe_fk", "{parent_id}", "f", "parent.id", None),
         ("probe_check", "{amount}", "c", None, None)],
        [("probe_idx", "{amount,label}", "f", "f", "{DESC,ASC}", "btree", None, None)],
    ]
    result = backend.introspection.get_constraints(cursor, "probe")
    assert result["probe_pk"]["primary_key"] is True
    assert result["probe_pk"]["columns"] == ["id"]
    assert result["probe_fk"]["foreign_key"] == ("parent", "id")
    assert result["probe_check"]["check"] is True
    assert result["probe_idx"]["columns"] == ["amount", "label"]
    assert result["probe_idx"]["orders"] == ["DESC", "ASC"]
    assert result["probe_idx"]["unique"] is False
    assert result["probe_idx"]["primary_key"] is False
    assert cursor.execute.call_count == 2
    constraint_query, index_query = cursor.execute.call_args_list
    assert "FROM pg_constraint" in constraint_query.args[0]
    assert "array_agg" not in constraint_query.args[0]
    assert constraint_query.args[1] == ["probe"]
    assert "array_agg" in index_query.args[0]
    assert "pg_index" in index_query.args[0]
    assert index_query.args[1] == ["btree", "probe"]


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
    assert all("bigint" in sql and "GENERATED BY DEFAULT AS IDENTITY" in sql for sql in creates)
    assert all("RETURNING" not in sql and "bigserial" not in sql for sql in editor.collected_sql)
