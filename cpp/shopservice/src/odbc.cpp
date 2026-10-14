#include "shopservice/odbc.hpp"

#include <algorithm>

namespace shopservice::odbc {

namespace {

struct Diagnostic {
    std::string sqlState;
    std::string message;
};

Diagnostic readDiagnostics(SQLSMALLINT handleType, SQLHANDLE handle) {
    Diagnostic diag;
    SQLCHAR state[SQL_SQLSTATE_SIZE + 1];
    SQLCHAR text[SQL_MAX_MESSAGE_LENGTH];
    SQLINTEGER nativeError = 0;
    SQLSMALLINT textLength = 0;
    for (SQLSMALLINT i = 1;; ++i) {
        SQLRETURN rc = SQLGetDiagRec(handleType, handle, i, state, &nativeError, text,
                                     static_cast<SQLSMALLINT>(sizeof(text)), &textLength);
        if (!SQL_SUCCEEDED(rc)) {
            break;
        }
        if (diag.sqlState.empty()) {
            diag.sqlState = reinterpret_cast<const char*>(state);
        }
        if (!diag.message.empty()) {
            diag.message += "; ";
        }
        diag.message += "[" + std::string(reinterpret_cast<const char*>(state)) + "] " +
                        std::string(reinterpret_cast<const char*>(text));
    }
    if (diag.message.empty()) {
        diag.message = "unknown ODBC error";
    }
    return diag;
}

std::optional<std::string> readColumn(SQLHSTMT stmt, SQLUSMALLINT column, Connection& conn) {
    std::string value;
    char buffer[1024];
    for (;;) {
        SQLLEN indicator = 0;
        SQLRETURN rc = SQLGetData(stmt, column, SQL_C_CHAR, buffer, sizeof(buffer), &indicator);
        if (rc == SQL_NO_DATA) {
            break;
        }
        if (!SQL_SUCCEEDED(rc)) {
            Diagnostic diag = readDiagnostics(SQL_HANDLE_STMT, stmt);
            if (diag.sqlState.rfind("08", 0) == 0) {
                conn.markBroken();
            }
            throw OdbcError("SQLGetData failed: " + diag.message, diag.sqlState);
        }
        if (indicator == SQL_NULL_DATA) {
            return std::nullopt;
        }
        if (indicator != SQL_NO_TOTAL && indicator < static_cast<SQLLEN>(sizeof(buffer))) {
            value.append(buffer, static_cast<std::size_t>(indicator));
            break;
        }
        // Data truncated: the buffer is full (minus the null terminator); keep reading.
        value.append(buffer, sizeof(buffer) - 1);
    }
    return value;
}

// Frees a statement handle when it goes out of scope.
class StatementGuard {
public:
    explicit StatementGuard(SQLHSTMT stmt) : stmt_(stmt) {}
    ~StatementGuard() {
        if (stmt_ != SQL_NULL_HSTMT) {
            SQLFreeHandle(SQL_HANDLE_STMT, stmt_);
        }
    }
    StatementGuard(const StatementGuard&) = delete;
    StatementGuard& operator=(const StatementGuard&) = delete;

