"""FastAPI application factory."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import Response

from .api import order_items, orders, products, reviews, users
from .config import DatabaseConfig
from .database import init_databases
from .repositories import Repositories
from .serialization import BadRequest

logger = logging.getLogger(__name__)


def create_app(config: Optional[DatabaseConfig] = None) -> FastAPI:
    """Build the ShopService API. Databases and tables are created on startup if missing."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        databases = init_databases(config or DatabaseConfig.from_environment())
        app.state.databases = databases
        app.state.repositories = Repositories.create(databases)
        try:
            yield
        finally:
            databases.dispose()

    app = FastAPI(title="ShopService (Python)", lifespan=lifespan)

    # Spring returns 400 for unreadable bodies and type mismatches; FastAPI would return 422.
    @app.exception_handler(RequestValidationError)
    async def validation_error(_: Request, __: RequestValidationError) -> Response:
        return Response(status_code=400)

    @app.exception_handler(BadRequest)
    async def bad_request(_: Request, __: BadRequest) -> Response:
        return Response(status_code=400)

    @app.exception_handler(Exception)
    async def unexpected_error(_: Request, exc: Exception) -> Response:
        logger.exception("Unhandled error", exc_info=exc)
        return Response(status_code=500)

    for module in (users, products, reviews, orders, order_items):
        app.include_router(module.router)
    return app
