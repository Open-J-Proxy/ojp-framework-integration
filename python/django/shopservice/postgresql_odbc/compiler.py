from django.db.backends.postgresql.compiler import (
    SQLAggregateCompiler,
    SQLCompiler,
    SQLDeleteCompiler,
    SQLInsertCompiler as PostgreSQLInsertCompiler,
    SQLUpdateCompiler,
)
from django.db.models.sql.compiler import SQLInsertCompiler as ValuesInsertCompiler


class SQLInsertCompiler(PostgreSQLInsertCompiler):
    # Keep PostgreSQL's compiler/RETURNING, but never bind lists for UNNEST.
    assemble_as_sql = ValuesInsertCompiler.assemble_as_sql
