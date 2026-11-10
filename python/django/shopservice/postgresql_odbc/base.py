import warnings
from contextlib import contextmanager
from datetime import date, datetime, time, timedelta
from decimal import Decimal
from urllib.parse import quote as urlquote, urlsplit, urlunsplit
from uuid import UUID

import pyodbc
from django.conf import settings
from django.core.exceptions import ImproperlyConfigured
from django.db import NotSupportedError
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.postgresql.base import DatabaseWrapper as PostgreSQLWrapper
from django.utils.asyncio import async_unsafe
from django.utils.functional import cached_property

from .client import DatabaseClient
from .creation import DatabaseCreation
from .features import DatabaseFeatures
from .introspection import DatabaseIntrospection
from .literals import check_sql, interpolate
from .operations import DatabaseOperations
from .schema import DatabaseSchemaEditor


def connection_fields(connection_string):
    """Read ODBC key/value attributes, including escaped closing braces."""
    fields = {}
    offset = 0
    while offset < len(connection_string):
        if connection_string[offset] in "; \t\r\n":
            offset += 1
            continue
        end = connection_string.find("=", offset)
        if end < 0:
            raise ImproperlyConfigured("Invalid ODBC connection string attribute")
        key = connection_string[offset:end].strip().upper()
        offset = end + 1
        while offset < len(connection_string) and connection_string[offset] in " \t\r\n":
            offset += 1
        if connection_string[offset : offset + 1] == "{":
            offset += 1
            value = []
            while offset < len(connection_string):
                char = connection_string[offset]
                offset += 1
                if char == "}":
                    if connection_string[offset : offset + 1] == "}":
                        value.append("}")
                        offset += 1
                    else:
                        break
                else:
                    value.append(char)
            else:
                raise ImproperlyConfigured("Unclosed ODBC connection string brace")
            while offset < len(connection_string) and connection_string[offset] in " \t\r\n":
                offset += 1
            if offset < len(connection_string) and connection_string[offset] != ";":
                raise ImproperlyConfigured("Invalid text after ODBC braced value")
            fields[key] = "".join(value)
        else:
            end = connection_string.find(";", offset)
            if end == -1:
                end = len(connection_string)
            fields[key] = connection_string[offset:end].strip()
            offset = end
    return fields


class CursorWrapper:
    def __init__(self, cursor, db):
        self.cursor = cursor
        self.db = db
        self.last_sql = None
        self.last_params = None

    def __getattr__(self, name):
        return getattr(self.cursor, name)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()

    def __iter__(self):
        while (row := self.fetchone()) is not None:
            yield row

    @staticmethod
    def _parameter(value):
        if isinstance(value, bool):
            return "true" if value else "false"
        if isinstance(value, (datetime, date, time)):
            return value.isoformat()
        if isinstance(value, (Decimal, UUID, timedelta)):
            return str(value)
        if isinstance(value, memoryview):
            return value.tobytes()
        if isinstance(value, (list, tuple, dict)) or type(value).__module__.startswith("psycopg"):
            raise NotSupportedError("PostgreSQL array/range/extension parameter adapters aren't supported over ODBC")
        return value

    def execute(self, sql, params=None):
        self.last_sql, self.last_params = sql, params
        check_sql(sql)
        query, values = interpolate(sql, params, lambda value: "?")
        self.cursor.execute(query, tuple(self._parameter(value) for value in values))
        return self

    def executemany(self, sql, param_list):
        rows = list(param_list)
        if not rows:
            return self
        check_sql(sql)
        query, _ = interpolate(sql, rows[0], lambda value: "?")
        formatted = []
        for params in rows:
            current, values = interpolate(sql, params, lambda value: "?")
            if current != query:
                raise ValueError("Inconsistent executemany placeholders")
            formatted.append(tuple(self._parameter(value) for value in values))
        self.cursor.executemany(query, formatted)
        return self

    def fetchone(self):
        row = self.cursor.fetchone()
        return tuple(row) if row is not None else None

    def fetchmany(self, size=None):
        rows = self.cursor.fetchmany() if size is None else self.cursor.fetchmany(size)
        return [tuple(row) for row in rows]

    def fetchall(self):
        return [tuple(row) for row in self.cursor.fetchall()]

    def callproc(self, procname, params=None):
        raise NotSupportedError("callproc() isn't supported; use parameterized PostgreSQL SQL")


