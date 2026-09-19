package com.backendplan.testing.advanced;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// The CONSUMER defines the contract it needs, against a mock of the provider - no real provider
// running here at all. This generates a pact file (target/pacts/orders-consumer-orders-provider.json)
// that the provider will later verify itself against, in its own build, with no consumer running.
@ExtendWith(PactConsumerTestExt.class)
class OrdersApiConsumerPactTest {

    @Pact(consumer = "orders-consumer", provider = "orders-provider")
    V4Pact ordersPact(PactBuilder builder) {
        return builder
                .given("order ord-1 exists")
                .expectsToReceiveHttpInteraction("a request for order ord-1", http -> http
                        .withRequest(req -> req.method("GET").path("/orders/ord-1"))
                        .willRespondWith(res -> res
                                .status(200)
                                .header("Content-Type", "application/json")
                                .body("{\"id\": \"ord-1\", \"status\": \"CONFIRMED\"}")))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "ordersPact")
    void consumerCanParseTheProvidersResponse(MockServer mockServer) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            String response = client.execute(new HttpGet(mockServer.getUrl() + "/orders/ord-1"),
                    r -> new String(r.getEntity().getContent().readAllBytes()));
            org.junit.jupiter.api.Assertions.assertEquals(
                    "{\"id\": \"ord-1\", \"status\": \"CONFIRMED\"}", response);
        }
    }
}
