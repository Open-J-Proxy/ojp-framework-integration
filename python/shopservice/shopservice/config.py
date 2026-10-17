"""Database configuration read from environment variables."""

from __future__ import annotations

import os
import re
from dataclasses import dataclass

from sqlalchemy.engine import URL

_DATABASE_NAME = re.compile(r"^[A-Za-z0-9_]{1,128}$")


def _env(name: str, default: str) -> str:
    value = os.environ.get(name)
    return value if value else default


def _env_flag(name: str, default: bool) -> bool:
    return _env(name, "true" if default else "false").lower() in ("true", "yes", "1")


def is_valid_database_name(name: str) -> bool:
    return bool(_DATABASE_NAME.match(name))


@dataclass(frozen=True)
class DatabaseConfig:
    host: str = "localhost"
    port: int = 1433
    username: str = "sa"
    secret: str = ""
    driver: str = "ODBC Driver 18 for SQL Server"
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
            _env("CATALOG_DB", defaults.catalog_database),
            _env("CHECKOUT_DB", defaults.checkout_database),
            _env_flag("DB_ENCRYPT", defaults.encrypt),
            _env_flag("DB_TRUST_SERVER_CERTIFICATE", defaults.trust_server_certificate),
        )
        if not (is_valid_database_name(config.catalog_database) and is_valid_database_name(config.checkout_database)):
            raise ValueError("CATALOG_DB and CHECKOUT_DB may only contain letters, digits and '_'")
        return config

    def url(self, database: str) -> URL:
        """SQLAlchemy URL: the pyodbc dialect over the native Microsoft ODBC Driver for SQL Server."""
        return URL.create(
            "mssql+pyodbc",
            self.username,
            self.secret,
            self.host,
            self.port,
            database,
            {
                "driver": self.driver,
                "Encrypt": "yes" if self.encrypt else "no",
                "TrustServerCertificate": "yes" if self.trust_server_certificate else "no",
            },
        )
