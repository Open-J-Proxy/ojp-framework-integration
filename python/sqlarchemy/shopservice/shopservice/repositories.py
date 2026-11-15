"""Spring-Data-like repositories. Each method runs in its own transaction and returns detached entities."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, Iterable, Optional, TypeVar

from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session, sessionmaker

from .database import Databases
from .models import Order, OrderItem, Product, Review, User

T = TypeVar("T")


@dataclass(frozen=True)
class PageRequest:
    page: int = 0
    size: int = 20

    @property
    def offset(self) -> int:
        return self.page * self.size


class Repository(Generic[T]):
    model: type

    def __init__(self, sessions: sessionmaker[Session]):
        self._sessions = sessions

    def save(self, entity: T) -> T:
        return self.save_all([entity])[0]

    def save_all(self, entities: Iterable[T]) -> list[T]:
        with self._sessions.begin() as session:
            merged = [session.merge(entity) for entity in entities]
            session.flush()
            return merged

    def find_by_id(self, entity_id: Optional[int]) -> Optional[T]:
        if entity_id is None:
            return None
        with self._sessions() as session:
            return session.get(self.model, entity_id)

    def exists_by_id(self, entity_id: Optional[int]) -> bool:
        return self.find_by_id(entity_id) is not None

    def find_all(self) -> list[T]:
        with self._sessions() as session:
            return list(session.scalars(select(self.model).order_by(self.model.id)).unique())

    def find_page(self, page: PageRequest, *criteria) -> tuple[list[T], int]:
        with self._sessions() as session:
            query = select(self.model).where(*criteria).order_by(self.model.id).offset(page.offset).limit(page.size)
            items = list(session.scalars(query).unique())
            total = session.scalar(select(func.count()).select_from(self.model).where(*criteria))
            return items, int(total or 0)

    def count(self) -> int:
        with self._sessions() as session:
            return int(session.scalar(select(func.count()).select_from(self.model)) or 0)

    def delete(self, entity: T) -> None:
        self.delete_by_id(entity.id)

    def delete_by_id(self, entity_id: int) -> None:
        with self._sessions.begin() as session:
            entity = session.get(self.model, entity_id)
            if entity is not None:
                session.delete(entity)

    def delete_all(self) -> None:
        with self._sessions.begin() as session:
            session.execute(delete(self.model))


class UserRepository(Repository[User]):
    model = User


class ProductRepository(Repository[Product]):
    model = Product


class ReviewRepository(Repository[Review]):
    model = Review

    def find_by_user_id(self, user_id: int, page: PageRequest) -> tuple[list[Review], int]:
        return self.find_page(page, Review.user_id == user_id)

    def find_by_product_id(self, product_id: int, page: PageRequest) -> tuple[list[Review], int]:
        return self.find_page(page, Review.product_id == product_id)


class OrderRepository(Repository[Order]):
    model = Order


class OrderItemRepository(Repository[OrderItem]):
    model = OrderItem

    def find_by_order_id(self, order_id: int) -> list[OrderItem]:
        with self._sessions() as session:
            query = select(OrderItem).where(OrderItem.order_id == order_id).order_by(OrderItem.id)
            return list(session.scalars(query))


@dataclass
class Repositories:
    users: UserRepository
    products: ProductRepository
    reviews: ReviewRepository
    orders: OrderRepository
    order_items: OrderItemRepository

    @classmethod
    def create(cls, databases: Databases) -> "Repositories":
        return cls(
            users=UserRepository(databases.catalog_sessions),
            products=ProductRepository(databases.catalog_sessions),
            reviews=ReviewRepository(databases.catalog_sessions),
            orders=OrderRepository(databases.checkout_sessions),
            order_items=OrderItemRepository(databases.checkout_sessions),
        )
