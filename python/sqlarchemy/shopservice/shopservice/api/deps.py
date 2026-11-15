"""Shared FastAPI dependencies and response helpers."""

from __future__ import annotations

from typing import Annotated, Any

from fastapi import Depends, Request, Response
from fastapi.responses import JSONResponse

from ..repositories import Repositories


def get_repositories(request: Request) -> Repositories:
    return request.app.state.repositories


Repos = Annotated[Repositories, Depends(get_repositories)]


def ok(content: Any) -> JSONResponse:
    return JSONResponse(status_code=200, content=content)


def status(code: int) -> Response:
    return Response(status_code=code)
