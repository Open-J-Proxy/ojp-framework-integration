"""Mirror of ReviewController."""

from __future__ import annotations

from typing import Any, Optional

from fastapi import APIRouter, Body

from ..models import Review
from ..serialization import (
    BadRequest,
    as_object,
    nested_id,
    opt_int,
    opt_str,
    page_json,
    page_request,
    review_json,
)
from .deps import Repos, ok, status

router = APIRouter(prefix="/reviews")


def _query_int(value: Optional[str]) -> Optional[int]:
    if value is None:
        return None
    try:
        return int(value)
    except ValueError as exc:
        raise BadRequest("query parameter") from exc


@router.post("")
def create(repos: Repos, body: Any = Body(None)):
    data = as_object(body)
    user_id = nested_id(data, "user")
    product_id = nested_id(data, "product")
    if user_id is None or product_id is None:
        return status(400)
    user = repos.users.find_by_id(user_id)
    product = repos.products.find_by_id(product_id)
    if user is None or product is None:
        return status(400)
    review = Review(
        user=user,
        product=product,
        rating=opt_int(data, "rating") or 0,
        comment=opt_str(data, "comment"),
    )
    return ok(review_json(repos.reviews.save(review)))


@router.get("")
def list_reviews(
    repos: Repos,
    page: Optional[str] = None,
    size: Optional[str] = None,
    userId: Optional[str] = None,  # noqa: N803 - query parameter name matches the Spring API
    productId: Optional[str] = None,  # noqa: N803
):
    request = page_request(page, size)
    user_id = _query_int(userId)
    product_id = _query_int(productId)
    if user_id is not None:
        items, total = repos.reviews.find_by_user_id(user_id, request)
    elif product_id is not None:
        items, total = repos.reviews.find_by_product_id(product_id, request)
    else:
        items, total = repos.reviews.find_page(request)
    return ok(page_json([review_json(r) for r in items], total, request))


@router.get("/{review_id}")
def get(review_id: int, repos: Repos):
    review = repos.reviews.find_by_id(review_id)
    return ok(review_json(review)) if review else status(404)


@router.put("/{review_id}")
def update(review_id: int, repos: Repos, body: Any = Body(None)):
    existing = repos.reviews.find_by_id(review_id)
    if existing is None:
        return status(404)
    data = as_object(body)
    user = repos.users.find_by_id(nested_id(data, "user"))
    if user is not None:
        existing.user = user
    product = repos.products.find_by_id(nested_id(data, "product"))
    if product is not None:
        existing.product = product
    existing.rating = opt_int(data, "rating") or 0
    existing.comment = opt_str(data, "comment")
    return ok(review_json(repos.reviews.save(existing)))


@router.delete("/{review_id}")
def delete(review_id: int, repos: Repos):
    existing = repos.reviews.find_by_id(review_id)
    if existing is None:
        return status(404)
    repos.reviews.delete(existing)
    return status(204)
