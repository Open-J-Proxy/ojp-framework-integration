"""Build escaped DSN-less OJP connection strings, never a local fallback."""

import os
import re


def brace(value: str) -> str:
    return "{" + value.replace("}", "}}") + "}"


def database_settings(kind: str) -> dict:
    name = os.environ.get(f"{kind.upper()}_DB", f"django_{kind}_db")
    host = os.environ.get("DB_HOST", "localhost")
    port = os.environ.get("DB_PORT", "5432")
    if not re.fullmatch(r"[A-Za-z0-9_]{1,63}", name):
        raise ValueError("Database names must contain only letters, digits and underscores")
    if not re.fullmatch(r"[A-Za-z0-9.-]{1,253}", host):
        raise ValueError("DB_HOST must be a hostname or IPv4 address")
    if not port.isdigit() or not 1 <= int(port) <= 65535:
        raise ValueError("DB_PORT must be between 1 and 65535")
    fields = {
        "DRIVER": os.environ.get("ODBC_DRIVER", "OJP"),
        "SERVER": os.environ.get("OJP_SERVER", "localhost:1059"),
        "DATABASE": f"jdbc:postgresql://{host}:{port}/{name}?stringtype=unspecified",
        "UID": os.environ.get("DB_USER", "postgres"),
        "PWD": os.environ.get("DB_PASSWORD", ""),
    }
    return {
        "ENGINE": "shopservice.odbc_backend",
        "NAME": name,
        "CONN_MAX_AGE": 0,
        "CONN_HEALTH_CHECKS": False,
        "OPTIONS": {
            "connection_string": "".join(f"{key}={brace(value)};" for key, value in fields.items()),
        },
    }
