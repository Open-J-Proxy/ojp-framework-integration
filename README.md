# ojp-framework-integration
Contains integration tests of OJP with the main Java frameworks

It also contains non-Java reference implementations of the shopservice:
- C++ ODBC shopservice against SQL Server (`cpp/shopservice`, via the OJP ODBC driver and OJP servers): see [cpp/shopservice/README.md](cpp/shopservice/README.md)
- Python SQLAlchemy shopservice against SQL Server (`python/sqlarchemy/shopservice`, SQLAlchemy over pyodbc and the OJP ODBC driver, with all database operations going through OJP): see [python/sqlarchemy/shopservice/README.md](python/sqlarchemy/shopservice/README.md)
- Python Django shopservice against PostgreSQL (`python/django/shopservice`, reuses Django's PostgreSQL backend through an ODBC bridge and the OJP ODBC driver): see [python/django/shopservice/README.rst](python/django/shopservice/README.rst).

## Running integration workflows manually

In GitHub Actions, select the Java, C++, or Python integration workflow and click
**Run workflow**. Set `ojp_branch` to the branch of `Open-J-Proxy/ojp` to build
and test (default: `main`). The workflow branch selector controls the integration
repository branch, while `ojp_branch` controls the OJP source branch.

Push, pull request, and `ojp-updated` repository dispatch runs always build OJP
from `main`.
