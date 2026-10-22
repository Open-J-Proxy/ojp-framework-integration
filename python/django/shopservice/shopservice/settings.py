"""Two pre-provisioned PostgreSQL databases accessed exclusively through OJP."""

import os

import pyodbc
from django.core.management.utils import get_random_secret_key

from .config import database_settings

# Disable the driver's process-wide pool before opening any connections.
pyodbc.pooling = False

SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY") or get_random_secret_key()
DEBUG = os.environ.get("DJANGO_DEBUG", "false").lower() == "true"
ALLOWED_HOSTS = os.environ.get("DJANGO_ALLOWED_HOSTS", "localhost,127.0.0.1,testserver").split(",")
INSTALLED_APPS = ["catalog.apps.CatalogConfig", "checkout.apps.CheckoutConfig"]
MIDDLEWARE = ["django.middleware.common.CommonMiddleware"]
ROOT_URLCONF = "shopservice.urls"
WSGI_APPLICATION = "shopservice.wsgi.application"
ASGI_APPLICATION = "shopservice.asgi.application"
DATABASES = {"default": database_settings("catalog"), "checkout": database_settings("checkout")}
DATABASE_ROUTERS = ["shopservice.routers.ShopRouter"]
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
USE_TZ = False
TIME_ZONE = "UTC"
APPEND_SLASH = False
DATA_UPLOAD_MAX_MEMORY_SIZE = 2_621_440
