"""Small Django ORM repositories with explicit, single-database transactions."""

from dataclasses import dataclass
from typing import Generic, Iterable, TypeVar

from django.db import router, transaction

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

    @property
    def alias(self):
        return router.db_for_write(self.model)

    def query(self):
        return self.model.objects.using(self.alias).order_by("id")

    def save(self, entity: T) -> T:
        return self.save_all([entity])[0]

    def save_all(self, entities: Iterable[T]) -> list[T]:
        with transaction.atomic(using=self.alias):
            result = list(entities)
            for entity in result:
                entity.save(using=self.alias)
            return result

    def find_by_id(self, entity_id):
        return self.query().filter(pk=entity_id).first() if entity_id is not None else None

    def exists_by_id(self, entity_id):
        return entity_id is not None and self.query().filter(pk=entity_id).exists()

    def find_all(self):
        return list(self.query())

    def find_page(self, page: PageRequest, **criteria):
        query = self.query().filter(**criteria)
        return list(query[page.offset:page.offset + page.size]), query.count()

    def count(self):
        return self.query().count()

    def delete(self, entity):
        self.delete_by_id(entity.pk)

    def delete_by_id(self, entity_id):
        with transaction.atomic(using=self.alias):
            self.query().filter(pk=entity_id).delete()

    def delete_all(self):
        with transaction.atomic(using=self.alias):
            self.query().delete()


class UserRepository(Repository[User]):
    model = User


class ProductRepository(Repository[Product]):
    model = Product


class ReviewRepository(Repository[Review]):
    model = Review

    def query(self):
        return super().query().select_related("user", "product")

    def find_by_user_id(self, user_id, page):
        return self.find_page(page, user_id=user_id)

    def find_by_product_id(self, product_id, page):
        return self.find_page(page, product_id=product_id)


class OrderRepository(Repository[Order]):
    model = Order

    def query(self):
        return super().query().prefetch_related("order_items")

    def save_with_items(self, entity, items=None):
        # Saving the parent, removing orphans and inserting replacements must
        # either all commit or all roll back on checkout.
        with transaction.atomic(using=self.alias):
            entity.save(using=self.alias)
            if items is not None:
                OrderItem.objects.using(self.alias).filter(order_id=entity.pk).delete()
                for item in items:
                    item.order_id = entity.pk
                    item.save(using=self.alias)
            return self.find_by_id(entity.pk)


class OrderItemRepository(Repository[OrderItem]):
    model = OrderItem

    def find_by_order_id(self, order_id):
        return list(self.query().filter(order_id=order_id))


@dataclass
class Repositories:
    users: UserRepository
    products: ProductRepository
    reviews: ReviewRepository
    orders: OrderRepository
    order_items: OrderItemRepository

    @classmethod
    def create(cls):
        return cls(UserRepository(), ProductRepository(), ReviewRepository(), OrderRepository(), OrderItemRepository())
