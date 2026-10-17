# Bringing Open J Proxy to your C++ application

[Open J Proxy](https://github.com/Open-J-Proxy/ojp), or OJP, is an open-source database proxy that puts connection management between your application and your database. It brings decades of enterprise software knowledge, shaped by the needs of the biggest organizations in the world, within reach of other languages—not just Java. For a C++ team, that means accessing the mature JDBC ecosystem through familiar ODBC calls, while OJP Server manages the real database connections. Your application stays native C++; Java runs on the server, not inside your application.

The basic architecture is **OJP ODBC client → gRPC → OJP Server → JDBC → Database**. Your C++ code calls the ODBC Driver Manager, which loads the OJP ODBC driver. That driver sends requests over gRPC to OJP Server, and the server uses a database-specific JDBC driver to execute them. Results travel back along the same route.

```mermaid
flowchart LR
    app["C++ application"] -->|"ODBC calls"| client["OJP ODBC client"]
    client -->|"gRPC"| server["OJP Server"]
    server -->|"JDBC"| db["Database"]
```

Let's make that concrete with the [C++ shopservice](https://github.com/Open-J-Proxy/ojp-framework-integration/tree/main/cpp/shopservice). It is a C++17 REST service with users, products, reviews, orders, and order items. Catalog data lives in `cpp_catalog_db`, while checkout data lives in `cpp_checkout_db`. There is an important starting-point detail: **the current shopservice on `main` connects directly to SQL Server, but no longer has a local connection pool. It is a reference for the migration described here, not an already-integrated OJP application.** We will use its existing ODBC design to explain what changes, without pretending that switching a driver name is enough.

## Start with the connection lifecycle

Before installing anything, agree on one rule: **the client application cannot have an active connection pool when using OJP**. Disable both application or framework pooling and ODBC Driver Manager pooling. The application must open a connection when it needs database work and close it when that work finishes. OJP Server owns the backend pools, so closing an application connection does not mean throwing away the server's reusable physical database connection.

For C++, this fits naturally with RAII: create a connection object for a unit of work and let its destructor disconnect on every exit path, including exceptions. Keep the same connection throughout a transaction, commit or roll back explicitly, then close it. Do not open a separate connection for each statement inside a transaction, and do not keep idle connections around between requests.

In the current shopservice, the [ODBC implementation](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/odbc.cpp) already calls `SQLDriverConnect` in the `Connection` constructor and `SQLDisconnect` in its destructor. `DataSource::acquire` creates a fresh connection, and the [lease owns that connection](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/include/shopservice/odbc.hpp) until it goes out of scope. There is no idle collection or local pool to disable, so keep this lifecycle for OJP. Separately, set `SQL_ATTR_CONNECTION_POOLING` to `SQL_CP_OFF` on `SQL_NULL_HENV` before allocating the first ODBC environment, and check the return value. Removing application pooling does not replace disabling Driver Manager pooling.

## Get the client and server ready

The [OJP C++ ODBC client guide](https://github.com/Open-J-Proxy/ojp/tree/main/ojp-client-cpp-odbc) is the source of truth for building and registering the driver. For a Linux development setup, you need a C++17 compiler, CMake 3.20 or newer, Ninja, Protobuf and gRPC C++ development packages, and unixODBC with its development headers. You also need the full OJP source tree because the build reads its shared protocol definition, plus an extracted [googleapis source archive](https://github.com/googleapis/googleapis) for the date and time protocol definitions. Prebuilt ODBC binaries are not currently published, so plan to build the driver yourself.

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

For this example, assume Docker is installed and SQL Server is already running and reachable from the OJP container. Create an `ojp-libs` directory, download the [Microsoft JDBC driver for SQL Server](https://learn.microsoft.com/en-us/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server), and put a compatible runtime JAR in that directory. OJP does not bundle backend JDBC drivers. The Microsoft ODBC driver used by the original shopservice is not a substitute: the C++ side now uses OJP's ODBC driver, and the server side needs Microsoft's JDBC driver.

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

## Apply the same idea to the shopservice

The shopservice's [connection-string builder](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/config.cpp) currently produces Microsoft ODBC settings such as `Server={tcp:host,port}` and a plain database name. An OJP adaptation must instead produce the connection string above, with an OJP endpoint and a full JDBC URL. Set `DRIVER` to `OJP`, use one JDBC URL for `cpp_catalog_db` and another for `cpp_checkout_db`, and move SQL Server encryption settings into those URLs. **Changing only `ODBC_DRIVER=OJP` will not work.** The existing `DB_HOST` and `DB_PORT` describe SQL Server, so keep the OJP endpoint distinct rather than silently reusing those settings.

There is also a startup detail worth catching early. The [schema initialization code](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/schema.cpp) first connects to `master` to create missing databases. If you retain that behavior, the adapted builder must produce a JDBC URL for `master` too, and the startup account needs database-creation permissions. In a deployed service, provisioning databases separately lets the application use a more restricted account. Neither approach requires changing the catalog and checkout split.

Startup needs one more adaptation: `Connection::executeBatch` drains batch results with `SQLMoreResults`, which the current OJP ODBC driver does not support. The [service constructor](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/app.cpp) always runs the table-creation batches, even if the databases already exist. A practical migration is to provision both databases and their tables externally, then bypass those initialization calls in the OJP version of the service and its test setup. Alternatively, rewrite initialization to execute individual statements through supported APIs without the `SQLMoreResults` path. Precreating only the databases will not avoid this startup failure.

Once connection configuration and startup are adapted and Driver Manager pooling is disabled, the existing [repository code](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/src/repositories.cpp) gives useful examples of what can stay familiar. Creating a user still uses `SQLPrepare`, `SQLBindParameter`, and `SQLExecute`, with `?` placeholders for values. Reading products still fetches rows through ODBC. Creating an order with its items still needs one connection and one transaction, completed with `SQLEndTran` before disconnecting. OJP changes the route to the database; it does not translate SQL Server SQL into a different dialect or make two separate database transactions automatically atomic.

## Try real requests before calling the migration done

After adapting the service, follow its [build and test instructions](https://github.com/Open-J-Proxy/ojp-framework-integration/blob/main/cpp/shopservice/README.md#building-and-running) to build it and run the integration suite against SQL Server through OJP. Exercise user creation, product prices, Unicode review comments, and orders with nested items, as well as rollback and error paths. The checked-in suite currently tests direct SQL Server access, so a passing unmodified run is not evidence that the OJP route works. Check that requests actually reach OJP and that returning a datasource lease disconnects rather than caching an idle client connection.

The ODBC client is evolving and does not implement every ODBC API. Its [documented coverage and limitations](https://github.com/Open-J-Proxy/ojp/blob/main/ojp-client-cpp-odbc/README.md#using-other-languages-through-odbc) matter more than a blanket compatibility claim; Linux integration coverage does not imply that every platform or framework has been validated. Keep the initial experiment on a trusted development network and review the [production deployment guide](https://github.com/Open-J-Proxy/ojp/blob/main/documents/monitoring/PRODUCTION_DEPLOYMENT_GUIDE.md) before handling real credentials or exposing endpoints.

The appealing part is that your business code can remain recognizably C++. You keep ODBC, prepared statements, and scoped resource management, while moving backend connection ownership into OJP Server. Start with one successful query, adapt the shopservice's two connection destinations, connection lifecycle, and schema startup, and let its real workflows show you whether the integration is ready for your application.
