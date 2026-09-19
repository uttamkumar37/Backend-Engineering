package com.backendplan.springai.beginner;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

// The smallest useful Spring AI call: a ChatClient wrapping a model (here, a REAL local Ollama
// model - no API key, no network call to a hosted provider). Same ChatClient API works
// unchanged against OpenAI/Anthropic/etc - that portability is Spring AI's actual value.
public class ChatClientBasicsDemo {

    public static void main(String[] args) {
        OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");
        OllamaChatModel model = new OllamaChatModel(ollamaApi,
                OllamaOptions.create().withModel("llama3.2:1b"));

        ChatClient chatClient = ChatClient.create(model);

        String response = chatClient.prompt()
                .user("In one short sentence, what is a circuit breaker in software?")
                .call()
                .content();

        System.out.println("Model response:");
        System.out.println(response);
    }
}