class DatabaseWrapper(PostgreSQLWrapper):
    display_name = "PostgreSQL via OJP ODBC"
    Database = pyodbc
    SchemaEditorClass = DatabaseSchemaEditor
    client_class = DatabaseClient
    creation_class = DatabaseCreation
    features_class = DatabaseFeatures
    introspection_class = DatabaseIntrospection
    ops_class = DatabaseOperations

    @property
    def pool(self):
        if "pool" in self.settings_dict["OPTIONS"]:
            raise ImproperlyConfigured("psycopg pools aren't supported by postgresql_odbc")
        return None

    def close_pool(self):
        pass

    def get_connection_params(self):
        options = self.settings_dict["OPTIONS"]
        unsupported = set(options) - {"connection_string", "timeout"}
        if unsupported:
            raise ImproperlyConfigured(
                "Unsupported postgresql_odbc OPTIONS: " + ", ".join(sorted(unsupported))
            )
        name = self.settings_dict["NAME"]
        if name == "" or (name is not None and (len(name.encode()) > 63 or "\0" in name)):
            raise ImproperlyConfigured("Supply a nonempty PostgreSQL NAME of at most 63 bytes")
        name = name if name is not None else "postgres"
        fields = connection_fields(options.get("connection_string", ""))
        fields.setdefault("DRIVER", "OJP")
        fields.setdefault("SERVER", "localhost:1059")
        jdbc_url = fields.get(
            "DATABASE",
            "jdbc:postgresql://{}:{}/{}".format(
                self.settings_dict.get("HOST") or "localhost",
                self.settings_dict.get("PORT") or "5432",
                urlquote(name, safe=""),
            ) + "?stringtype=unspecified",
        )
        if not jdbc_url.startswith("jdbc:postgresql://"):
            raise ImproperlyConfigured("OJP DATABASE must be a jdbc:postgresql:// URL")
        parts = urlsplit(jdbc_url[5:])
        fields["DATABASE"] = "jdbc:" + urlunsplit(
            parts._replace(path="/" + urlquote(name, safe=""))
        )
        for setting, attribute in (("USER", "UID"), ("PASSWORD", "PWD")):
            if self.settings_dict.get(setting):
                fields[attribute] = self.settings_dict[setting]
        return {
            "connection_string": "".join(
                key + "={" + value.replace("}", "}}") + "};"
                for key, value in fields.items()
            )
        }

    def _connect(self, params, autocommit):
        # OJP currently lacks SQLSetConnectAttrW. Set the real mode before
        # connecting while pyodbc believes it is already in autocommit mode.
        pyodbc.pooling = False
        return pyodbc.connect(
            params["connection_string"],
            autocommit=True,
            attrs_before={} if autocommit else {pyodbc.SQL_ATTR_AUTOCOMMIT: 0},
            timeout=self.settings_dict["OPTIONS"].get("timeout", 15),
        )

    @async_unsafe
    def get_new_connection(self, conn_params):
        if any(app.startswith("django.contrib.postgres") for app in settings.INSTALLED_APPS):
            warnings.warn(
                "django.contrib.postgres psycopg connection hooks are unsupported by postgresql_odbc.",
                RuntimeWarning,
                stacklevel=2,
            )
            raise NotSupportedError("Remove django.contrib.postgres; its psycopg hooks cannot use pyodbc")
        self._odbc_autocommit = True
        return self._connect(conn_params, True)

    def init_connection_state(self):
        BaseDatabaseWrapper.init_connection_state(self)
        self._configure_timezone(self.connection)
        warnings.warn(
            "OJP may lease different PostgreSQL sessions between autocommit statements. "
            "Set the server/database/role timezone to the Django connection timezone; "
            "session SET state is guaranteed only inside a transaction. Autocommit "
            "transitions reconnect, discarding temporary tables and other session state.",
            RuntimeWarning,
            stacklevel=2,
        )

    def _configure_timezone(self, connection):
        with CursorWrapper(connection.cursor(), self) as cursor:
            cursor.execute("SELECT current_setting('TimeZone')")
            current = cursor.fetchone()[0]
            if current == self.timezone_name:
                return False
            cursor.execute("SELECT set_config('TimeZone', %s, false)", [self.timezone_name])
            cursor.fetchone()
        return True

    def ensure_timezone(self):
        if self.connection is None:
            return False
        return self._configure_timezone(self.connection)

    def _set_autocommit(self, autocommit):
        if self._odbc_autocommit == autocommit:
            return
        connection = self._connect(self.get_connection_params(), autocommit)
        try:
            # Reapply the same configuration in either mode. A transaction's
            # SET must stay on its leased session: don't commit before atomic().
            self._configure_timezone(connection)
        except Exception:
            connection.close()
            raise
        try:
            self.connection.close()
        except Exception:
            # The old handle may be half-closed; force Django to reconnect.
            self.connection = None
            connection.close()
            raise
        self.connection = connection
        self._odbc_autocommit = autocommit

    @async_unsafe
    def create_cursor(self, name=None):
        if name is not None:
            raise NotSupportedError("Named/server-side cursors aren't supported over ODBC")
        return CursorWrapper(self.connection.cursor(), self)

    @async_unsafe
    def chunked_cursor(self):
        return self._cursor()

    make_debug_cursor = BaseDatabaseWrapper.make_debug_cursor

    def is_usable(self):
        if self.connection is None:
            return False
        try:
            with CursorWrapper(self.connection.cursor(), self) as cursor:
                cursor.execute("SELECT 1")
                cursor.fetchone()
        except pyodbc.Error:
            return False
        return True

    @contextmanager
    def _nodb_cursor(self):
        # No fallback to a production database and no psycopg exception handling.
        with BaseDatabaseWrapper._nodb_cursor(self) as cursor:
            yield cursor

    @cached_property
    def pg_version(self):
        with self.temporary_connection() as cursor:
            cursor.execute("SHOW server_version_num")
            return int(cursor.fetchone()[0])
