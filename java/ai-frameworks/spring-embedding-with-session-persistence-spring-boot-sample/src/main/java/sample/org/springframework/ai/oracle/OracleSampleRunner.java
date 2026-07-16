/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package sample.org.springframework.ai.oracle;

import java.util.List;
import java.util.Scanner;

import javax.sql.DataSource;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import org.springframework.ai.oracle.chunking.OracleChunkingPreferences;
import org.springframework.ai.oracle.chunking.OracleDocumentSplitter;
import org.springframework.ai.oracle.loader.OracleDocumentPreferences;
import org.springframework.ai.oracle.loader.OracleDocumentReader;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class OracleSampleRunner implements CommandLineRunner {

    private static final double RAG_ROUTING_THRESHOLD = 0.2;

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;
    private final ChatClient assistant;
    private final OracleVectorStore vectorStore;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    OracleSampleRunner(DataSource dataSource, ResourceLoader resourceLoader, ChatClient assistant,
            OracleVectorStore vectorStore, RetrievalAugmentationAdvisor retrievalAugmentationAdvisor) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.assistant = assistant;
        this.vectorStore = vectorStore;
        this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;
    }

    @Override
    public void run(String... args) {
        String sessionId = OracleSampleConfiguration.env("ORACLE_SAMPLE_SESSION_ID",
                OracleSampleConfiguration.DEFAULT_SESSION_ID);
        int addBatchSize = OracleSampleConfiguration.envInt("ORACLE_VECTORSTORE_ADD_BATCH_SIZE",
                OracleSampleConfiguration.DEFAULT_ADD_BATCH_SIZE);

        List<Document> documents = loadSourceDocuments();
        List<Document> chunks = buildDocumentSplitter().split(documents);
        addInBatches(chunks, addBatchSize);

        printStartupSummary(documents.size(), chunks.size(), addBatchSize, sessionId);
        runChatLoop(sessionId);
    }

    private void runChatLoop(String sessionId) {
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
                    String answer = generateAnswer(sessionId, input);
                    System.out.printf("Assistant: %s%n", answer);
                    List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                            .query(input)
                            .topK(3)
                            .similarityThresholdAll()
                            .build());
                    for (Document match : matches) {
                        System.out.printf("score=%s text=%s%n", match.getScore(), match.getText());
                    }
                }
                catch (RuntimeException ex) {
                    System.out.printf("Assistant request failed: %s%n", ex.getMessage());
                    System.out.println("Check Ollama availability and model selection, then try again.");
                }
            }
        }
    }

    private String generateAnswer(String sessionId, String input) {
        if (hasRelevantDocumentContext(input)) {
            return assistant.prompt()
                    .advisors(retrievalAugmentationAdvisor)
                    .system("""
                            You are a helpful assistant.

                            If the user asks about Oracle sample code, embeddings, vector stores, chunking,
                            database tables, or loaded documents, use the retrieved context.

                            If a document-specific answer cannot be grounded in the retrieved context,
                            say that the Oracle vector store did not return enough context.
                            """)
                    .user(input)
                    .advisors(advisor -> advisor
                            .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId)
                            .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, OracleSampleConfiguration.DEFAULT_USER_ID))
                    .call()
                    .content();
        }

        return assistant.prompt()
                .system("""
                        You are a friendly assistant.
                        For normal conversation, answer naturally and briefly.
                        Do not refuse harmless requests.
                        """)
                .user(input)
                .advisors(advisor -> advisor
                        .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId)
                        .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, OracleSampleConfiguration.DEFAULT_USER_ID))
                .call()
                .content();
    }

    private boolean hasRelevantDocumentContext(String input) {
        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(input)
                .topK(3)
                .similarityThreshold(RAG_ROUTING_THRESHOLD)
                .build());
        return !matches.isEmpty();
    }

    private List<Document> loadSourceDocuments() {
        String resourceLocation = OracleSampleConfiguration.env("ORACLE_SOURCE_DOCUMENT_RESOURCE",
                OracleSampleConfiguration.DEFAULT_SOURCE_RESOURCE);
        Resource resource = this.resourceLoader.getResource(resourceLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Resource not found: " + resourceLocation);
        }

        OracleDocumentReader reader = OracleDocumentReader.builder(dataSource)
                .resource(resource)
                .preferences(OracleDocumentPreferences.builder().format("TEXT").build())
                .build();
        return reader.get();
    }

    private OracleDocumentSplitter buildDocumentSplitter() {
        OracleChunkingPreferences options = OracleChunkingPreferences.builder()
                .by(OracleSampleConfiguration.env("ORACLE_CHUNK_BY", OracleSampleConfiguration.DEFAULT_CHUNK_BY))
                .max(OracleSampleConfiguration.envInt("ORACLE_CHUNK_MAX", OracleSampleConfiguration.DEFAULT_CHUNK_MAX))
                .overlap(OracleSampleConfiguration.envInt("ORACLE_CHUNK_OVERLAP",
                        OracleSampleConfiguration.DEFAULT_CHUNK_OVERLAP))
                .split(OracleSampleConfiguration.env("ORACLE_CHUNK_SPLIT", OracleSampleConfiguration.DEFAULT_CHUNK_SPLIT))
                .language("american")
                .normalize("all")
                .extended(true)
                .build();

        return OracleDocumentSplitter.builder(dataSource).preferences(options).build();
    }

    private void addInBatches(List<Document> documents, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalStateException("ORACLE_VECTORSTORE_ADD_BATCH_SIZE must be greater than 0.");
        }
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }

    private void printStartupSummary(int sourceCount, int chunkCount, int addBatchSize, String sessionId) {
        System.out.printf("Loaded %d documents as %d chunks into %s.%n", sourceCount, chunkCount,
                OracleSampleConfiguration.VECTOR_TABLE_NAME);
        System.out.printf(
                "Chunking setup: by=%s, max=%d, overlap=%d, split=%s%n",
                OracleSampleConfiguration.env("ORACLE_CHUNK_BY", OracleSampleConfiguration.DEFAULT_CHUNK_BY),
                OracleSampleConfiguration.envInt("ORACLE_CHUNK_MAX", OracleSampleConfiguration.DEFAULT_CHUNK_MAX),
                OracleSampleConfiguration.envInt("ORACLE_CHUNK_OVERLAP", OracleSampleConfiguration.DEFAULT_CHUNK_OVERLAP),
                OracleSampleConfiguration.env("ORACLE_CHUNK_SPLIT", OracleSampleConfiguration.DEFAULT_CHUNK_SPLIT));
        System.out.printf("Vector-store add batch size: %d%n", addBatchSize);
        System.out.printf("Embedding batching enabled: %s%n",
                OracleSampleConfiguration.envBoolean("ORACLE_EMBEDDING_BATCHING", false));
        System.out.printf("ONNX load on startup enabled: %s%n", true);
        System.out.printf("Chat started with Ollama model %s.%n",
                OracleSampleConfiguration.env("OLLAMA_CHAT_MODEL", "qwen3:8b"));
        System.out.printf("Source document resource location: %s%n",
                OracleSampleConfiguration.env("ORACLE_SOURCE_DOCUMENT_RESOURCE",
                        OracleSampleConfiguration.DEFAULT_SOURCE_RESOURCE));
        System.out.printf("Oracle session id: %s%n", sessionId);
        System.out.println("Type 'exit' or 'quit' to stop.");
    }
}
