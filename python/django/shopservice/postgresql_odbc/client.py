from django.db import NotSupportedError
from django.db.backends.postgresql.client import DatabaseClient as PostgreSQLClient


class DatabaseClient(PostgreSQLClient):
    def runshell(self, parameters):
        raise NotSupportedError("dbshell/psql bypasses OJP and is disabled for postgresql_odbc")
