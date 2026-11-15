"""Mirror of OrderItemController."""

from __future__ import annotations

from typing import Any, Optional

from fastapi import APIRouter, Body

from ..models import OrderItem
from ..repositories import Repositories
from ..serialization import as_object, opt_int, order_item_json
from .deps import Repos, ok, status

router = APIRouter(prefix="/orders/{order_id}/items")


def _find_item_in_order(repos: Repositories, order_id: int, item_id: int) -> Optional[OrderItem]:
    item = repos.order_items.find_by_id(item_id)
    if item is None or item.order_id != order_id:
        return None
    return item


@router.post("")
def create(order_id: int, repos: Repos, body: Any = Body(None)):
    if not repos.orders.exists_by_id(order_id):
        return status(400)
    data = as_object(body)
    product_id = opt_int(data, "productId")
    if not repos.products.exists_by_id(product_id):
        return status(400)
    item = OrderItem(order_id=order_id, product_id=product_id, quantity=opt_int(data, "quantity") or 0)
    return ok(order_item_json(repos.order_items.save(item)))


@router.get("")
def list_items(order_id: int, repos: Repos):
    return ok([order_item_json(item) for item in repos.order_items.find_by_order_id(order_id)])


@router.get("/{item_id}")
def get(order_id: int, item_id: int, repos: Repos):
    item = _find_item_in_order(repos, order_id, item_id)
    return ok(order_item_json(item)) if item else status(404)


@router.put("/{item_id}")
def update(order_id: int, item_id: int, repos: Repos, body: Any = Body(None)):
    data = as_object(body)
    product_id = opt_int(data, "productId")
    if product_id is not None and not repos.products.exists_by_id(product_id):
        return status(400)
    existing = _find_item_in_order(repos, order_id, item_id)
    if existing is None:
        return status(404)
    if product_id is not None:
        existing.product_id = product_id
    existing.quantity = opt_int(data, "quantity") or 0
    return ok(order_item_json(repos.order_items.save(existing)))


@router.delete("/{item_id}")
def delete(order_id: int, item_id: int, repos: Repos):
    existing = _find_item_in_order(repos, order_id, item_id)
    if existing is None:
        return status(404)
    repos.order_items.delete(existing)
    return status(204)
