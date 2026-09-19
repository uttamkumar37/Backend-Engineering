package com.backendplan.springai.advanced;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.util.Comparator;
import java.util.List;

// A full, real RAG pipeline: embed a small knowledge base, embed the query, retrieve the closest
// chunk by cosine similarity, then ground the generation in ONLY that retrieved context. Proves
// the concept doc's point that retrieval quality - not the generation model - is what determines
// whether the final answer is actually correct.
public class RagDemo {

    // a deliberately small, real "knowledge base" - private facts the model was NOT trained on
    static final List<String> KNOWLEDGE_BASE = List.of(
            "The backendplan-orders service uses PostgreSQL and enforces idempotency via a unique constraint on the payment_key column.",
            "The backendplan-notifications service publishes to Kafka topic 'notifications-outbound' with 6 partitions.",
            "On-call rotation for the platform team is handled through the internal tool PagerLoop, not PagerDuty.",
            "The staging environment's database connection pool is capped at 20 connections via HikariCP."
    );

    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static void main(String[] args) {
        OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");
        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(ollamaApi,
                OllamaOptions.create().withModel("nomic-embed-text"));

        // embed every chunk in the knowledge base once
        record EmbeddedChunk(String text, float[] embedding) {}
        List<EmbeddedChunk> embeddedChunks = KNOWLEDGE_BASE.stream()
                .map(text -> new EmbeddedChunk(text, embeddingModel.embed(new Document(text))))
                .toList();

        String question = "What database connection pool size is used in staging?";
        float[] questionEmbedding = embeddingModel.embed(new Document(question));

        EmbeddedChunk bestMatch = embeddedChunks.stream()
                .max(Comparator.comparingDouble(c -> cosineSimilarity(c.embedding(), questionEmbedding)))
                .orElseThrow();

        System.out.println("Question: " + question);
        System.out.println();
        System.out.println("--- Retrieval step: closest chunk by cosine similarity ---");
        for (EmbeddedChunk chunk : embeddedChunks) {
            double sim = cosineSimilarity(chunk.embedding(), questionEmbedding);
            System.out.printf("  sim=%.4f  %s%n", sim, chunk.text());
        }
        System.out.println();
        System.out.println("Retrieved (most similar): " + bestMatch.text());

        OllamaChatModel chatModel = new OllamaChatModel(ollamaApi,
                OllamaOptions.create().withModel("llama3.2:1b").withTemperature(0.0f));
        ChatClient chatClient = ChatClient.create(chatModel);

        String answer = chatClient.prompt()
                .user(u -> u.text("""
                        Answer the question using ONLY the context below. If the context doesn't
                        contain the answer, say so - do not guess.

                        Context: {context}

                        Question: {question}
                        """)
                        .param("context", bestMatch.text())
                        .param("question", question))
                .call()
                .content();

        System.out.println();
        System.out.println("--- Generation step: grounded ONLY in the retrieved chunk ---");
        System.out.println(answer);
        System.out.println();
        System.out.println("The model was never trained on this fact - it could only answer");
        System.out.println("correctly because retrieval found the right chunk to ground it in.");
    }
}
