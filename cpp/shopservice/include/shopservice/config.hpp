#pragma once

#include <string>

namespace shopservice {

// OJP endpoints and backend SQL Server settings, read from environment variables.
struct DatabaseConfig {
    std::string driver = "OJP";
    std::string ojpServer = "localhost:1059";
    std::string host = "localhost";
    int port = 1433;
    std::string username = "sa";
    std::string password;
    std::string catalogDatabase = "cpp_catalog_db";
    std::string checkoutDatabase = "cpp_checkout_db";
    bool encrypt = false;
    bool trustServerCertificate = true;

    static DatabaseConfig fromEnvironment();

    std::string jdbcUrl(const std::string& database) const;
    // SERVER targets OJP; DATABASE is the backend JDBC URL.
    std::string connectionString(const std::string& database) const;
};

// Returns the value of an environment variable or the fallback when unset/empty.
std::string envOr(const char* name, const std::string& fallback);

// True when name is a safe SQL Server identifier for the databases we create.
bool isValidDatabaseName(const std::string& name);

}  // namespace shopservice
