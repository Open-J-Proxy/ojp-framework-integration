"""SQLAlchemy ORM entities, split into the catalog and checkout databases like the Spring Boot JPA entities.

Every table has the ``py_`` prefix so it cannot clash with the other framework modules.
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Optional

from sqlalchemy import BigInteger, ForeignKey, Identity, Integer, Numeric, String, TypeDecorator, UnicodeText, func
from sqlalchemy.dialects.mssql import DATETIME2
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Timestamp(TypeDecorator):
    """``DATETIME2(6)`` column that also accepts timestamps returned as text.

    The OJP ODBC driver reports result columns as character data, so values arrive as ISO strings.
    """

    impl = DATETIME2(precision=6)
    cache_ok = True

    def process_result_value(self, value, dialect):
        if isinstance(value, str):
            return datetime.fromisoformat(value)
        return value


class CatalogBase(DeclarativeBase):
    """Entities stored in the catalog database."""


class CheckoutBase(DeclarativeBase):
    """Entities stored in the checkout database."""


# ── Catalog ─────────────────────────────────────────────────────────────────


class User(CatalogBase):
    __tablename__ = "py_users"

    id: Mapped[int] = mapped_column(BigInteger, Identity(start=1, increment=1), primary_key=True)
    username: Mapped[str] = mapped_column(String(255), nullable=False)
    email: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[Optional[datetime]] = mapped_column(Timestamp(), nullable=True)


class Product(CatalogBase):
    __tablename__ = "py_products"

    id: Mapped[int] = mapped_column(BigInteger, Identity(start=1, increment=1), primary_key=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    price: Mapped[Decimal] = mapped_column(Numeric(19, 2, asdecimal=True), nullable=False)


class Review(CatalogBase):
    __tablename__ = "py_reviews"

    id: Mapped[int] = mapped_column(BigInteger, Identity(start=1, increment=1), primary_key=True)
    user_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("py_users.id"), nullable=False)
    product_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("py_products.id"), nullable=False)
    rating: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    comment: Mapped[Optional[str]] = mapped_column(UnicodeText, nullable=True)

    user: Mapped[User] = relationship(lazy="joined")
    product: Mapped[Product] = relationship(lazy="joined")


# ── Checkout ────────────────────────────────────────────────────────────────


class Order(CheckoutBase):
    __tablename__ = "py_orders"

    id: Mapped[int] = mapped_column(BigInteger, Identity(start=1, increment=1), primary_key=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    order_date: Mapped[Optional[datetime]] = mapped_column(
        Timestamp(), nullable=True, default=datetime.now, server_default=func.sysdatetime()
    )

    order_items: Mapped[list["OrderItem"]] = relationship(
        back_populates="order",
        cascade="all, delete-orphan",
        passive_deletes=True,
        lazy="selectin",
        order_by="OrderItem.id",
    )


class OrderItem(CheckoutBase):
    __tablename__ = "py_order_items"

    id: Mapped[int] = mapped_column(BigInteger, Identity(start=1, increment=1), primary_key=True)
    order_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("py_orders.id", ondelete="CASCADE"), nullable=False)
    product_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    order: Mapped[Optional[Order]] = relationship(back_populates="order_items", lazy="select")
