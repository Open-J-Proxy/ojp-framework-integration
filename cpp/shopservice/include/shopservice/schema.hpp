#pragma once

#include "shopservice/config.hpp"
#include "shopservice/odbc.hpp"

namespace shopservice {

// Creates the catalog and checkout databases on the server when they are missing.
void ensureDatabasesExist(const odbc::Environment& env, const DatabaseConfig& config);

// Creates the cpp_* tables in each database when they are missing.
void createCatalogSchema(odbc::Connection& conn);
void createCheckoutSchema(odbc::Connection& conn);

}  // namespace shopservice
