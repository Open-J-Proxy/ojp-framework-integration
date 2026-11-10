"""Connection-free PostgreSQL quoting and DB-API format parameter handling."""

from collections.abc import Mapping
import re

from django.db import NotSupportedError
from psycopg import sql


def check_sql(query):
    """Reject PostgreSQL ? operators that collide with ODBC bind markers."""
    offset = 0
    while offset < len(query):
        char = query[offset]
        if char in ("'", '"'):
            delimiter = char
            escaped = (
                char == "'" and offset > 0 and query[offset - 1] in "eE"
                and (offset < 2 or not (query[offset - 2].isalnum() or query[offset - 2] in "_$"))
            )
            offset += 1
            while offset < len(query):
                if escaped and query[offset] == "\\":
                    offset += 2
                elif query[offset] == delimiter:
                    offset += 1
                    if query[offset : offset + 1] != delimiter:
                        break
                    offset += 1
                else:
                    offset += 1
        elif query.startswith("--", offset):
            end = query.find("\n", offset + 2)
            offset = len(query) if end < 0 else end + 1
        elif query.startswith("/*", offset):
            depth = 1
            offset += 2
            while depth and offset < len(query):
                if query.startswith("/*", offset):
                    depth += 1
                    offset += 2
                elif query.startswith("*/", offset):
                    depth -= 1
                    offset += 2
                else:
                    offset += 1
        elif char == "$" and (
            offset == 0 or not (query[offset - 1].isalnum() or query[offset - 1] in "_$")
        ) and (tag := re.match(r"\$(?:[A-Za-z_][A-Za-z0-9_]*)?\$", query[offset:])):
            end = query.find(tag[0], offset + len(tag[0]))
            offset = len(query) if end < 0 else end + len(tag[0])
        elif char == "?":
            raise NotSupportedError(
                "PostgreSQL ?/?|/?& operators conflict with ODBC parameter markers; "
                "use PostgreSQL jsonb_exists/jsonb_exists_any/jsonb_exists_all functions"
            )
        else:
            offset += 1


def quote(value):
    # No psycopg connection or pyodbc context is involved. The context-free
    # dumper emits E'' strings where needed, independently of session settings.
    return sql.Literal(value).as_string(context=None)


def interpolate(query, params, render):
    """Interpret DB-API %s, %(name)s and %% (including inside SQL literals)."""
    if params is None:
        return query, ()
    named = isinstance(params, Mapping)
    values = []
    result = []
    offset = 0
    positional = iter(()) if named else iter(params)
    while offset < len(query):
        if query[offset] != "%":
            result.append(query[offset])
            offset += 1
            continue
        offset += 1
        if offset < len(query) and query[offset] == "%":
            result.append("%")
            offset += 1
            continue
        if named and offset < len(query) and query[offset] == "(":
            end = query.find(")", offset + 1)
            if end == -1 or query[end + 1 : end + 2] != "s":
                raise ValueError("Invalid named DB-API placeholder")
            value = params[query[offset + 1 : end]]
            offset = end + 2
        elif not named and query[offset : offset + 1] == "s":
            try:
                value = next(positional)
            except StopIteration as exc:
                raise ValueError("Not enough SQL parameters") from exc
            offset += 1
        else:
            raise ValueError("Only %s, %(name)s and %% DB-API placeholders are supported")
        values.append(value)
        result.append(render(value))
    if not named:
        try:
            next(positional)
        except StopIteration:
            pass
        else:
            raise ValueError("Too many SQL parameters")
    return "".join(result), tuple(values)
