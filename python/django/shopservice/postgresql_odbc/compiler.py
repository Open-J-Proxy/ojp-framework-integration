from contextvars import ContextVar

from django.db.backends.postgresql.compiler import (
    SQLAggregateCompiler as PostgreSQLAggregateCompiler,
    SQLCompiler as PostgreSQLCompiler,
    SQLDeleteCompiler,
    SQLInsertCompiler as PostgreSQLInsertCompiler,
    SQLUpdateCompiler,
)
from django.db.models.sql.compiler import SQLInsertCompiler as ValuesInsertCompiler
from django.db.models.expressions import ColPairs


# UNION components and aggregate subqueries must keep their native SQL types.
_compiling = ContextVar("postgresql_odbc_compiling", default=False)


class IntervalResultsMixin:
    def as_sql(self, *args, **kwargs):
        outermost = not _compiling.get() and not self.query.subquery
        token = _compiling.set(True)
        try:
            sql, params = super().as_sql(*args, **kwargs)
        finally:
            _compiling.reset(token)
        if not outermost:
            return sql, params
        expressions = (
            list(self.query.annotation_select.values())
            if isinstance(self, PostgreSQLAggregateCompiler)
            else [
                expression for expression, _, _ in
                self.select + getattr(self, "_odbc_extra_select", [])
            ]
        )
        expressions = [
            column
            for expression in expressions
            for column in (
                expression.get_cols() if isinstance(expression, ColPairs) else [expression]
            )
        ]
        if any(
            expression.output_field.get_internal_type() == "DurationField"
            for expression in expressions
        ):
            # Normalize only the final results. Casting inside the original
            # query would change interval ordering, DISTINCT and arithmetic.
            aliases = [f"col{index}" for index in range(len(expressions))]
            columns = [
                f"{alias}::text"
                if expression.output_field.get_internal_type() == "DurationField" else alias
                for alias, expression in zip(aliases, expressions)
            ]
            explain_prefix = ""
            if self.query.explain_info:
                info = self.query.explain_info
                prefix = self.connection.ops.explain_query_prefix(info.format, **info.options) + " "
                if sql.startswith(prefix):
                    explain_prefix, sql = prefix, sql[len(prefix):]
            sql = f"SELECT {', '.join(columns)} FROM ({sql}) AS ojp_result ({', '.join(aliases)})"
            sql = explain_prefix + sql
        return sql, params


class SQLCompiler(IntervalResultsMixin, PostgreSQLCompiler):
    def get_extra_select(self, order_by, select):
        self._odbc_extra_select = super().get_extra_select(order_by, select)
        return self._odbc_extra_select


class SQLAggregateCompiler(IntervalResultsMixin, PostgreSQLAggregateCompiler):
    pass


class SQLInsertCompiler(PostgreSQLInsertCompiler):
    # Keep PostgreSQL's compiler/RETURNING, but never bind lists for UNNEST.
    assemble_as_sql = ValuesInsertCompiler.assemble_as_sql
