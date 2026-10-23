from django.db.backends.postgresql.introspection import (
    DatabaseIntrospection as PostgreSQLIntrospection,
    FieldInfo,
)
from psycopg.adapt import Transformer
from psycopg.pq import Format

from .operations import DatabaseOperations


def parse_pg_array(value):
    """Use PostgreSQL's text[] loader, without a connection, for catalog arrays."""
    if value is None or isinstance(value, list):
        return value
    if isinstance(value, tuple):
        return list(value)
    if isinstance(value, str):
        value = value.encode()
    # text[] handles quoted commas, escaped quotes/backslashes, NULL, dimensions
    # and nested arrays. Catalog name[]/char[] values use the same text syntax.
    return Transformer().get_loader(1009, Format.TEXT).load(value)


def boolean(value):
    return DatabaseOperations.convert_boolean(value, None, None)


class CatalogCursor:
    def __init__(self, cursor):
        self.cursor = cursor
        self.indexes = False

    def execute(self, sql, params=None):
        # Coupled to Django 5.2.18's two get_constraints() catalog projections:
        # constraint rows first, then the array_agg-based index projection.
        self.indexes = "array_agg" in sql
        return self.cursor.execute(sql, params)

    def fetchall(self):
        result = []
        for row in self.cursor.fetchall():
            row = list(row)
            row[1] = parse_pg_array(row[1])
            if self.indexes:
                row[2], row[3] = boolean(row[2]), boolean(row[3])
                row[4], row[7] = parse_pg_array(row[4]), parse_pg_array(row[7])
            else:
                row[4] = parse_pg_array(row[4])
            result.append(row)
        return result


class DatabaseIntrospection(PostgreSQLIntrospection):
    def get_constraints(self, cursor, table_name):
        return super().get_constraints(CatalogCursor(cursor), table_name)

    def get_table_description(self, cursor, table_name):
        # ODBC type codes are Python types, not PostgreSQL OIDs. Resolve OIDs,
        # typmods and domain base types directly without sampling a table row.
        cursor.execute(
            """
            SELECT a.attname,
                   CASE WHEN t.typtype = 'd' THEN t.typbasetype ELSE a.atttypid END,
                   a.attlen, CASE WHEN a.atttypmod = -1 THEN t.typtypmod ELSE a.atttypmod END,
                   NOT (a.attnotnull OR (t.typtype = 'd' AND t.typnotnull)),
                   pg_get_expr(ad.adbin, ad.adrelid),
                   CASE WHEN co.collname = 'default' THEN NULL ELSE co.collname END,
                   a.attidentity != '',
                   col_description(a.attrelid, a.attnum)
            FROM pg_attribute a
            JOIN pg_type t ON a.atttypid = t.oid
            JOIN pg_class c ON a.attrelid = c.oid
            LEFT JOIN pg_attrdef ad ON a.attrelid = ad.adrelid AND a.attnum = ad.adnum
            LEFT JOIN pg_collation co ON a.attcollation = co.oid
            WHERE c.oid = to_regclass(%s)
              AND a.attnum > 0 AND NOT a.attisdropped
            ORDER BY a.attnum
            """,
            [self.connection.ops.quote_name(table_name)],
        )
        result = []
        for name, oid, length, modifier, nullable, default, collation, identity, comment in cursor.fetchall():
            oid, length, modifier = int(oid), int(length), int(modifier)
            size = modifier - 4 if oid in (1042, 1043) and modifier >= 4 else length
            precision = scale = None
            if oid == 1700 and modifier >= 4:
                precision = ((modifier - 4) >> 16) & 65535
                scale = ((modifier - 4) & 2047)
                if scale >= 1024:
                    scale -= 2048
            result.append(FieldInfo(
                name, oid, size if size >= 0 else None, size if size >= 0 else None,
                precision, scale, boolean(nullable), default, collation,
                boolean(identity), comment,
            ))
        return result
