#include "shopservice/config.hpp"

#include "shopservice/odbc.hpp"

#include <cstdlib>
#include <regex>
#include <stdexcept>

namespace shopservice {

std::string envOr(const char* name, const std::string& fallback) {
    const char* value = std::getenv(name);
    return (value != nullptr && *value != '\0') ? std::string(value) : fallback;
}

namespace {

bool envFlag(const char* name, bool fallback) {
    std::string value = envOr(name, fallback ? "true" : "false");
    return value == "true" || value == "TRUE" || value == "yes" || value == "1";
}

}  // namespace

bool isValidDatabaseName(const std::string& name) {
    if (name.empty() || name.size() > 128) {
        return false;
    }
    for (char c : name) {
        const bool ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
        if (!ok) {
            return false;
        }
    }
    return true;
}

DatabaseConfig DatabaseConfig::fromEnvironment() {
    DatabaseConfig config;
    config.driver = envOr("ODBC_DRIVER", config.driver);
    config.ojpServer = envOr("OJP_SERVER", config.ojpServer);
    config.host = envOr("DB_HOST", config.host);
    const std::string port = envOr("DB_PORT", std::to_string(config.port));
    if (port.empty() || port.find_first_not_of("0123456789") != std::string::npos) {
        throw std::invalid_argument("DB_PORT must be a number");
    }
    try {
        config.port = std::stoi(port);
    } catch (const std::exception&) {
        throw std::invalid_argument("DB_PORT must be a number");
    }
    config.username = envOr("DB_USER", config.username);
    config.password = envOr("DB_PASSWORD", config.password);
    config.catalogDatabase = envOr("CATALOG_DB", config.catalogDatabase);
    config.checkoutDatabase = envOr("CHECKOUT_DB", config.checkoutDatabase);
    config.encrypt = envFlag("DB_ENCRYPT", config.encrypt);
    config.trustServerCertificate = envFlag("DB_TRUST_SERVER_CERTIFICATE", config.trustServerCertificate);

    if (!isValidDatabaseName(config.catalogDatabase) || !isValidDatabaseName(config.checkoutDatabase)) {
        throw std::invalid_argument("CATALOG_DB and CHECKOUT_DB may only contain letters, digits and '_'");
    }
    config.connectionString(config.catalogDatabase);
    config.jdbcUrl(config.checkoutDatabase);
    return config;
}

std::string DatabaseConfig::jdbcUrl(const std::string& database) const {
    if (!isValidDatabaseName(database)) {
        throw std::invalid_argument("Invalid database name");
    }
    if (!std::regex_match(host, std::regex("[A-Za-z0-9.-]{1,253}"))) {
        throw std::invalid_argument("DB_HOST may only contain letters, digits, '.' and '-'");
    }
    if (port < 1 || port > 65535) {
        throw std::invalid_argument("DB_PORT must be between 1 and 65535");
    }
    return "jdbc:sqlserver://" + host + ":" + std::to_string(port) +
           ";databaseName=" + database +
           ";encrypt=" + (encrypt ? "true" : "false") +
           ";trustServerCertificate=" + (trustServerCertificate ? "true" : "false");
}

std::string DatabaseConfig::connectionString(const std::string& database) const {
    using odbc::escapeConnectionValue;
    if (!std::regex_match(ojpServer, std::regex(
            "[A-Za-z0-9.-]{1,253}:[0-9]{1,5}(,[A-Za-z0-9.-]{1,253}:[0-9]{1,5})*"))) {
        throw std::invalid_argument("OJP_SERVER must be 'host:port' or a comma-separated list of endpoints");
    }
    return "Driver=" + escapeConnectionValue(driver) +
           ";Server=" + escapeConnectionValue(ojpServer) +
           ";Database=" + escapeConnectionValue(jdbcUrl(database)) +
           ";UID=" + escapeConnectionValue(username) +
           ";PWD=" + escapeConnectionValue(password) +
           ";";
}

}  // namespace shopservice
