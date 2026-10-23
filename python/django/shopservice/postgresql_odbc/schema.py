from django.db import NotSupportedError
from django.db.backends.postgresql.schema import DatabaseSchemaEditor as PostgreSQLSchemaEditor

from .literals import quote


class DatabaseSchemaEditor(PostgreSQLSchemaEditor):
    def quote_value(self, value):
        return quote(value)

    def _is_collation_deterministic(self, collation_name):
        value = super()._is_collation_deterministic(collation_name)
        return self.connection.ops.convert_boolean(value, None, self.connection)

    def column_sql(self, model, field, include_default=False):
        if field.get_internal_type() in {
            "ArrayField", "HStoreField", "IntegerRangeField", "BigIntegerRangeField",
            "DecimalRangeField", "DateTimeRangeField", "DateRangeField",
        }:
            raise NotSupportedError("PostgreSQL extension field adapters aren't supported over ODBC")
        return super().column_sql(model, field, include_default)
