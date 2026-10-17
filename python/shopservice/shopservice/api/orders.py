"""Mirror of OrderController."""

from __future__ import annotations

from typing import Any, Optional

from fastapi import APIRouter, Body

from ..models import Order, OrderItem
from ..repositories import Repositories
from ..serialization import BadRequest, as_object, opt_datetime, opt_int, order_json, page_json, page_request
from .deps import Repos, ok, status

router = APIRouter(prefix="/orders")


def _read_items(data: dict[str, Any]) -> Optional[list[OrderItem]]:
    raw = data.get("orderItems")
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise BadRequest("orderItems")
    items = []
    for element in raw:
        if not isinstance(element, dict):
            raise BadRequest("orderItems")
        product_id = opt_int(element, "productId")
        if product_id is None:
            raise BadRequest("productId")
        items.append(OrderItem(product_id=product_id, quantity=opt_int(element, "quantity") or 0))
    return items


def _is_user_valid(repos: Repositories, user_id: Optional[int]) -> bool:
    return user_id is not None and repos.users.exists_by_id(user_id)


def _are_items_valid(repos: Repositories, items: Optional[list[OrderItem]]) -> bool:
    """Mirrors OrderController#isValidOrderItems: an empty list is invalid and every item needs an existing product."""
    if items is None:
        return True
    if not items:
        return False
    return all(repos.products.exists_by_id(item.product_id) for item in items)


@router.post("")
def create(repos: Repos, body: Any = Body(None)):
    data = as_object(body)
    user_id = opt_int(data, "userId")
    if not _is_user_valid(repos, user_id):
        return status(400)
    items = _read_items(data)
    if not _are_items_valid(repos, items):
        return status(400)
    order = Order(user_id=user_id, order_items=items or [])
    order_date = opt_datetime(data, "orderDate")
    if order_date is not None:
        order.order_date = order_date
    return ok(order_json(repos.orders.save(order)))


@router.get("")
def list_orders(repos: Repos, page: Optional[str] = None, size: Optional[str] = None):
    request = page_request(page, size)
    items, total = repos.orders.find_page(request)
    return ok(page_json([order_json(o) for o in items], total, request))


@router.get("/{order_id}")
def get(order_id: int, repos: Repos):
    order = repos.orders.find_by_id(order_id)
    return ok(order_json(order)) if order else status(404)


@router.put("/{order_id}")
def update(order_id: int, repos: Repos, body: Any = Body(None)):
    existing = repos.orders.find_by_id(order_id)
    if existing is None:
        return status(404)
    data = as_object(body)
    user_id = opt_int(data, "userId")
    if _is_user_valid(repos, user_id):
        existing.user_id = user_id
    order_date = opt_datetime(data, "orderDate")
    if order_date is not None:
        existing.order_date = order_date
    items = _read_items(data)
    if items is not None and _are_items_valid(repos, items):
        existing.order_items = items
    return ok(order_json(repos.orders.save(existing)))


@router.delete("/{order_id}")
def delete(order_id: int, repos: Repos):
    existing = repos.orders.find_by_id(order_id)
    if existing is None:
        return status(404)
    repos.orders.delete(existing)
    return status(204)
