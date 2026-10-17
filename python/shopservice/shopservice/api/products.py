"""Mirror of ProductController."""

from __future__ import annotations

from typing import Any, Optional

from fastapi import APIRouter, Body, Response

from ..models import Product
from ..serialization import as_object, opt_int, page_json, page_request, product_json, required_decimal, required_str
from .deps import Repos, ok, status

router = APIRouter(prefix="/products")


@router.post("")
def create(repos: Repos, body: Any = Body(None)):
    data = as_object(body)
    product = Product(name=required_str(data, "name"), price=required_decimal(data, "price"))
    return ok(product_json(repos.products.save(product)))


@router.get("")
def list_products(repos: Repos, page: Optional[str] = None, size: Optional[str] = None):
    request = page_request(page, size)
    items, total = repos.products.find_page(request)
    return ok(page_json([product_json(p) for p in items], total, request))


@router.get("/{product_id}")
def get(product_id: int, repos: Repos):
    product = repos.products.find_by_id(product_id)
    return ok(product_json(product)) if product else status(404)


@router.put("/{product_id}")
def update(product_id: int, repos: Repos, body: Any = Body(None)):
    existing = repos.products.find_by_id(product_id)
    if existing is None:
        return status(404)
    data = as_object(body)
    existing.name = required_str(data, "name")
    existing.price = required_decimal(data, "price")
    return ok(product_json(repos.products.save(existing)))


@router.put("")
def save(body: Any = Body(None)):
    """Same (non-persisting) behaviour as the Spring Boot ``PUT /products`` endpoint."""
    product_id = opt_int(as_object(body), "id")
    location = "/products/" + (str(product_id) if product_id is not None else "null")
    return Response(status_code=201, headers={"Location": location})


@router.delete("/{product_id}")
def delete(product_id: int, repos: Repos):
    existing = repos.products.find_by_id(product_id)
    if existing is None:
        return status(404)
    repos.products.delete(existing)
    return status(204)
