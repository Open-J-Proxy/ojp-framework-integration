from django.db.backends.postgresql.features import DatabaseFeatures as PostgreSQLFeatures


class DatabaseFeatures(PostgreSQLFeatures):
    can_use_chunked_reads = False
    uses_server_side_binding = True
    supports_timezones = True
    has_json_operators = False
