"""Database configuration read from environment variables."""

from __future__ import annotations

import os
import re
from dataclasses import dataclass

from sqlalchemy.engine import URL

_DATABASE_NAME = re.compile(r"^[A-Za-z0-9_]{1,128}$")
_HOST = re.compile(r"^[A-Za-z0-9.\-]{1,253}$")
_OJP_ENDPOINTS = re.compile(r"^[A-Za-z0-9.\-]{1,253}:[0-9]{1,5}(,[A-Za-z0-9.\-]{1,253}:[0-9]{1,5})*$")


def _env(name: str, default: str) -> str:
    value = os.environ.get(name)
    return value if value else default


def _env_flag(name: str, default: bool) -> bool:
    return _env(name, "true" if default else "false").lower() in ("true", "yes", "1")


def is_valid_database_name(name: str) -> bool:
    return bool(_DATABASE_NAME.match(name))


def _brace(value: str) -> str:
    """Quote an ODBC connection-string value; a literal '}' is escaped by doubling it."""
    return "{" + value.replace("}", "}}") + "}"


@dataclass(frozen=True)
class DatabaseConfig:
    """Connection settings.

    All database access goes through OJP: the application talks to the OJP ODBC driver, which forwards every call
    over gRPC to the OJP server(s) in ``ojp_server``. The OJP server then opens the real JDBC connection to SQL
    Server using ``host``/``port``, which are therefore resolved from the OJP server's point of view.
    """

    host: str = "localhost"
    port: int = 1433
    username: str = "sa"
    secret: str = ""
    driver: str = "OJP"
    ojp_server: str = "localhost:1059"
    catalog_database: str = "py_catalog_db"
    checkout_database: str = "py_checkout_db"
    encrypt: bool = False
    trust_server_certificate: bool = True

    @classmethod
    def from_environment(cls) -> "DatabaseConfig":
        defaults = cls()
        port = _env("DB_PORT", str(defaults.port))
        if not port.isdigit():
            raise ValueError("DB_PORT must be a number")
        config = cls(
            _env("DB_HOST", defaults.host),
            int(port),
            _env("DB_USER", defaults.username),
            _env("DB_PASSWORD", defaults.secret),
            _env("ODBC_DRIVER", defaults.driver),
            _env("OJP_SERVER", defaults.ojp_server),
            _env("CATALOG_DB", defaults.catalog_database),
            _env("CHECKOUT_DB", defaults.checkout_database),
            _env_flag("DB_ENCRYPT", defaults.encrypt),
            _env_flag("DB_TRUST_SERVER_CERTIFICATE", defaults.trust_server_certificate),
        )
        if not (is_valid_database_name(config.catalog_database) and is_valid_database_name(config.checkout_database)):
            raise ValueError("CATALOG_DB and CHECKOUT_DB may only contain letters, digits and '_'")
        if not _HOST.match(config.host):
            raise ValueError("DB_HOST may only contain letters, digits, '.' and '-'")
        if not _OJP_ENDPOINTS.match(config.ojp_server):
            raise ValueError("OJP_SERVER must be 'host:port' or a comma-separated list of 'host:port' endpoints")
        return config

    def jdbc_url(self, database: str) -> str:
        """Backend JDBC URL that the OJP server uses to reach SQL Server."""
        if not is_valid_database_name(database):
            raise ValueError("Invalid database name")
        return (
            f"jdbc:sqlserver://{self.host}:{self.port};databaseName={database}"
            f";encrypt={'true' if self.encrypt else 'false'}"
            f";trustServerCertificate={'true' if self.trust_server_certificate else 'false'}"
        )

    def odbc_connection_string(self, database: str) -> str:
        """DSN-less OJP ODBC connection string: SERVER is the OJP endpoint, DATABASE the backend JDBC URL."""
        fields = {
            "DRIVER": _brace(self.driver),
            "SERVER": _brace(self.ojp_server),
            "DATABASE": _brace(self.jdbc_url(database)),
            "UID": _brace(self.username),
            "PWD": _brace(self.secret),
        }
        return "".join(f"{key}={value};" for key, value in fields.items())

    def url(self, database: str) -> URL:
        """SQLAlchemy URL: the mssql+pyodbc dialect over the OJP ODBC driver."""
        return URL.create("mssql+pyodbc", query={"odbc_connect": self.odbc_connection_string(database)})
