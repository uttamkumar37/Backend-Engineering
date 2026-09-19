package com.backendplan.springai.intermediate;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

// Asking for JSON directly and parsing it into a real Java type is far more robust than parsing
// free text with regex - the test can assert on FIELDS (structural properties), not exact strings.
public class StructuredOutputDemo {

    record OrderClassification(String category, boolean urgent) {}

    public static void main(String[] args) {
        OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");
        OllamaChatModel model = new OllamaChatModel(ollamaApi,
                OllamaOptions.create().withModel("llama3.2:1b").withTemperature(0.0f));
        ChatClient chatClient = ChatClient.create(model);

        String customerMessage = "My order never arrived and I need it TODAY for a wedding!";

        OrderClassification result = chatClient.prompt()
                .user(u -> u.text("""
                        Classify this customer message into a category (one of: SHIPPING, BILLING, PRODUCT_QUALITY, OTHER)
                        and whether it's urgent (true/false).
                        Message: {message}
                        """)
                        .param("message", customerMessage))
                .call()
                .entity(OrderClassification.class);

        System.out.println("Message: " + customerMessage);
        System.out.println("Parsed into a real Java record: " + result);
        System.out.println("category = " + result.category() + ", urgent = " + result.urgent());
        System.out.println("A test can now assert on result.urgent() being true - a structural");
        System.out.println("property - instead of doing brittle string matching on free text.");
    }
}
