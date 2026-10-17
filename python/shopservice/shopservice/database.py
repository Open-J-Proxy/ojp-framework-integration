"""Engines, sessions and schema bootstrap for the catalog and checkout databases."""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from .config import DatabaseConfig, is_valid_database_name
from .models import CatalogBase, CheckoutBase


@dataclass
class Databases:
    catalog_engine: Engine
    checkout_engine: Engine
    catalog_sessions: sessionmaker[Session]
    checkout_sessions: sessionmaker[Session]

    def dispose(self) -> None:
        self.catalog_engine.dispose()
        self.checkout_engine.dispose()


def _engine(config: DatabaseConfig, database: str, **kwargs) -> Engine:
    return create_engine(config.url(database), pool_pre_ping=True, **kwargs)


def ensure_databases_exist(config: DatabaseConfig) -> None:
    """Create the catalog and checkout databases if they are missing."""
    master = _engine(config, "master", isolation_level="AUTOCOMMIT")
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
