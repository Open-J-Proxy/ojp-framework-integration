# ojp-framework-integration
Contains integration tests of OJP with the main Java frameworks

It also contains non-Java reference implementations of the shopservice:
- C++ ODBC shopservice against SQL Server (`cpp/shopservice`, direct ODBC connections for now): see [cpp/shopservice/README.md](cpp/shopservice/README.md)
- Python SQLAlchemy shopservice against SQL Server (`python/shopservice`, SQLAlchemy over the native ODBC driver, direct connections for now): see [python/shopservice/README.md](python/shopservice/README.md)
