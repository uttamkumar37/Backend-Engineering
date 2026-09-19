package com.backendplan.testing.advanced;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

// The PROVIDER, in its own build, replays every consumer's recorded contract against its REAL
// implementation - no consumer running, just the pact file the consumer test generated. This is
// what catches a breaking change at the provider's build time, before a deploy.
@Provider("orders-provider")
@PactFolder("target/pacts")
class OrdersApiProviderVerificationTest {

    static HttpServer server;

    @BeforeAll
    static void startRealProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress(8091), 0);
        server.createContext("/orders/ord-1", exchange -> {
            byte[] body = "{\"id\": \"ord-1\", \"status\": \"CONFIRMED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopRealProvider() {
        server.stop(0);
    }

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", 8091));
    }

    @State("order ord-1 exists")
    void orderExists() {
        // no setup needed - the real provider always serves ord-1 in this demo
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
