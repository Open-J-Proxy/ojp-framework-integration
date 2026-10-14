#pragma once

// Thin RAII wrapper around the standard ODBC 3.x C API.
// Only standard ODBC calls are used so any compliant driver/driver manager works.

#include <sql.h>
#include <sqlext.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <vector>

namespace shopservice::odbc {

class OdbcError : public std::runtime_error {
public:
    OdbcError(const std::string& message, std::string sqlState)
        : std::runtime_error(message), sqlState_(std::move(sqlState)) {}

    const std::string& sqlState() const { return sqlState_; }

    // SQLSTATE class 08 = connection exception.
    bool isConnectionError() const { return sqlState_.rfind("08", 0) == 0; }

private:
    std::string sqlState_;
};

// A statement parameter. Values are bound with SQLBindParameter.
class Param {
public:
    enum class Kind { Null, Int64, Int32, String, Decimal };

    static Param null() { return Param(Kind::Null); }
    static Param int64(std::int64_t v) { Param p(Kind::Int64); p.i64_ = v; return p; }
    static Param int32(std::int32_t v) { Param p(Kind::Int32); p.i32_ = v; return p; }
    static Param string(std::string v) { Param p(Kind::String); p.str_ = std::move(v); return p; }
    // Decimal value given in its canonical textual form (e.g. "19.99").
    static Param decimal(std::string v, SQLULEN precision, SQLSMALLINT scale) {
        Param p(Kind::Decimal);
        p.str_ = std::move(v);
        p.precision_ = precision;
        p.scale_ = scale;
        return p;
    }
    static Param optionalString(const std::optional<std::string>& v) {
        return v ? string(*v) : null();
    }

    Kind kind() const { return kind_; }

private:
    explicit Param(Kind k) : kind_(k) {}

    friend class Connection;

    Kind kind_;
    std::int64_t i64_ = 0;
    std::int32_t i32_ = 0;
    std::string str_;
    SQLULEN precision_ = 0;
    SQLSMALLINT scale_ = 0;
    SQLLEN indicator_ = 0;
};

// Every column is fetched as text; NULL is represented as std::nullopt.
using Row = std::vector<std::optional<std::string>>;

class Environment {
public:
    Environment();
    ~Environment();
    Environment(const Environment&) = delete;
    Environment& operator=(const Environment&) = delete;

    SQLHENV handle() const { return env_; }

private:
    SQLHENV env_ = SQL_NULL_HENV;
};

class Connection {
public:
    Connection(const Environment& env, const std::string& connectionString);
    ~Connection();
    Connection(const Connection&) = delete;
    Connection& operator=(const Connection&) = delete;

    // Executes a statement and returns all rows of the first result set (if any).
    std::vector<Row> query(const std::string& sql, std::vector<Param> params = {});
    // Executes a statement and returns the affected row count.
    std::int64_t execute(const std::string& sql, std::vector<Param> params = {});
    // Executes a (possibly multi-statement) batch without parameters.
    void executeBatch(const std::string& sql);

    void setAutoCommit(bool enabled);
    bool autoCommit() const { return autoCommit_; }
    void commit();
    void rollback();

    bool broken() const { return broken_; }
    void markBroken() { broken_ = true; }

    SQLHDBC handle() const { return dbc_; }

private:
    // Prepares, binds and executes; returns the statement handle (caller frees it).
    SQLHSTMT run(const std::string& sql, std::vector<Param>& params);
    [[noreturn]] void fail(SQLSMALLINT handleType, SQLHANDLE handle, const std::string& context);

    SQLHDBC dbc_ = SQL_NULL_HDBC;
    bool connected_ = false;
    bool autoCommit_ = true;
    bool broken_ = false;
};

// A minimal thread-safe connection pool for one database ("datasource").
class DataSource {
public:
    class Lease {
    public:
        Lease(DataSource* ds, std::unique_ptr<Connection> conn) : ds_(ds), conn_(std::move(conn)) {}
        Lease(Lease&& other) noexcept = default;
        Lease& operator=(Lease&&) = delete;
        Lease(const Lease&) = delete;
        ~Lease();

        Connection& operator*() { return *conn_; }
        Connection* operator->() { return conn_.get(); }

    private:
        DataSource* ds_;
        std::unique_ptr<Connection> conn_;
    };

    DataSource(std::shared_ptr<Environment> env, std::string connectionString, std::size_t maxIdle = 8);

    Lease acquire();

    // Runs fn inside a transaction on a pooled connection; commits on success,
    // rolls back on any exception.
    template <typename Fn>
    auto inTransaction(Fn&& fn) -> decltype(fn(std::declval<Connection&>())) {
        Lease lease = acquire();
        lease->setAutoCommit(false);
        try {
            if constexpr (std::is_void_v<decltype(fn(*lease))>) {
                fn(*lease);
                lease->commit();
                lease->setAutoCommit(true);
            } else {
                auto result = fn(*lease);
                lease->commit();
                lease->setAutoCommit(true);
                return result;
            }
        } catch (...) {
            try {
                lease->rollback();
                lease->setAutoCommit(true);
            } catch (...) {
                lease->markBroken();
            }
            throw;
        }
    }

private:
    void release(std::unique_ptr<Connection> conn);

    std::shared_ptr<Environment> env_;
    std::string connectionString_;
    std::size_t maxIdle_;
    std::mutex mutex_;
    std::vector<std::unique_ptr<Connection>> idle_;
};

// Escapes a value for use inside an ODBC connection string attribute ({...} form).
std::string escapeConnectionValue(const std::string& value);

}  // namespace shopservice::odbc
