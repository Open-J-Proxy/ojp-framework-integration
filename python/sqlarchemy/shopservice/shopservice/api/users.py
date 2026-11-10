"""Mirror of UserController."""

from __future__ import annotations

from typing import Any, Optional

from fastapi import APIRouter, Body

from ..models import User
from ..serialization import as_object, opt_datetime, page_json, page_request, required_str, user_json
from .deps import Repos, ok, status

router = APIRouter(prefix="/users")


@router.post("")
def create(repos: Repos, body: Any = Body(None)):
    data = as_object(body)
    user = User(
        username=required_str(data, "username"),
        email=required_str(data, "email"),
        created_at=opt_datetime(data, "createdAt"),
    )
    return ok(user_json(repos.users.save(user)))


@router.get("")
def list_users(repos: Repos, page: Optional[str] = None, size: Optional[str] = None):
    request = page_request(page, size)
    items, total = repos.users.find_page(request)
    return ok(page_json([user_json(u) for u in items], total, request))


@router.get("/{user_id}")
def get(user_id: int, repos: Repos):
    user = repos.users.find_by_id(user_id)
    return ok(user_json(user)) if user else status(404)


@router.put("/{user_id}")
def update(user_id: int, repos: Repos, body: Any = Body(None)):
    existing = repos.users.find_by_id(user_id)
    if existing is None:
        return status(404)
    data = as_object(body)
    existing.username = required_str(data, "username")
    existing.email = required_str(data, "email")
    return ok(user_json(repos.users.save(existing)))


@router.delete("/{user_id}")
def delete(user_id: int, repos: Repos):
    existing = repos.users.find_by_id(user_id)
    if existing is None:
        return status(404)
    repos.users.delete(existing)
    return status(204)
