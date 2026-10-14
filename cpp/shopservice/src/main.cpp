#include "shopservice/app.hpp"
#include "shopservice/config.hpp"

#include <httplib.h>

#include <csignal>
#include <iostream>

namespace {

httplib::Server* runningServer = nullptr;

void handleSignal(int) {
    if (runningServer != nullptr) {
        runningServer->stop();
    }
}

}  // namespace

int main() {
    try {
        const auto config = shopservice::DatabaseConfig::fromEnvironment();
        const std::string bindHost = shopservice::envOr("BIND_HOST", "0.0.0.0");
        const int port = std::stoi(shopservice::envOr("PORT", "8080"));

        std::cout << "Connecting to SQL Server " << config.host << ':' << config.port << " via ODBC driver '"
                  << config.driver << "'..." << std::endl;
        shopservice::ShopService service(config);

        httplib::Server server;
        service.registerRoutes(server);

        runningServer = &server;
        std::signal(SIGINT, handleSignal);
        std::signal(SIGTERM, handleSignal);

        std::cout << "ShopService listening on " << bindHost << ':' << port << std::endl;
        if (!server.listen(bindHost, port)) {
            std::cerr << "Failed to listen on " << bindHost << ':' << port << std::endl;
            return 1;
        }
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "Failed to start application: " << e.what() << std::endl;
        return 1;
    }
}
