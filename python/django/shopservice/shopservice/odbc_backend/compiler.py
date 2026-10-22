"""Keep generated-key retrieval on the same OJP backend transaction as INSERT."""

from django.db import transaction
from django.db.models.sql.compiler import (
    SQLAggregateCompiler,
    SQLCompiler,
    SQLDeleteCompiler,
    SQLInsertCompiler as BaseSQLInsertCompiler,
    SQLUpdateCompiler,
)


class SQLInsertCompiler(BaseSQLInsertCompiler):
    def execute_sql(self, returning_fields=None):
        # OJP can lease a different JDBC session for each autocommit statement.
        with transaction.atomic(using=self.using):
            return super().execute_sql(returning_fields)
