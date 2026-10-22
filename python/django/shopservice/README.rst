Django shopservice through OJP
=============================

This is the Django equivalent of ``python/sqlarchemy/shopservice``. All database
traffic uses pyodbc, the OJP ODBC driver and OJP's PostgreSQL JDBC connections.
There is no SQLite or direct PostgreSQL fallback. Install the OJP ODBC driver
and ensure the OJP server has the PostgreSQL JDBC driver available.

Setup and run
-------------

From this directory, with Python 3.10 or newer::

    python -m venv .venv
    . .venv/bin/activate
    pip install -e '.[test]'

Configure the environment (database host/port are resolved by the OJP server)::

    export OJP_SERVER=localhost:1059
    export ODBC_DRIVER=OJP
    export DB_HOST=localhost
    export DB_PORT=5432
    export DB_USER=postgres
    export DB_PASSWORD='your-password'
    export CATALOG_DB=django_catalog_db
    export CHECKOUT_DB=django_checkout_db

Both databases must already exist. Bootstrap their tables explicitly::

    python manage.py migrate --database=default
    python manage.py migrate --database=checkout
    python manage.py runserver 0.0.0.0:8000 --noreload

``default`` is the catalog alias: users, products and reviews. ``checkout``
contains orders and order items. The router also restricts migrations to their
owning database. Checkout stores catalog user/product IDs as integer values,
not cross-database foreign keys. Order replacement, orphan removal and order
deletion are atomic on checkout. There is no distributed transaction across
the databases.

The connection string uses braced, escaped values::

    DRIVER={OJP};SERVER={localhost:1059};DATABASE={jdbc:postgresql://localhost:5432/django_catalog_db?stringtype=unspecified};UID={postgres};******;

The backend is ``shopservice.odbc_backend`` and receives this string through
``OPTIONS["connection_string"]``. This is a project-specific PostgreSQL adapter,
not an official PostgreSQL backend supplied by ``mssql-django``. The pinned
``mssql-django==1.7.2`` dependency provides only its third-party pyodbc
``CursorWrapper`` transport; its SQL Server dialect, schema editor and
connection backend are not used. Generic Django backend classes and the local
adapter supply PostgreSQL SQL, schema operations, introspection and conversion
of OJP's text results. This adapter targets this sample's models and migrations,
not arbitrary Django applications or every PostgreSQL feature.

The JDBC URL must include ``?stringtype=unspecified``. OJP transports parameters
as text, including decimal and timestamp values. PostgreSQL JDBC's default
``setString`` binding sends VARCHAR parameters, which PostgreSQL cannot
implicitly assign to numeric/timestamp columns. With ``stringtype=unspecified``,
PostgreSQL infers each parameter's type from the SQL context.

Generated IDs use PostgreSQL ``serial``/``bigserial`` columns and a separate
``SELECT currval(pg_get_serial_sequence(...))`` on the same connection, never
``INSERT RETURNING``. The insert compiler wraps both statements in one
transaction so OJP retains the same JDBC session. Autocommit transitions
reconnect using pre-connect ODBC attributes because the driver lacks
``SQLSetConnectAttrW``. ORM query results are buffered before iteration; do not
hold raw cursors open across transaction-mode changes. Django persistent connections and pyodbc
pooling are disabled: connections are scoped to a request, while OJP owns
server-side pooling. For deployment, set ``DJANGO_SECRET_KEY`` and
``DJANGO_ALLOWED_HOSTS`` (comma-separated); debug defaults to off. The sample
API is intentionally unauthenticated and must not be exposed publicly without
appropriate access controls.

Production must set ``DJANGO_SECRET_KEY`` to a strong, stable secret shared by
all application workers. When it is absent or empty, development startup
generates a random key for that process instead of using a known signing key.
Such ephemeral keys are not suitable for production signing or future sessions.

REST contracts
--------------

``/users``, ``/products``, ``/reviews`` and ``/orders`` support POST and GET;
their ``/{id}`` routes support GET, PUT and DELETE. Successful creation/update
returns 200; deletion returns an empty 204. Missing resources return 404 and
invalid input returns an empty 400. Paths do not have trailing slashes.
``/orders/{id}/items`` also supports POST/GET and its ``/{item_id}`` supports
GET/PUT/DELETE. Item JSON omits the parent to avoid recursive responses.

Lists use Spring-compatible page envelopes with ``page`` and ``size`` (defaults
0 and 20, maximum size 2000). Reviews accept ``userId`` and ``productId`` filters;
``userId`` takes precedence. ``PUT /products`` deliberately only returns 201
with a Location header, without persisting anything, matching the reference.
User updates preserve ``createdAt``. Order creation accepts omitted items but
rejects an explicit empty list; order updates ignore empty or invalid-product
item lists and invalid catalog user IDs, preserving the previous values.

Tests
-----

Use dedicated databases: integration tests migrate both aliases and clear all
five application tables before and after every test. They never create/drop
databases. Do not point them at data you want to retain::

    pytest -q
    pytest -q -m integration
    pytest -q -m 'not integration'

The integration suite mirrors the reference's 30 repository/controller cases
and adds routing, filtering, pagination, malformed-input, cascade, orphan and
transaction rollback coverage. Unit tests do not connect to either database.
Use pytest rather than Django's test runner: the fixtures manually migrate the
two existing databases and do not invoke Django's automatic test-database
creation.

The ``.github/workflows/python-django-shopservice.yml`` workflow provisions
PostgreSQL 16 and both databases, builds/registers the OJP ODBC driver, starts
three OJP servers with separate metrics ports, and runs the entire suite.
Local integration runs require that same OJP/PostgreSQL infrastructure;
collecting tests or running unit tests alone does not validate the live path.
