#include "test_support.hpp"

#include "shopservice/config.hpp"

#include <gtest/gtest.h>
#include <httplib.h>

#include <memory>
#include <stdexcept>
#include <thread>

namespace shopservice::test {

namespace {

class ShopServiceEnvironment : public ::testing::Environment {
public:
    void SetUp() override {
        try {
            service_ = std::make_unique<ShopService>(DatabaseConfig::fromEnvironment());
        } catch (const std::exception& e) {
            FAIL() << "Unable to initialise ShopService against SQL Server: " << e.what();
        }
        server_ = std::make_unique<httplib::Server>();
        service_->registerRoutes(*server_);
        port_ = server_->bind_to_any_port("127.0.0.1");
        ASSERT_GT(port_, 0) << "Unable to bind the embedded HTTP server";
        thread_ = std::thread([this] { server_->listen_after_bind(); });
        server_->wait_until_ready();
    }

    void TearDown() override {
        if (server_) {
            server_->stop();
        }
        if (thread_.joinable()) {
            thread_.join();
        }
        server_.reset();
        service_.reset();
    }

    ShopService& service() { return *service_; }
    int port() const { return port_; }

private:
    std::unique_ptr<ShopService> service_;
    std::unique_ptr<httplib::Server> server_;
    std::thread thread_;
    int port_ = -1;
};

ShopServiceEnvironment* environment = nullptr;

httplib::Client client() {
    httplib::Client c("127.0.0.1", environment->port());
    c.set_read_timeout(30, 0);
    return c;
}

HttpResponse toResponse(const httplib::Result& result) {
    if (!result) {
        throw std::runtime_error("HTTP request failed: " + httplib::to_string(result.error()));
    }
    return HttpResponse{result->status, result->body};
}

}  // namespace

ShopService& shop() { return environment->service(); }

HttpResponse httpGet(const std::string& path) { return toResponse(client().Get(path)); }

HttpResponse httpPost(const std::string& path, const std::string& jsonBody) {
    return toResponse(client().Post(path, jsonBody, "application/json"));
}

HttpResponse httpPut(const std::string& path, const std::string& jsonBody) {
    return toResponse(client().Put(path, jsonBody, "application/json"));
}

HttpResponse httpDelete(const std::string& path) { return toResponse(client().Delete(path)); }

}  // namespace shopservice::test

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    // Ownership passes to googletest.
    shopservice::test::environment = static_cast<shopservice::test::ShopServiceEnvironment*>(
        ::testing::AddGlobalTestEnvironment(new shopservice::test::ShopServiceEnvironment()));
    return RUN_ALL_TESTS();
}
