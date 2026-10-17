#include "shopservice/config.hpp"

#include <gtest/gtest.h>

#include <cstdlib>
#include <optional>
#include <stdexcept>

namespace shopservice::test {
namespace {

class ScopedEnvironment {
public:
    ScopedEnvironment(const char* name, const char* value) : name_(name) {
        if (const char* old = std::getenv(name)) previous_ = old;
        if (value) setenv(name, value, 1);
        else unsetenv(name);
    }
    ~ScopedEnvironment() {
        if (previous_) setenv(name_.c_str(), previous_->c_str(), 1);
        else unsetenv(name_.c_str());
    }

private:
    std::string name_;
    std::optional<std::string> previous_;
};

TEST(DatabaseConfigTest, DefaultsRouteThroughOjp) {
    DatabaseConfig config;
    EXPECT_EQ("OJP", config.driver);
    EXPECT_EQ("localhost:1059", config.ojpServer);
    EXPECT_EQ("Driver={OJP};Server={localhost:1059};"
              "Database={jdbc:sqlserver://localhost:1433;databaseName=cpp_catalog_db;"
              "encrypt=false;trustServerCertificate=true};UID={sa};PW" "D={};",
              config.connectionString(config.catalogDatabase));
    EXPECT_NE(std::string::npos, config.connectionString("master").find("databaseName=master;"));
    EXPECT_NE(std::string::npos, config.connectionString(config.checkoutDatabase).find("databaseName=cpp_checkout_db;"));
}

TEST(DatabaseConfigTest, ClusterAndBackendSettingsAreSeparate) {
    DatabaseConfig config;
    config.ojpServer = "proxy-a:1059,proxy-b:1060,proxy-c:1061";
    config.host = "sql.internal";
    config.port = 1444;
    config.encrypt = true;
    config.trustServerCertificate = false;
    EXPECT_EQ("jdbc:sqlserver://sql.internal:1444;databaseName=catalog;"
              "encrypt=true;trustServerCertificate=false", config.jdbcUrl("catalog"));
    EXPECT_NE(std::string::npos, config.connectionString("catalog").find(
        "Server={proxy-a:1059,proxy-b:1060,proxy-c:1061};"));
}

TEST(DatabaseConfigTest, CredentialsAreOdbcEscaped) {
    DatabaseConfig config;
    config.username = "user};UID=other";
    config.password = "value};Server=other";
    EXPECT_NE(std::string::npos, config.connectionString("catalog").find(
        "UID={user}};UID=other};PW" "D={value}};Server=other};"));
}

TEST(DatabaseConfigTest, RejectsJdbcPropertyInjection) {
    DatabaseConfig config;
    EXPECT_THROW(config.connectionString("catalog;encrypt=false"), std::invalid_argument);
    config.host = "localhost;databaseName=other";
    EXPECT_THROW(config.connectionString("catalog"), std::invalid_argument);
}

TEST(DatabaseConfigTest, RejectsInvalidEndpointsAndBackendPorts) {
    DatabaseConfig config;
    for (const auto* endpoint : {"localhost", "localhost:1059;UID=other", "host:1059,", ""}) {
        config.ojpServer = endpoint;
        EXPECT_THROW(config.connectionString("catalog"), std::invalid_argument);
    }
    config.ojpServer = "localhost:1059";
    for (int port : {0, -1, 65536}) {
        config.port = port;
        EXPECT_THROW(config.connectionString("catalog"), std::invalid_argument);
    }
}

TEST(DatabaseConfigTest, ReadsOjpEndpointFromEnvironment) {
    ScopedEnvironment endpoint("OJP_SERVER", "proxy-a:1059,proxy-b:1060");
    ScopedEnvironment host("DB_HOST", "sql.internal");
    ScopedEnvironment port("DB_PORT", "1444");
    const auto config = DatabaseConfig::fromEnvironment();
    EXPECT_EQ("proxy-a:1059,proxy-b:1060", config.ojpServer);
    EXPECT_EQ("sql.internal", config.host);
    EXPECT_EQ(1444, config.port);
}

TEST(DatabaseConfigTest, RejectsMalformedPortFromEnvironment) {
    for (const auto* value : {"1433oops", "-1", "65536", "99999999999999999"}) {
        ScopedEnvironment port("DB_PORT", value);
        EXPECT_THROW(DatabaseConfig::fromEnvironment(), std::invalid_argument);
    }
}

}  // namespace
}  // namespace shopservice::test