    SQLHSTMT get() const { return stmt_; }
    SQLHSTMT release() {
        SQLHSTMT s = stmt_;
        stmt_ = SQL_NULL_HSTMT;
        return s;
    }

private:
    SQLHSTMT stmt_;
};

}  // namespace

std::string escapeConnectionValue(const std::string& value) {
    std::string escaped = "{";
    for (char c : value) {
        escaped += c;
        if (c == '}') {
            escaped += '}';
        }
    }
    escaped += "}";
    return escaped;
}

// ── Environment ─────────────────────────────────────────────────────────────

Environment::Environment() {
    if (!SQL_SUCCEEDED(SQLAllocHandle(SQL_HANDLE_ENV, SQL_NULL_HANDLE, &env_))) {
        throw OdbcError("Unable to allocate ODBC environment handle", "HY001");
    }
    SQLRETURN rc = SQLSetEnvAttr(env_, SQL_ATTR_ODBC_VERSION,
                                 reinterpret_cast<SQLPOINTER>(SQL_OV_ODBC3), 0);
    if (!SQL_SUCCEEDED(rc)) {
        Diagnostic diag = readDiagnostics(SQL_HANDLE_ENV, env_);
        SQLFreeHandle(SQL_HANDLE_ENV, env_);
        throw OdbcError("Unable to select ODBC 3 behaviour: " + diag.message, diag.sqlState);
    }
}

Environment::~Environment() {
    if (env_ != SQL_NULL_HENV) {
        SQLFreeHandle(SQL_HANDLE_ENV, env_);
    }
}

// ── Connection ──────────────────────────────────────────────────────────────

Connection::Connection(const Environment& env, const std::string& connectionString) {
    if (!SQL_SUCCEEDED(SQLAllocHandle(SQL_HANDLE_DBC, env.handle(), &dbc_))) {
        throw OdbcError("Unable to allocate ODBC connection handle", "HY001");
    }
    SQLSetConnectAttr(dbc_, SQL_ATTR_LOGIN_TIMEOUT, reinterpret_cast<SQLPOINTER>(15), 0);

    SQLCHAR out[1024];
    SQLSMALLINT outLength = 0;
    SQLRETURN rc = SQLDriverConnect(
        dbc_, nullptr,
        reinterpret_cast<SQLCHAR*>(const_cast<char*>(connectionString.c_str())), SQL_NTS,
        out, static_cast<SQLSMALLINT>(sizeof(out)), &outLength, SQL_DRIVER_NOPROMPT);
    if (!SQL_SUCCEEDED(rc)) {
        Diagnostic diag = readDiagnostics(SQL_HANDLE_DBC, dbc_);
        SQLFreeHandle(SQL_HANDLE_DBC, dbc_);
        dbc_ = SQL_NULL_HDBC;
        // Never include the connection string in the message: it contains credentials.
        throw OdbcError("Unable to connect: " + diag.message, diag.sqlState);
    }
    connected_ = true;
}

Connection::~Connection() {
    if (dbc_ != SQL_NULL_HDBC) {
        if (connected_) {
            if (!autoCommit_) {
                SQLEndTran(SQL_HANDLE_DBC, dbc_, SQL_ROLLBACK);
            }
            SQLDisconnect(dbc_);
        }
        SQLFreeHandle(SQL_HANDLE_DBC, dbc_);
    }
}

void Connection::fail(SQLSMALLINT handleType, SQLHANDLE handle, const std::string& context) {
    Diagnostic diag = readDiagnostics(handleType, handle);
    if (diag.sqlState.rfind("08", 0) == 0) {
        broken_ = true;
    }
    throw OdbcError(context + ": " + diag.message, diag.sqlState);
}

SQLHSTMT Connection::run(const std::string& sql, std::vector<Param>& params) {
    SQLHSTMT stmt = SQL_NULL_HSTMT;
    if (!SQL_SUCCEEDED(SQLAllocHandle(SQL_HANDLE_STMT, dbc_, &stmt))) {
        fail(SQL_HANDLE_DBC, dbc_, "SQLAllocHandle(STMT) failed");
    }
    StatementGuard guard(stmt);
    SQLRETURN rc = SQLPrepare(stmt, reinterpret_cast<SQLCHAR*>(const_cast<char*>(sql.c_str())), SQL_NTS);
    if (!SQL_SUCCEEDED(rc)) {
        fail(SQL_HANDLE_STMT, stmt, "SQLPrepare failed");
    }

    for (std::size_t i = 0; i < params.size(); ++i) {
        Param& p = params[i];
        const auto number = static_cast<SQLUSMALLINT>(i + 1);
        switch (p.kind_) {
            case Param::Kind::Null:
                p.indicator_ = SQL_NULL_DATA;
                rc = SQLBindParameter(stmt, number, SQL_PARAM_INPUT, SQL_C_CHAR, SQL_VARCHAR, 1, 0,
                                      nullptr, 0, &p.indicator_);
                break;
            case Param::Kind::Int64:
                p.indicator_ = 0;
                rc = SQLBindParameter(stmt, number, SQL_PARAM_INPUT, SQL_C_SBIGINT, SQL_BIGINT, 0, 0,
                                      &p.i64_, 0, &p.indicator_);
                break;
            case Param::Kind::Int32:
                p.indicator_ = 0;
                rc = SQLBindParameter(stmt, number, SQL_PARAM_INPUT, SQL_C_SLONG, SQL_INTEGER, 0, 0,
                                      &p.i32_, 0, &p.indicator_);
                break;
            case Param::Kind::String: {
                // Client-side text is passed as SQL_C_CHAR and bound as a Unicode SQL type
                // so the driver converts it to UTF-16 for NVARCHAR columns.
                p.indicator_ = static_cast<SQLLEN>(p.str_.size());
                const SQLULEN size = std::max<SQLULEN>(p.str_.size(), 1);
                const SQLSMALLINT sqlType = size > 4000 ? SQL_WLONGVARCHAR : SQL_WVARCHAR;
                rc = SQLBindParameter(stmt, number, SQL_PARAM_INPUT, SQL_C_CHAR, sqlType, size, 0,
                                      const_cast<char*>(p.str_.data()),
                                      static_cast<SQLLEN>(p.str_.size()), &p.indicator_);
                break;
            }
            case Param::Kind::Decimal:
                p.indicator_ = static_cast<SQLLEN>(p.str_.size());
                rc = SQLBindParameter(stmt, number, SQL_PARAM_INPUT, SQL_C_CHAR, SQL_DECIMAL,
                                      p.precision_, p.scale_, const_cast<char*>(p.str_.data()),
                                      static_cast<SQLLEN>(p.str_.size()), &p.indicator_);
                break;
        }
        if (!SQL_SUCCEEDED(rc)) {
            fail(SQL_HANDLE_STMT, stmt, "SQLBindParameter failed");
        }
    }

    rc = SQLExecute(stmt);
    if (rc != SQL_NO_DATA && !SQL_SUCCEEDED(rc)) {
        fail(SQL_HANDLE_STMT, stmt, "SQLExecute failed");
    }
    return guard.release();
}

std::vector<Row> Connection::query(const std::string& sql, std::vector<Param> params) {
    StatementGuard guard(run(sql, params));
    SQLHSTMT stmt = guard.get();
    std::vector<Row> rows;
    SQLSMALLINT columns = 0;
    if (!SQL_SUCCEEDED(SQLNumResultCols(stmt, &columns))) {
        fail(SQL_HANDLE_STMT, stmt, "SQLNumResultCols failed");
    }
    if (columns > 0) {
        for (;;) {
            SQLRETURN rc = SQLFetch(stmt);
            if (rc == SQL_NO_DATA) {
                break;
            }
            if (!SQL_SUCCEEDED(rc)) {
                fail(SQL_HANDLE_STMT, stmt, "SQLFetch failed");
            }
            Row row;
            row.reserve(static_cast<std::size_t>(columns));
            for (SQLSMALLINT c = 1; c <= columns; ++c) {
                row.push_back(readColumn(stmt, static_cast<SQLUSMALLINT>(c), *this));
            }
            rows.push_back(std::move(row));
        }
    }
    return rows;
}

std::int64_t Connection::execute(const std::string& sql, std::vector<Param> params) {
    StatementGuard guard(run(sql, params));
    SQLLEN count = 0;
    if (!SQL_SUCCEEDED(SQLRowCount(guard.get(), &count))) {
        fail(SQL_HANDLE_STMT, guard.get(), "SQLRowCount failed");
    }
    return static_cast<std::int64_t>(count);
}

void Connection::executeBatch(const std::string& sql) {
    SQLHSTMT stmt = SQL_NULL_HSTMT;
    if (!SQL_SUCCEEDED(SQLAllocHandle(SQL_HANDLE_STMT, dbc_, &stmt))) {
        fail(SQL_HANDLE_DBC, dbc_, "SQLAllocHandle(STMT) failed");
    }
    StatementGuard guard(stmt);
    SQLRETURN rc = SQLExecDirect(stmt, reinterpret_cast<SQLCHAR*>(const_cast<char*>(sql.c_str())), SQL_NTS);
    if (rc != SQL_NO_DATA && !SQL_SUCCEEDED(rc)) {
        fail(SQL_HANDLE_STMT, stmt, "SQLExecDirect failed");
    }
    // Drain every result so errors raised by later statements in the batch surface.
    for (;;) {
        rc = SQLMoreResults(stmt);
        if (rc == SQL_NO_DATA) {
            break;
        }
        if (!SQL_SUCCEEDED(rc)) {
            fail(SQL_HANDLE_STMT, stmt, "SQLMoreResults failed");
        }
    }
}

void Connection::setAutoCommit(bool enabled) {
    SQLRETURN rc = SQLSetConnectAttr(
        dbc_, SQL_ATTR_AUTOCOMMIT,
        reinterpret_cast<SQLPOINTER>(static_cast<SQLULEN>(enabled ? SQL_AUTOCOMMIT_ON : SQL_AUTOCOMMIT_OFF)),
        SQL_IS_UINTEGER);
    if (!SQL_SUCCEEDED(rc)) {
        fail(SQL_HANDLE_DBC, dbc_, "Unable to change auto-commit mode");
    }
    autoCommit_ = enabled;
}

void Connection::commit() {
    if (!SQL_SUCCEEDED(SQLEndTran(SQL_HANDLE_DBC, dbc_, SQL_COMMIT))) {
        fail(SQL_HANDLE_DBC, dbc_, "Commit failed");
    }
}

void Connection::rollback() {
    if (!SQL_SUCCEEDED(SQLEndTran(SQL_HANDLE_DBC, dbc_, SQL_ROLLBACK))) {
        fail(SQL_HANDLE_DBC, dbc_, "Rollback failed");
    }
}

// ── DataSource ──────────────────────────────────────────────────────────────

DataSource::Lease::~Lease() {
    if (ds_ != nullptr && conn_) {
        ds_->release(std::move(conn_));
    }
}

DataSource::DataSource(std::shared_ptr<Environment> env, std::string connectionString, std::size_t maxIdle)
    : env_(std::move(env)), connectionString_(std::move(connectionString)), maxIdle_(maxIdle) {}

DataSource::Lease DataSource::acquire() {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!idle_.empty()) {
            std::unique_ptr<Connection> conn = std::move(idle_.back());
            idle_.pop_back();
            return Lease(this, std::move(conn));
        }
    }
    return Lease(this, std::make_unique<Connection>(*env_, connectionString_));
}

void DataSource::release(std::unique_ptr<Connection> conn) {
    if (!conn->broken() && !conn->autoCommit()) {
        try {
            conn->rollback();
            conn->setAutoCommit(true);
        } catch (...) {
            conn->markBroken();
        }
    }
    if (conn->broken()) {
        return;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (idle_.size() < maxIdle_) {
        idle_.push_back(std::move(conn));
    }
}

}  // namespace shopservice::odbc
