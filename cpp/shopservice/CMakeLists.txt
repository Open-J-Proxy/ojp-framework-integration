cmake_minimum_required(VERSION 3.16)

project(shopservice VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

option(SHOPSERVICE_BUILD_TESTS "Build the ShopService integration tests" ON)

find_package(ODBC REQUIRED)
find_package(nlohmann_json 3.10 REQUIRED)
find_package(Threads REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(HTTPLIB REQUIRED IMPORTED_TARGET cpp-httplib)

add_library(shopservice_core
  src/odbc.cpp
  src/config.cpp
  src/schema.cpp
  src/model.cpp
  src/repositories.cpp
  src/app.cpp
)
target_include_directories(shopservice_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(shopservice_core
  PUBLIC ODBC::ODBC nlohmann_json::nlohmann_json PkgConfig::HTTPLIB Threads::Threads
)
target_compile_options(shopservice_core PRIVATE -Wall -Wextra)

add_executable(shopservice src/main.cpp)
target_link_libraries(shopservice PRIVATE shopservice_core)

if(SHOPSERVICE_BUILD_TESTS)
  enable_testing()
  find_package(GTest REQUIRED)

  add_executable(shopservice_it
    test/test_main.cpp
    test/CatalogRepositoryIT.cpp
    test/CheckoutRepositoryIT.cpp
    test/UserControllerIT.cpp
    test/ProductControllerIT.cpp
    test/ReviewControllerIT.cpp
    test/OrderItemControllerIT.cpp
    test/OrderAndOrderItemControllerIT.cpp
  )
  target_link_libraries(shopservice_it PRIVATE shopservice_core GTest::gtest)

  # A single ctest entry runs the whole suite so the SQL Server schema and the
  # embedded HTTP server are initialised once, like a Spring test context.
  add_test(NAME shopservice_it COMMAND shopservice_it)
endif()
