#include "shopservice/config.hpp"

#include "shopservice/odbc.hpp"

#include <cstdlib>
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
    config.host = envOr("DB_HOST", config.host);
    const std::string port = envOr("DB_PORT", std::to_string(config.port));
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
    return config;
}

std::string DatabaseConfig::connectionString(const std::string& database) const {
    using odbc::escapeConnectionValue;
    return "Driver=" + escapeConnectionValue(driver) +
           ";Server=" + escapeConnectionValue("tcp:" + host + "," + std::to_string(port)) +
           ";Database=" + escapeConnectionValue(database) +
           ";UID=" + escapeConnectionValue(username) +
           ";PWD=" + escapeConnectionValue(password) +
           ";Encrypt=" + (encrypt ? "yes" : "no") +
           ";TrustServerCertificate=" + (trustServerCertificate ? "yes" : "no") + ";";
}

}  // namespace shopservice
