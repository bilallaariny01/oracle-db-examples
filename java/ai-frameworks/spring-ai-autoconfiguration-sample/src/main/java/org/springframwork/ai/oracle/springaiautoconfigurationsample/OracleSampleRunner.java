/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package org.springframwork.ai.oracle.springaiautoconfigurationsample;

import java.util.List;
import java.util.Scanner;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.oracle.chunking.OracleDocumentSplitter;
import org.springframework.ai.oracle.loader.OracleDocumentReader;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class OracleSampleRunner implements CommandLineRunner {

    private final OracleDocumentReader documentReader;

    private final OracleDocumentSplitter documentSplitter;

    private final ChatClient assistant;

    private final VectorStore vectorStore;

    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    private final OracleSampleProperties properties;

    private final Environment environment;

    OracleSampleRunner(OracleDocumentReader documentReader, OracleDocumentSplitter documentSplitter,
            ChatClient.Builder chatClientBuilder, VectorStore vectorStore,
            RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, OracleSampleProperties properties,
            Environment environment) {
        this.documentReader = documentReader;
        this.documentSplitter = documentSplitter;
        this.assistant = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        int addBatchSize = this.properties.getVectorStoreAddBatchSize();
        if (addBatchSize <= 0) {
            throw new IllegalStateException("sample.oracle.vector-store-add-batch-size must be greater than 0.");
        }

        List<Document> documents = this.documentReader.get();
        List<Document> chunks = this.documentSplitter.split(documents);
        addInBatches(chunks, addBatchSize);

        printStartupSummary(documents.size(), chunks.size(), addBatchSize);
        runChatLoop();
    }

    private void runChatLoop() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                if (!scanner.hasNextLine()) {
                    return;
                }

                String input = scanner.nextLine().trim();
                if (!StringUtils.hasText(input)) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                    System.out.println("Bye.");
                    return;
                }

                try {
                    String answer = generateAnswer(input);
                    if (!StringUtils.hasText(answer)) {
                        throw new IllegalStateException(
                                "Ollama returned an empty response. Disable thinking or increase sample.oracle.ollama-chat-max-tokens.");
                    }
                    System.out.printf("Assistant: %s%n", answer);
                    printRetrievedMatches(input);
                }
                catch (RuntimeException ex) {
                    System.out.printf("Assistant request failed: %s%n", ex.getMessage());
                    System.out.println(
                            "Check Oracle, Ollama availability, model selection, and thinking/max-token settings, then try again.");
                }
            }
        }
    }

    private String generateAnswer(String input) {
        if (hasRelevantDocumentContext(input)) {
            return this.assistant.prompt()
                    .advisors(this.retrievalAugmentationAdvisor)
                    .system("""
                            You are a helpful assistant.

                            If the user asks about Oracle sample code, embeddings, vector stores, chunking,
                            database tables, session persistence, autoconfiguration, or loaded documents,
                            use the retrieved context.

                            If a document-specific answer cannot be grounded in the retrieved context,
                            say that the Oracle vector store did not return enough context.
                            """)
                    .user(input)
                    .advisors(advisor -> addSessionContext(advisor))
                    .call()
                    .content();
        }

        return this.assistant.prompt()
                .system("""
                        You are a friendly assistant.
                        For normal conversation, answer naturally and briefly.
                        Do not refuse harmless requests.
                        """)
                .user(input)
                .advisors(advisor -> addSessionContext(advisor))
                .call()
                .content();
    }

    private void addSessionContext(ChatClient.AdvisorSpec advisor) {
        advisor.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.properties.getSessionId())
                .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, this.properties.getUserId());
    }

    private boolean hasRelevantDocumentContext(String input) {
        List<Document> matches = this.vectorStore.similaritySearch(SearchRequest.builder()
                .query(input)
                .topK(this.properties.getRetrievalTopK())
                .similarityThreshold(this.properties.getRetrievalSimilarityThreshold())
                .build());
        return !matches.isEmpty();
    }

    private void printRetrievedMatches(String input) {
        List<Document> matches = this.vectorStore.similaritySearch(SearchRequest.builder()
                .query(input)
                .topK(this.properties.getRetrievalTopK())
                .similarityThresholdAll()
                .build());
        for (Document match : matches) {
            System.out.printf("score=%s text=%s%n", match.getScore(), safeText(match));
        }
    }

    private void addInBatches(List<Document> documents, int batchSize) {
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            this.vectorStore.add(documents.subList(start, end));
        }
    }

    private void printStartupSummary(int sourceCount, int chunkCount, int addBatchSize) {
        System.out.printf("Loaded %d documents as %d chunks into %s.%n", sourceCount, chunkCount,
                this.properties.getVectorTableName());
        System.out.printf("Chunking setup: by=%s, max=%s, overlap=%s, split=%s%n",
                property("spring.ai.oracle.document-splitter.preferences.by"),
                property("spring.ai.oracle.document-splitter.preferences.max"),
                property("spring.ai.oracle.document-splitter.preferences.overlap"),
                property("spring.ai.oracle.document-splitter.preferences.split"));
        System.out.printf("Vector-store add batch size: %d%n", addBatchSize);
        System.out.printf("Embedding batching enabled: %s%n",
                property("spring.ai.oracle.embedding.options.batching"));
        System.out.printf("ONNX load on startup enabled: %s%n",
                property("spring.ai.oracle.embedding.initialize-on-startup"));
        System.out.printf("Chat started with Ollama model %s.%n", this.properties.getOllamaChatModel());
        System.out.printf("Ollama thinking enabled: %s%n", this.properties.isOllamaChatThinkingEnabled());
        System.out.printf("Ollama max tokens: %s%n", this.properties.getOllamaChatMaxTokens());
        System.out.printf("Source document resource location: %s%n", this.properties.getSourceDocumentResource());
        System.out.printf("Oracle session id: %s%n", this.properties.getSessionId());
        System.out.println("Type 'exit' or 'quit' to stop.");
    }

    private String property(String name) {
        return this.environment.getProperty(name, "");
    }

    private static String safeText(Document document) {
        return document.getText() != null ? document.getText() : document.getFormattedContent();
    }

}
