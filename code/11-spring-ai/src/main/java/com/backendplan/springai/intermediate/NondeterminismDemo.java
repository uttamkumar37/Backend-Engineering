package com.backendplan.springai.intermediate;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

// The same prompt, same model, called 3 times with temperature > 0, produces DIFFERENT outputs.
// This is why exact-string-match assertions are the wrong default for testing LLM-backed code -
// you assert on structural/semantic properties instead (see StructuredOutputDemo).
public class NondeterminismDemo {

    public static void main(String[] args) {
        OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");

        System.out.println("--- temperature=1.0 (default-ish randomness): 3 calls, same prompt ---");
        OllamaChatModel randomModel = new OllamaChatModel(ollamaApi,
                OllamaOptions.create().withModel("llama3.2:1b").withTemperature(1.0f));
        ChatClient randomClient = ChatClient.create(randomModel);
        for (int i = 1; i <= 3; i++) {
            String response = randomClient.prompt()
                    .user("Name one animal. Respond with just the animal's name, nothing else.")
                    .call()
                    .content();
            System.out.println("  call " + i + ": " + response.trim());
        }

        System.out.println();
        System.out.println("--- temperature=0.0 (as deterministic as this model gets): 3 calls, same prompt ---");
        OllamaChatModel deterministicModel = new OllamaChatModel(ollamaApi,
                OllamaOptions.create().withModel("llama3.2:1b").withTemperature(0.0f));
        ChatClient deterministicClient = ChatClient.create(deterministicModel);
        for (int i = 1; i <= 3; i++) {
            String response = deterministicClient.prompt()
                    .user("Name one animal. Respond with just the animal's name, nothing else.")
                    .call()
                    .content();
            System.out.println("  call " + i + ": " + response.trim());
        }
    }
}
