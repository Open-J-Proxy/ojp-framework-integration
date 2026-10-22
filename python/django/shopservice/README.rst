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

    DRIVER={OJP};SERVER={localhost:1059};DATABASE={jdbc:postgresql://localhost:5432/django_catalog_db};UID={postgres};******;

The backend is ``shopservice.odbc_backend`` and receives this string through
``OPTIONS["connection_string"]``. Django persistent connections and pyodbc
pooling are disabled: connections are scoped to a request, while OJP owns
server-side pooling. For deployment, set ``DJANGO_SECRET_KEY`` and
``DJANGO_ALLOWED_HOSTS`` (comma-separated); debug defaults to off. The sample
API is intentionally unauthenticated and must not be exposed publicly without
appropriate access controls.

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
