"""OJP PostgreSQL adapter using the third-party mssql-django ODBC cursor layer.

Only the transport is shared with mssql-django, not its SQL Server SQL dialect.
OJP reports result columns as text and executes DML through JDBC executeUpdate,
so values need conversion and generated keys require a separate SELECT.
"""

from datetime import datetime
from decimal import Decimal

import pyodbc
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.base.client import BaseDatabaseClient
from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.base.features import BaseDatabaseFeatures
from django.db.backends.base.introspection import BaseDatabaseIntrospection, TableInfo
from django.db.backends.base.operations import BaseDatabaseOperations
from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from mssql.base import CursorWrapper as ODBCCursorWrapper


class CursorWrapper(ODBCCursorWrapper):
    def execute(self, sql, params=None):
        # The upstream GROUP BY rewrite emits T-SQL DECLARE statements.
        self.last_sql = sql
        self.last_params = self.format_params(params)
        return self.cursor.execute(self.format_sql(sql, params), self.last_params)

    def fetchone(self):
        # Do not invoke SQLMoreResults: OJP exposes a single result set.
        row = self.cursor.fetchone()
        return self.format_row(row) if row is not None else None


class DatabaseFeatures(BaseDatabaseFeatures):
    supports_transactions = True
    can_rollback_ddl = True
    uses_savepoints = True
    can_release_savepoints = True
    supports_foreign_keys = True
    supports_column_check_constraints = True
    supports_table_check_constraints = True
    can_return_columns_from_insert = False
    can_return_rows_from_bulk_insert = False
    has_bulk_insert = False
    supports_timezones = False


class DatabaseOperations(BaseDatabaseOperations):
    compiler_module = "shopservice.odbc_backend.compiler"
    cast_data_types = {"AutoField": "integer", "BigAutoField": "bigint"}

    def quote_name(self, name):
        if name.startswith('"') and name.endswith('"'):
            return name
        return '"' + name.replace('"', '""') + '"'

    def no_limit_value(self):
        return None

    def deferrable_sql(self):
        return " DEFERRABLE INITIALLY DEFERRED"

    def last_insert_id(self, cursor, table_name, pk_name):
        cursor.execute("SELECT currval(pg_get_serial_sequence(%s, %s))", [table_name, pk_name])
        return int(cursor.fetchone()[0])

    def adapt_datetimefield_value(self, value):
        return value.isoformat(sep=" ") if value is not None else None

    def adapt_decimalfield_value(self, value, max_digits=None, decimal_places=None):
        return str(value) if value is not None else None

    def get_db_converters(self, expression):
        converters = super().get_db_converters(expression)
        field_type = expression.output_field.get_internal_type()
        if field_type in (
            "AutoField", "BigAutoField", "SmallAutoField", "IntegerField",
            "BigIntegerField", "SmallIntegerField", "PositiveIntegerField",
            "PositiveBigIntegerField", "PositiveSmallIntegerField",
        ):
            converters.append(self.convert_integer)
        elif field_type == "DecimalField":
            converters.append(self.convert_decimal)
        elif field_type == "DateTimeField":
            converters.append(self.convert_datetime)
        elif field_type == "BooleanField":
            converters.append(self.convert_boolean)
        return converters

    @staticmethod
    def convert_integer(value, expression, connection):
        return int(value) if value is not None else None

    @staticmethod
    def convert_decimal(value, expression, connection):
        return Decimal(str(value)) if value is not None else None

    @staticmethod
    def convert_datetime(value, expression, connection):
        return datetime.fromisoformat(value) if isinstance(value, str) else value

    @staticmethod
    def convert_boolean(value, expression, connection):
        return value in (True, 1, "1", "t", "true") if value is not None else None

    def sql_flush(self, style, tables, *, reset_sequences=False, allow_cascade=False):
        if not tables:
            return []
        sql = "TRUNCATE " + ", ".join(self.quote_name(table) for table in tables)
        if reset_sequences:
            sql += " RESTART IDENTITY"
        if allow_cascade:
            sql += " CASCADE"
        return [sql + ";"]


class DatabaseIntrospection(BaseDatabaseIntrospection):
    def get_table_list(self, cursor):
        cursor.execute(
            "SELECT table_name, table_type FROM information_schema.tables "
            "WHERE table_schema = current_schema()"
        )
        return [TableInfo(name, "v" if kind == "VIEW" else "t") for name, kind in cursor.fetchall()]


class DatabaseWrapper(BaseDatabaseWrapper):
    vendor = "postgresql"
    display_name = "PostgreSQL via OJP ODBC (mssql-django cursor adapter)"
    Database = pyodbc
    SchemaEditorClass = BaseDatabaseSchemaEditor
    client_class = BaseDatabaseClient
    creation_class = BaseDatabaseCreation
    features_class = DatabaseFeatures
    introspection_class = DatabaseIntrospection
    ops_class = DatabaseOperations
    data_types = {
        "AutoField": "serial",
        "BigAutoField": "bigserial",
        "SmallAutoField": "smallserial",
        "BigIntegerField": "bigint",
        "BooleanField": "boolean",
        "CharField": "varchar(%(max_length)s)",
        "DateTimeField": "timestamp",
        "DecimalField": "numeric(%(max_digits)s, %(decimal_places)s)",
        "IntegerField": "integer",
        "PositiveIntegerField": "integer",
        "SmallIntegerField": "smallint",
        "TextField": "text",
    }
    operators = {
        "exact": "= %s", "gt": "> %s", "gte": ">= %s",
        "lt": "< %s", "lte": "<= %s", "contains": "LIKE %s",
        "startswith": "LIKE %s", "endswith": "LIKE %s",
    }
    driver_charset = None
    supports_mars = True

    def get_connection_params(self):
        return {"connection_string": self.settings_dict["OPTIONS"]["connection_string"]}

    def _connect(self, params, autocommit):
        # unixODBC routes post-connect changes to the missing SQLSetConnectAttrW.
        # Select the actual mode before connecting, while pyodbc believes it is
        # already in autocommit mode and therefore never changes that attribute.
        pyodbc.pooling = False
        attrs = {} if autocommit else {pyodbc.SQL_ATTR_AUTOCOMMIT: 0}
        return pyodbc.connect(
            params["connection_string"], autocommit=True, attrs_before=attrs,
            timeout=self.settings_dict["OPTIONS"].get("timeout", 15),
        )

    def get_new_connection(self, conn_params):
        self._odbc_autocommit = True
        return self._connect(conn_params, True)

    def init_connection_state(self):
        pass

    def create_cursor(self, name=None):
        return CursorWrapper(self.connection.cursor(), self)

    def _set_autocommit(self, autocommit):
        if self._odbc_autocommit != autocommit:
            # Django commits/rolls back before restoring autocommit. Reconnect
            # instead of issuing an unsupported post-connect ODBC attribute call.
            connection = self._connect(self.get_connection_params(), autocommit)
            self.connection.close()
            self.connection = connection
            self._odbc_autocommit = autocommit

    def is_usable(self):
        try:
            with self.connection.cursor() as cursor:
                cursor.execute("SELECT 1")
                cursor.fetchone()
            return True
        except pyodbc.Error:
            return False

    def _on_error(self, error):
        pass
