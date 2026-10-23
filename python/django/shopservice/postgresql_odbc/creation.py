import re
import sys

from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.postgresql.creation import DatabaseCreation as PostgreSQLCreation

from .literals import quote


class DatabaseCreation(PostgreSQLCreation):
    def _get_database_create_suffix(self, encoding=None, template=None):
        parts = []
        if encoding:
            parts.append("ENCODING " + quote(encoding))
        if template:
            parts.append("TEMPLATE " + self._quote_name(template))
        return "WITH " + " ".join(parts) if parts else ""

    def _execute_create_test_db(self, cursor, parameters, keepdb=False):
        if keepdb and self._database_exists(cursor, parameters["dbname"]):
            return
        try:
            # Bypass PostgreSQLCreation's psycopg DuplicateDatabase check.
            BaseDatabaseCreation._execute_create_test_db(self, cursor, parameters, keepdb)
        except Exception as exc:
            cause = exc.__cause__ or exc
            duplicate = any(
                re.search(r"(?<![A-Z0-9])42P04(?![A-Z0-9])", str(arg))
                for arg in cause.args
            )
            if not duplicate:
                self.log("Got an error creating the test database: %s" % exc)
                sys.exit(2)
            if not keepdb:
                raise
