"""Engines, sessions and schema bootstrap for the catalog and checkout databases."""

from __future__ import annotations

from dataclasses import dataclass

import pyodbc
from sqlalchemy import Engine, create_engine, text
from sqlalchemy.dialects.mssql.pyodbc import MSDialect_pyodbc
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import NullPool

from .config import DatabaseConfig, is_valid_database_name
from .models import CatalogBase, CheckoutBase

_SQL_AUTOCOMMIT_OFF = 0


class _OJPExecutionContext(MSDialect_pyodbc.execution_ctx_cls):
    def fetchall_for_returning(self, cursor):
        rows = super().fetchall_for_returning(cursor)
        imv = self.compiled._insertmanyvalues
        if imv is not None and imv.implicit_sentinel:
            # SQLAlchemy sorts this identity sentinel before applying result processors.
            # OJP's character metadata would otherwise sort "10" before "9".
            return [tuple(row[:-1]) + (int(row[-1]),) for row in rows]
        return rows


@dataclass
class Databases:
    catalog_engine: Engine
    checkout_engine: Engine
    catalog_sessions: sessionmaker[Session]
    checkout_sessions: sessionmaker[Session]

    def dispose(self) -> None:
        self.catalog_engine.dispose()
        self.checkout_engine.dispose()


def _engine(config: DatabaseConfig, database: str, *, autocommit: bool = False) -> Engine:
    # The OJP server owns the database connection pools, so the client must not pool: NullPool opens a
    # connection per unit of work and really closes it afterwards.
    #
    # The OJP ODBC driver exports SQLDriverConnectW, so unixODBC treats it as a Unicode driver and only
    # forwards post-connect SQLSetConnectAttr calls to SQLSetConnectAttrW, which the driver does not
    # implement. pyodbc is therefore told the connection is in autocommit mode (so it never toggles the
    # attribute after connecting), while manual-commit mode is requested through attrs_before, which the
    # Driver Manager applies during connect. commit()/rollback() still reach the driver's SQLEndTran.
    attrs_before = {} if autocommit else {pyodbc.SQL_ATTR_AUTOCOMMIT: _SQL_AUTOCOMMIT_OFF}
    engine = create_engine(
        config.url(database),
        poolclass=NullPool,
        connect_args={"autocommit": True, "attrs_before": attrs_before},
    )
    engine.dialect.execution_ctx_cls = _OJPExecutionContext
    return engine


def ensure_databases_exist(config: DatabaseConfig) -> None:
    """Create the catalog and checkout databases if they are missing."""
    master = _engine(config, "master", autocommit=True)
    try:
        with master.connect() as conn:
            for name in (config.catalog_database, config.checkout_database):
                # CREATE DATABASE cannot take a bound parameter, so only validated names are
                # interpolated (and bracket-quoted).
                if not is_valid_database_name(name):
                    raise ValueError("Invalid database name")
                if conn.execute(text("SELECT DB_ID(:name)"), {"name": name}).scalar() is None:
                    conn.exec_driver_sql(f"CREATE DATABASE [{name}]")
    finally:
        master.dispose()


def init_databases(config: DatabaseConfig) -> Databases:
    """Create missing databases and tables and return engines/session factories for both datasources."""
    # Disable ODBC Driver Manager pooling too; this must happen before the first ODBC connection is opened.
    pyodbc.pooling = False
    ensure_databases_exist(config)
    catalog_engine = _engine(config, config.catalog_database)
    checkout_engine = _engine(config, config.checkout_database)
    CatalogBase.metadata.create_all(catalog_engine)
    CheckoutBase.metadata.create_all(checkout_engine)
    return Databases(
        catalog_engine=catalog_engine,
        checkout_engine=checkout_engine,
        catalog_sessions=sessionmaker(catalog_engine, expire_on_commit=False),
        checkout_sessions=sessionmaker(checkout_engine, expire_on_commit=False),
    )
