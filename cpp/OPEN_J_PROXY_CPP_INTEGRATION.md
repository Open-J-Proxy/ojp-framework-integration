# Bringing Open J Proxy to your C++ application

[Open J Proxy](https://github.com/Open-J-Proxy/ojp), or OJP, is an open-source database proxy that puts connection management between your application and your database. It brings decades of enterprise software knowledge, shaped by the needs of the biggest organizations in the world, within reach of other languages—not just Java. For a C++ team, that means accessing the mature JDBC ecosystem through familiar ODBC calls, while OJP Server manages the real database connections. Your application stays native C++; Java runs on the server, not inside your application.

The basic architecture is **OJP ODBC client → gRPC → OJP Server → JDBC → Database**. Your C++ code calls the ODBC Driver Manager, which loads the OJP ODBC driver. That driver sends requests over gRPC to OJP Server, and the server uses a database-specific JDBC driver to execute them. Results travel back along the same route.

```mermaid
flowchart LR
    app["C++ application"] -->|"ODBC calls"| client["OJP ODBC client"]
    client -->|"gRPC"| server["OJP Server"]
    server -->|"JDBC"| db["Database"]
```

Let's make that concrete with the [C++ shopservice](https://github.com/Open-J-Proxy/ojp-framework-integration/tree/main/cpp/shopservice). It is a C++17 REST service with users, products, reviews, orders, and order items. Catalog data lives in `cpp_catalog_db`, while checkout data lives in `cpp_checkout_db`. **The current shopservice on `main` already connects through OJP and has no local connection pool.** We will use that working integration to show how to configure the client and how familiar ODBC code fits this architecture.

## Start with the connection lifecycle

Before installing anything, agree on one rule: **the client application cannot have an active connection pool when using OJP**. Disable both application or framework pooling and ODBC Driver Manager pooling. The application must open a connection when it needs database work and close it when that work finishes. OJP Server owns the backend pools, so closing an application connection does not mean throwing away the server's reusable physical database connection.

For C++, this fits naturally with RAII: create a connection object for a unit of work and let its destructor disconnect on every exit path, including exceptions. Keep the same connection throughout a transaction, commit or roll back explicitly, then close it. Do not open a separate connection for each statement inside a transaction, and do not keep idle connections around between requests.

In the current shopservice, the [ODBC implementation](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/odbc.cpp) calls `SQLDriverConnect` in the `Connection` constructor and `SQLDisconnect` in its destructor. `DataSource::acquire` creates a fresh connection, and the [lease owns that connection](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/include/shopservice/odbc.hpp) until it goes out of scope. There is no idle collection or local pool to disable. Separately, ensure Driver Manager pooling is off; when making that explicit in your own application, set `SQL_ATTR_CONNECTION_POOLING` to `SQL_CP_OFF` on `SQL_NULL_HENV` before allocating the first ODBC environment, and check the return value. Removing application pooling does not replace disabling Driver Manager pooling.

## Get the client and server ready

The [OJP C++ ODBC client guide](https://github.com/Open-J-Proxy/ojp/tree/main/ojp-client-cpp-odbc) is the source of truth for building and registering the driver. For a Linux development setup, you need a C++17 compiler, CMake 3.20 or newer, Ninja, Protobuf and gRPC C++ development packages, and unixODBC with its development headers. You also need the full OJP source tree because the build reads its shared protocol definition, plus an extracted [googleapis source archive](https://github.com/googleapis/googleapis) for the date and time protocol definitions. Currently, you need to build the client locally because precompiled distributions are not yet available. Work is underway to provide them and make installation easier.

On Debian or Ubuntu, the client guide installs the system tools like this:

```bash
sudo apt-get update
sudo apt-get install build-essential cmake ninja-build \
  libprotobuf-dev protobuf-compiler libgrpc++-dev protobuf-compiler-grpc \
  unixodbc unixodbc-dev odbcinst
```

Download and extract the [OJP source](https://github.com/Open-J-Proxy/ojp/archive/refs/heads/main.zip) and googleapis archive into your development workspace. From the OJP source root, build and install the driver with the commands below, replacing the googleapis path with your actual absolute path. Keep the client revision compatible with your chosen server image; do not assume that an older server release understands a newer client's protocol.

```bash
cd ojp-client-cpp-odbc
cmake --preset release -DGOOGLEAPIS_PROTO_DIR=/absolute/path/to/googleapis
cmake --build --preset release --parallel
cmake --install build/release --prefix "$PWD/install"
```

Your application continues to link to the ODBC Driver Manager, not directly to gRPC or the OJP driver library. The shopservice already does this through `ODBC::ODBC` in its [CMake configuration](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/CMakeLists.txt). No Java runtime is needed in the C++ process. The server runs on Java 25 or newer in UTC; Docker supplies its Java runtime, so you do not need to install Java on the host for this walkthrough.

## Run OJP Server in Docker

For this example, assume Docker is installed and SQL Server is already running and reachable from the OJP container. Create an `ojp-libs` directory, download the [Microsoft JDBC driver for SQL Server](https://learn.microsoft.com/en-us/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server), and put a compatible runtime JAR in that directory. OJP does not bundle backend JDBC drivers. You do not need Microsoft's ODBC driver: the C++ side uses OJP's ODBC driver, and the server side needs Microsoft's JDBC driver.

From the directory containing `ojp-libs`, start the container with the driver directory mounted. This local-development example binds the gRPC port to the host's loopback interface so your C++ application on the same host can connect without exposing OJP to the network:

```bash
docker run --rm -d \
  --name ojp-server \
  -p 127.0.0.1:1059:1059 \
  -v "$PWD/ojp-libs:/opt/ojp/ojp-libs:ro" \
  -e JAVA_TOOL_OPTIONS="-Duser.timezone=UTC" \
  rrobetti/ojp:1.0.3
```

The tag above follows the [Docker deployment guide](https://github.com/Open-J-Proxy/ojp/blob/main/documents/configuration/DOCKER_DEPLOYMENT.md); choose a compatible image tag for your client revision. Make sure the mounted directory and JDBC JAR are readable by the container's non-root user. Check startup with `docker logs ojp-server`, and stop it with `docker stop ojp-server` when you finish.

OJP listens for gRPC connections on port `1059` by default. Your C++ application must be able to reach that port, and OJP Server must be able to reach SQL Server, usually on port `1433`. A database hostname in a JDBC URL is resolved inside the container; `localhost` means the OJP container itself, not the Docker host. Use a database hostname reachable from that container, or attach both containers to a shared Docker network and use the SQL Server container's network name. If you prefer running without Docker, the [source-build guide](https://github.com/Open-J-Proxy/ojp/blob/main/documents/runnable-jar/BUILDING_FROM_SOURCE.md) explains building the server, and the [runnable JAR guide](https://github.com/Open-J-Proxy/ojp/blob/main/documents/runnable-jar/README.md) explains launching it as a Java executable JAR.

## Register the driver and make a connection

Run `odbcinst -j` to find your unixODBC configuration location, then add an entry like this to `odbcinst.ini`. Use the absolute path printed by the installation step; some systems install libraries under `lib64` rather than `lib`.

```ini
[OJP]
Description=Open J Proxy ODBC driver
Driver=/absolute/path/to/ojp-client-cpp-odbc/install/lib/libojp_odbc.so
```

Confirm registration with `odbcinst -q -d -n OJP`. The driver guide also explains user-owned configuration through `ODBCSYSINI` and registration on macOS and Windows. Match the driver's architecture to your application and keep its runtime dependencies available. Registration creates a driver entry, not a data source: the current client requires a DSN-less connection through `SQLDriverConnect`, rather than `SQLConnect` with a conventional DSN.

Now the connection string has two different destinations. `SERVER` names OJP Server, while `DATABASE` contains the backend JDBC URL. `UID` and `PWD` carry database credentials. For the shopservice catalog, the shape is:

```text
DRIVER={OJP};SERVER={localhost:1059};DATABASE={jdbc:sqlserver://sqlserver.example.internal:1433;databaseName=cpp_catalog_db;encrypt=true;trustServerCertificate=false};UID={shopservice};PWD=;
```

Replace the example host and username with your configuration, populate the deliberately empty `PWD` field with the database password from secure configuration, and use a SQL Server certificate trusted by the server JVM. Do not hard-code credentials or log the completed string. The braces around `DATABASE` matter because its JDBC URL contains semicolons; brace the password value too, and escape a literal closing brace in any value by doubling it. Notice that the URL starts with `jdbc:sqlserver:`, not `jdbc:ojp[...]`: the ODBC client already knows how to contact OJP.

For a first connection, the [upstream C++ smoke test](https://github.com/Open-J-Proxy/ojp/blob/main/ojp-client-cpp-odbc/README.md#using-from-a-c-odbc-application) provides a complete example with error checking and cleanup. Set its `OJP_ODBC_CONNECTION_STRING` environment variable to your connection string, compile it against the Driver Manager, and run its `SELECT 1` before trying the full service. That variable belongs to the smoke test; the current shopservice does not read it.

## Configure and run the shopservice

The shopservice's [connection-string builder](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/config.cpp) already produces the OJP format above. `ODBC_DRIVER` defaults to `OJP`, and `OJP_SERVER` defaults to `localhost:1059`. The separate `DB_HOST` and `DB_PORT` settings describe SQL Server as reached by OJP Server, not the proxy endpoint. The builder creates a backend JDBC URL for each database and puts `DB_ENCRYPT` and `DB_TRUST_SERVER_CERTIFICATE` into that URL. You do not need to rewrite the builder or pass a full connection string yourself.

Use a checkout of the integration repository that includes the merged OJP support. From its `cpp/shopservice` directory, install the application dependencies described in the [shopservice README](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/README.md#tech-stack), then build and run it as follows. Set `DB_PASSWORD` through your secure environment configuration before running these commands, and replace the example SQL Server hostname and login with your own. This example assumes a trusted SQL Server certificate; the service's development defaults instead disable backend encryption and trust the server certificate.

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel

export OJP_SERVER=localhost:1059
export DB_HOST=sqlserver.example.internal
export DB_PORT=1433
export DB_USER=shopservice
export DB_ENCRYPT=true
export DB_TRUST_SERVER_CERTIFICATE=false
./build/shopservice
```

The [schema initialization code](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/schema.cpp) connects through OJP to `master` to create missing databases, then creates missing tables in the catalog and checkout databases. The connection builder handles the `master` JDBC URL too. Your setup account therefore needs permission to create any missing databases and tables. The merged implementation executes table-creation commands separately and no longer calls the unsupported `SQLMoreResults`; there is no need to bypass schema startup to try this example. For a production service, plan schema provisioning and application permissions separately rather than granting broad setup privileges to every runtime request.

The existing [repository code](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/repositories.cpp) shows what stays familiar. Creating a user uses `SQLPrepare`, `SQLBindParameter`, and `SQLExecute`, with `?` placeholders for values. Reading products fetches rows through ODBC. Transactions request autocommit-off before `SQLDriverConnect`, because changing that attribute after connecting through unixODBC would require the driver's missing `SQLSetConnectAttrW` entry point. The `DataSource::inTransaction` helper opens that transaction connection, commits on success, rolls back on failure, and closes the connection when its lease ends.

Generated IDs are a useful detail to borrow from this implementation. A user insert is followed by `SELECT CAST(@@IDENTITY AS BIGINT)` inside the same transaction, which pins both calls to the same backend connection. The service avoids `OUTPUT INSERTED`, which its JDBC update path does not support. Its schema has no identity-generating triggers; if you add those, revisit the identity lookup because `@@IDENTITY` can return a trigger-generated value. Creating an order with its items likewise keeps all its statements in one transaction, completed with `SQLEndTran` before disconnecting. OJP does not translate SQL Server SQL into a different dialect or make two separate database transactions automatically atomic.

## Try real requests and run the tests

With the same environment configuration, run `ctest --test-dir build --output-on-failure` from the shopservice directory. The [integration suite](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/README.md#running-the-integration-tests) now needs both OJP Server and SQL Server: it creates databases and tables through OJP and starts the HTTP API in-process. It covers catalog and checkout operations, with generated-ID and transaction-rollback regression tests. Configuration-only tests can run without either server using `ctest --test-dir build -R shopservice_config_test --output-on-failure`. Use a disposable test database environment, not production data, because the suite creates and deletes application records.

The [C++ integration workflow](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/.github/workflows/cpp-shopservice.yml) builds the OJP driver and server, registers the driver, and runs the service tests through three OJP endpoints. To use several already-running servers yourself, set `OJP_SERVER` to a comma-separated value such as `localhost:1059,localhost:1060,localhost:1061`; the single-container example above needs only `localhost:1059`. This gives you a concrete reference for testing the proxy route rather than relying on a direct database connection.

The ODBC client is evolving and does not implement every ODBC API. Its [documented coverage and limitations](https://github.com/Open-J-Proxy/ojp/blob/main/ojp-client-cpp-odbc/README.md#using-other-languages-through-odbc) matter more than a blanket compatibility claim; Linux integration coverage does not imply that every platform or framework has been validated. Keep the initial experiment on a trusted development network and review the [production deployment guide](https://github.com/Open-J-Proxy/ojp/blob/main/documents/monitoring/PRODUCTION_DEPLOYMENT_GUIDE.md) before handling real credentials or exposing endpoints.

The appealing part is that your business code can remain recognizably C++. You keep ODBC, prepared statements, and scoped resource management, while moving backend connection ownership into OJP Server. Start with one successful query, run the integrated shopservice with your own endpoints, and use its connection lifecycle, transactions, and tests as a practical starting point for your application.
