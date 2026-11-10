"""JSON (de)serialization helpers producing the same shapes as the Spring Boot (Jackson) controllers."""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal, InvalidOperation
from typing import Any, Optional, Sequence

from .models import Order, OrderItem, Product, Review, User
from .repositories import PageRequest

MAX_PAGE_SIZE = 2000
DEFAULT_PAGE_SIZE = 20


class BadRequest(Exception):
    """Raised for malformed request input; mapped to HTTP 400."""


# ── Parsing ─────────────────────────────────────────────────────────────────


def as_object(body: Any) -> dict[str, Any]:
    if not isinstance(body, dict):
        raise BadRequest("JSON object expected")
    return body


def opt_int(body: dict[str, Any], key: str) -> Optional[int]:
    value = body.get(key)
    if value is None:
        return None
    if isinstance(value, bool):
        raise BadRequest(key)
    if isinstance(value, int):
        return value
    if isinstance(value, float) and value.is_integer():
        return int(value)
    if isinstance(value, str):
        try:
            return int(value)
        except ValueError as exc:
            raise BadRequest(key) from exc
    raise BadRequest(key)


def opt_str(body: dict[str, Any], key: str) -> Optional[str]:
    value = body.get(key)
    if value is None:
        return None
    if not isinstance(value, str):
        raise BadRequest(key)
    return value


def required_str(body: dict[str, Any], key: str) -> str:
    value = opt_str(body, key)
    if value is None:
        raise BadRequest(key)
    return value


def required_decimal(body: dict[str, Any], key: str) -> Decimal:
    value = body.get(key)
    if value is None or isinstance(value, bool) or not isinstance(value, (int, float, str)):
        raise BadRequest(key)
    try:
        result = Decimal(str(value))
    except InvalidOperation as exc:
        raise BadRequest(key) from exc
    if not result.is_finite():
        raise BadRequest(key)
    return result


def opt_datetime(body: dict[str, Any], key: str) -> Optional[datetime]:
    value = body.get(key)
    if value is None:
        return None
    if not isinstance(value, str):
        raise BadRequest(key)
    try:
        return datetime.fromisoformat(value)
    except ValueError as exc:
        raise BadRequest(key) from exc


def nested_id(body: dict[str, Any], key: str) -> Optional[int]:
    value = body.get(key)
    if not isinstance(value, dict):
        return None
    return opt_int(value, "id")


def page_request(page: Optional[str], size: Optional[str]) -> PageRequest:
    """Like Spring's Pageable resolver: invalid values fall back to the defaults."""
    number = 0
    page_size = DEFAULT_PAGE_SIZE
    try:
        if page is not None and int(page) >= 0:
            number = int(page)
    except ValueError:
        pass
    try:
        if size is not None and int(size) >= 1:
            page_size = min(int(size), MAX_PAGE_SIZE)
    except ValueError:
        pass
    return PageRequest(number, page_size)


# ── Serialization ───────────────────────────────────────────────────────────


def _datetime(value: Optional[datetime]) -> Optional[str]:
    return value.isoformat() if value is not None else None


def _decimal(value: Optional[Decimal]) -> Optional[float]:
    return float(value) if value is not None else None


def user_json(user: User) -> dict[str, Any]:
    return {"id": user.id, "username": user.username, "email": user.email, "createdAt": _datetime(user.created_at)}


def product_json(product: Product) -> dict[str, Any]:
    return {"id": product.id, "name": product.name, "price": _decimal(product.price)}


def review_json(review: Review) -> dict[str, Any]:
    return {
        "id": review.id,
        "user": user_json(review.user),
        "product": product_json(review.product),
        "rating": review.rating,
        "comment": review.comment,
    }


def order_item_json(item: OrderItem) -> dict[str, Any]:
    return {"id": item.id, "productId": item.product_id, "quantity": item.quantity}


def order_json(order: Order) -> dict[str, Any]:
    return {
        "id": order.id,
        "userId": order.user_id,
        "orderItems": [order_item_json(item) for item in order.order_items],
        "orderDate": _datetime(order.order_date),
    }


def page_json(content: Sequence[dict[str, Any]], total: int, page: PageRequest) -> dict[str, Any]:
    """Spring Data compatible ``Page<T>`` JSON."""
    total_pages = (total + page.size - 1) // page.size
    sort = {"sorted": False, "unsorted": True, "empty": True}
    return {
        "content": list(content),
        "pageable": {
            "pageNumber": page.page,
            "pageSize": page.size,
            "sort": sort,
            "offset": page.offset,
            "paged": True,
            "unpaged": False,
        },
        "totalElements": total,
        "totalPages": total_pages,
        "last": page.page + 1 >= total_pages,
        "size": page.size,
        "number": page.page,
        "sort": sort,
        "first": page.page == 0,
        "numberOfElements": len(content),
        "empty": len(content) == 0,
    }
