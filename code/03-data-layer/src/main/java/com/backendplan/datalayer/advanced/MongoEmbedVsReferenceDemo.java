package com.backendplan.datalayer.advanced;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.List;

// NOT executed in this environment - no local MongoDB instance was available.
// Run against a real MongoDB (`brew install mongodb-community` and `brew services run mongodb-community`,
// or a Docker container) with: java -cp <classpath> com.backendplan.datalayer.advanced.MongoEmbedVsReferenceDemo
//
// Demonstrates the embed-vs-reference decision from the concept doc: an order's line items are
// embedded (always read/written together, bounded size) while the customer is referenced
// (independently large, updated separately, shared across many orders).
public class MongoEmbedVsReferenceDemo {

    public static void main(String[] args) {
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase db = client.getDatabase("backendplan_demo");

            MongoCollection<Document> customers = db.getCollection("customers");
            MongoCollection<Document> orders = db.getCollection("orders");

            customers.deleteMany(new Document());
            orders.deleteMany(new Document());

            Document customer = new Document("_id", "cust-1")
                    .append("name", "Uttam Kumar")
                    .append("email", "uttam@example.com");
            customers.insertOne(customer);

            // line items are embedded: always read/written with the order, bounded in count
            Document order = new Document("_id", "ord-1")
                    .append("customerId", "cust-1") // reference, not embed - customer is independent, reused, large
                    .append("status", "CREATED")
                    .append("lineItems", List.of(
                            new Document("sku", "sku-1").append("qty", 2).append("price", 19.99),
                            new Document("sku", "sku-2").append("qty", 1).append("price", 49.99)
                    ));
            orders.insertOne(order);

            System.out.println("--- Order document (line items embedded) ---");
            System.out.println(orders.find(new Document("_id", "ord-1")).first().toJson());

            System.out.println("--- To show the order with customer details, join at read time ---");
            Document foundOrder = orders.find(new Document("_id", "ord-1")).first();
            Document foundCustomer = customers.find(new Document("_id", foundOrder.getString("customerId"))).first();
            System.out.println("Order " + foundOrder.getString("_id") + " belongs to " + foundCustomer.getString("name"));
        }
    }
}
