import json
import re
from datetime import date, datetime, time
from decimal import Decimal
from uuid import UUID

from django.conf import settings
from django.db.backends.postgresql.operations import DatabaseOperations as PostgreSQLOperations
from django.utils import timezone
from django.utils.dateparse import parse_duration

from .literals import interpolate, quote


class DatabaseOperations(PostgreSQLOperations):
    compiler_module = "postgresql_odbc.compiler"

    def adapt_integerfield_value(self, value, internal_type):
        return value

    def adapt_ipaddressfield_value(self, value):
        return str(value) if value else None

    def adapt_json_value(self, value, encoder):
        return json.dumps(value, cls=encoder)

    def return_insert_columns(self, fields):
        sql, params = super().return_insert_columns(fields)
        if any(field.get_internal_type() == "DurationField" for field in fields):
            columns = [
                f"{self.quote_name(field.model._meta.db_table)}.{self.quote_name(field.column)}"
                + ("::text" if field.get_internal_type() == "DurationField" else "")
                for field in fields
            ]
            sql = "RETURNING " + ", ".join(columns)
        return sql, params

    def compose_sql(self, sql, params):
        return interpolate(sql, params, quote)[0]

    def last_executed_query(self, cursor, sql, params):
        return self.compose_sql(sql, params)

    def quote_name(self, name):
        if name.startswith('"') and name.endswith('"'):
            return name
        return '"' + name.replace('"', '""') + '"'

    def _savepoint_name(self, sid):
        # OJP intercepts these commands and doesn't accept quoted identifiers.
        if not isinstance(sid, str) or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_$]*", sid):
            raise ValueError("Invalid OJP savepoint identifier")
        return sid

    def savepoint_create_sql(self, sid):
        return "SAVEPOINT " + self._savepoint_name(sid)

    def savepoint_rollback_sql(self, sid):
        return "ROLLBACK TO SAVEPOINT " + self._savepoint_name(sid)

    def savepoint_commit_sql(self, sid):
        return "RELEASE SAVEPOINT " + self._savepoint_name(sid)

    def get_db_converters(self, expression):
        converters = super().get_db_converters(expression)
        field_type = expression.output_field.get_internal_type()
        if field_type in {
            "AutoField", "BigAutoField", "SmallAutoField", "IntegerField",
            "BigIntegerField", "SmallIntegerField", "PositiveIntegerField",
            "PositiveBigIntegerField", "PositiveSmallIntegerField",
        }:
            converters.append(self.convert_integer)
        converter = {
            "DecimalField": self.convert_decimal,
            "DateTimeField": self.convert_datetime,
            "DateField": self.convert_date,
            "TimeField": self.convert_time,
            "BooleanField": self.convert_boolean,
            "FloatField": self.convert_float,
            "UUIDField": self.convert_uuid,
            "BinaryField": self.convert_binary,
            "DurationField": self.convert_duration,
        }.get(field_type)
        if converter:
            converters.append(converter)
        return converters

    @staticmethod
    def convert_integer(value, expression, connection):
        return int(value) if value is not None else None

    @staticmethod
    def convert_decimal(value, expression, connection):
        return Decimal(str(value)) if value is not None else None

    @staticmethod
    def convert_float(value, expression, connection):
        return float(value) if value is not None else None

    @staticmethod
    def convert_boolean(value, expression, connection):
        if value is None or isinstance(value, bool):
            return value
        if value in (1, "1", "t", "true"):
            return True
        if value in (0, "0", "f", "false"):
            return False
        raise ValueError(f"Invalid PostgreSQL boolean: {value!r}")

    @staticmethod
    def convert_datetime(value, expression, connection):
        if value is None:
            return None
        if isinstance(value, str):
            value = datetime.fromisoformat(value)
        if settings.USE_TZ and timezone.is_naive(value):
            value = timezone.make_aware(value, connection.timezone)
        elif not settings.USE_TZ and timezone.is_aware(value):
            value = timezone.make_naive(value, connection.timezone)
        return value

    @staticmethod
    def convert_date(value, expression, connection):
        return date.fromisoformat(value) if isinstance(value, str) else value

    @staticmethod
    def convert_time(value, expression, connection):
        return time.fromisoformat(value) if isinstance(value, str) else value

    @staticmethod
    def convert_uuid(value, expression, connection):
        return UUID(value) if isinstance(value, str) else value

    @staticmethod
    def convert_binary(value, expression, connection):
        if isinstance(value, str) and value.startswith("\\x"):
            return bytes.fromhex(value[2:])
        return bytes(value) if value is not None else None

    @staticmethod
    def convert_duration(value, expression, connection):
        if isinstance(value, str):
            result = parse_duration(value)
            if result is None:
                raise ValueError(f"Unsupported PostgreSQL interval: {value!r}")
            return result
        return value
