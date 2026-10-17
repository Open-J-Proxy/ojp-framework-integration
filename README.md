# ojp-framework-integration
Contains integration tests of OJP with the main Java frameworks

It also contains non-Java reference implementations of the shopservice:
- C++ ODBC shopservice against SQL Server (`cpp/shopservice`, via the OJP ODBC driver and OJP servers): see [cpp/shopservice/README.md](cpp/shopservice/README.md)
- Python SQLAlchemy shopservice against SQL Server (`python/shopservice`, SQLAlchemy over pyodbc and the OJP ODBC driver, with all database operations going through OJP): see [python/shopservice/README.md](python/shopservice/README.md)
