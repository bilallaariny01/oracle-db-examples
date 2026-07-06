/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package sample.org.springframework.ai.oracle;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.oracle.chunking.OracleChunkingPreferences;
import org.springframework.ai.oracle.chunking.OracleDocumentSplitter;
import org.springframework.ai.oracle.embedding.OracleEmbeddingModel;
import org.springframework.ai.oracle.embedding.OracleEmbeddingOptions;
import org.springframework.ai.oracle.embedding.OracleEmbeddingPreferences;
import org.springframework.ai.oracle.loader.OracleDocumentPreferences;
import org.springframework.ai.oracle.loader.OracleDocumentReader;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.ai.session.jdbc.OracleJdbcSessionRepositoryDialect;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore.OracleVectorStoreDistanceType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.util.List;
import java.util.Scanner;
import javax.sql.DataSource;

/**
 * End-to-end sample that loads a document, stores chunk embeddings in Oracle Vector Store,
 * and serves an interactive RAG chat loop.
 */
public final class OracleEmbeddingVectorStoreSample {

    private static final String VECTOR_TABLE_NAME = "SPRING_AI_ORACLE_SAMPLE_STORE";
    private static final String DEFAULT_SOURCE_RESOURCE = "sample-documents/oracle-sample.md";
    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 384;
    private static final int DEFAULT_ADD_BATCH_SIZE = 16;

    private static final String DEFAULT_CHUNK_BY = "vocabulary";
    private static final int DEFAULT_CHUNK_MAX = 80;
    private static final int DEFAULT_CHUNK_OVERLAP = 16;
    private static final String DEFAULT_CHUNK_SPLIT = "sentence";
    private static final String DEFAULT_CHUNK_VOCABULARY = "DOC_VOCAB_1";

    private static final String DEFAULT_SESSION_ID = "oracle-sample-session";
    private static final String DEFAULT_USER_ID = "oracle-sample-user";

    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_CHAT_MODEL = "qwen3:8b";
    private static final int DEFAULT_OLLAMA_MAX_TOKENS = 150;

    private OracleEmbeddingVectorStoreSample() {
    }

    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {
            DriverManagerDataSource dataSource = createDataSource();
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            int dimensions = envInt("ORACLE_EMBEDDING_DIMENSIONS", DEFAULT_EMBEDDING_DIMENSIONS);
            String sessionId = env("ORACLE_SAMPLE_SESSION_ID", DEFAULT_SESSION_ID);

            initializeSessionSchema(dataSource);
            SessionRepository repository = buildSessionRepository(dataSource);
            SessionService sessionService = DefaultSessionService.builder().sessionRepository(repository).build();
            SessionMemoryAdvisor memoryAdvisor = SessionMemoryAdvisor.builder(sessionService)
                    .defaultUserId(DEFAULT_USER_ID)
                    .build();

            ChatClient assistant = ChatClient.builder(buildOllamaChatModel())
                    .defaultAdvisors(memoryAdvisor)
                    .build();

            OracleEmbeddingModel embeddingModel = buildEmbeddingModel(dataSource, dimensions);
            embeddingModel.afterPropertiesSet();

            OracleVectorStore vectorStore = OracleVectorStore.builder(jdbcTemplate, embeddingModel)
                    .tableName(VECTOR_TABLE_NAME)
                    .dimensions(dimensions)
                    .distanceType(OracleVectorStoreDistanceType.COSINE)
                    .forcedNormalization(true)
                    .initializeSchema(true)
                    .removeExistingVectorStoreTable(true)
                    .build();
            vectorStore.afterPropertiesSet();

            List<Document> documents = loadSourceDocuments(dataSource);
            List<Document> chunks = buildDocumentSplitter(dataSource).split(documents);
            int addBatchSize = envInt("ORACLE_VECTORSTORE_ADD_BATCH_SIZE", DEFAULT_ADD_BATCH_SIZE);
            addInBatches(vectorStore, chunks, addBatchSize);
            RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = retrievalAugmentationAdvisor(vectorStore);

            printStartupSummary(documents.size(), chunks.size(), addBatchSize, sessionId);
            runChatLoop(scanner, assistant, vectorStore, retrievalAugmentationAdvisor, sessionId);
        }
    }

    private static void runChatLoop(Scanner scanner, ChatClient assistant, OracleVectorStore vectorStore,
            RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, String sessionId) {
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
                String answer = generateAnswer(assistant, retrievalAugmentationAdvisor, sessionId, input);
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

    private static void printStartupSummary(int sourceCount, int chunkCount, int addBatchSize, String sessionId) {
        System.out.printf("Loaded %d documents as %d chunks into %s.%n", sourceCount, chunkCount, VECTOR_TABLE_NAME);
        System.out.printf(
                "Chunking setup: by=%s, vocabulary=%s, max=%d, overlap=%d, split=%s%n",
                env("ORACLE_CHUNK_BY", DEFAULT_CHUNK_BY),
                env("ORACLE_CHUNK_VOCABULARY", DEFAULT_CHUNK_VOCABULARY),
                envInt("ORACLE_CHUNK_MAX", DEFAULT_CHUNK_MAX),
                envInt("ORACLE_CHUNK_OVERLAP", DEFAULT_CHUNK_OVERLAP),
                env("ORACLE_CHUNK_SPLIT", DEFAULT_CHUNK_SPLIT));
        String extraChunkPreferences = env("ORACLE_CHUNK_ADDITIONAL_PREFERENCES_JSON", "");
        if (StringUtils.hasText(extraChunkPreferences)) {
            System.out.printf("Additional chunk preferences: %s%n", extraChunkPreferences);
        }
        System.out.printf("Vector-store add batch size: %d%n", addBatchSize);
        System.out.printf("Embedding batching enabled: %s%n",
                envBoolean("ORACLE_EMBEDDING_BATCHING", false));
        System.out.printf("ONNX load on startup enabled: %s%n", true);
        System.out.printf("Chat started with Ollama model %s.%n",
                env("OLLAMA_CHAT_MODEL", DEFAULT_OLLAMA_CHAT_MODEL));
        System.out.printf("Source document resource: %s%n",
                env("ORACLE_SOURCE_DOCUMENT_RESOURCE", DEFAULT_SOURCE_RESOURCE));
        System.out.printf("Oracle session id: %s%n", sessionId);
        System.out.println("Type 'exit' or 'quit' to stop.");
    }

    private static void addInBatches(OracleVectorStore vectorStore, List<Document> documents, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalStateException("ORACLE_VECTORSTORE_ADD_BATCH_SIZE must be greater than 0.");
        }
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }

    private static String generateAnswer(ChatClient assistant,
            RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, String sessionId, String input) {
        return assistant.prompt()
                .advisors(retrievalAugmentationAdvisor)
                .system("""
                        You are a helpful assistant.

                        If the user asks general conversational questions, answer normally.

                        If the user asks about Oracle sample code, embeddings, vector stores, chunking,
                        database tables, or loaded documents, use the retrieved context.

                        If a document-specific answer cannot be grounded in the retrieved context,
                        say that the Oracle vector store did not return enough context.
                        """)
                .user(input)
                .advisors(advisor -> advisor
                        .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId)
                        .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, DEFAULT_USER_ID))
                .call()
                .content();
    }

    private static RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(3)
                        .similarityThreshold(0.2)
                        .build())
                .build();
    }

    private static SessionRepository buildSessionRepository(DataSource dataSource) {
        return JdbcSessionRepository.builder()
                .dataSource(dataSource)
                .dialect(new OracleJdbcSessionRepositoryDialect())
                .build();
    }

    private static void initializeSessionSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("session.sql"));
        }
    }

    private static List<Document> loadSourceDocuments(DataSource dataSource) {
        String resourcePath = env("ORACLE_SOURCE_DOCUMENT_RESOURCE", DEFAULT_SOURCE_RESOURCE);
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Resource not found on classpath: " + resourcePath + " (add it under src/main/resources)");
        }

        OracleDocumentReader reader = OracleDocumentReader.builder(dataSource)
                .resource(resource)
                .preferences(OracleDocumentPreferences.builder().format("TEXT").build())
                .build();
        return reader.get();
    }

    private static OracleDocumentSplitter buildDocumentSplitter(DataSource dataSource) {
        String chunkBy = env("ORACLE_CHUNK_BY", DEFAULT_CHUNK_BY);
        OracleChunkingPreferences.Builder options = OracleChunkingPreferences.builder()
                .by(chunkBy)
                .max(envInt("ORACLE_CHUNK_MAX", DEFAULT_CHUNK_MAX))
                .overlap(envInt("ORACLE_CHUNK_OVERLAP", DEFAULT_CHUNK_OVERLAP))
                .split(env("ORACLE_CHUNK_SPLIT", DEFAULT_CHUNK_SPLIT))
                .language("american")
                .normalize("all")
                .extended(true);

        if ("vocabulary".equalsIgnoreCase(chunkBy)) {
            options.vocabulary(env("ORACLE_CHUNK_VOCABULARY", DEFAULT_CHUNK_VOCABULARY));
        }

        return OracleDocumentSplitter.builder(dataSource).preferences(options.build()).build();
    }

    private static OracleEmbeddingModel buildEmbeddingModel(DataSource dataSource, int dimensions) {
        OracleEmbeddingOptions options = OracleEmbeddingOptions.builder()
                .model(AllMiniLmL12V2EmbeddingModel.MODEL_NAME)
                .dimensions(dimensions)
                .batching(envBoolean("ORACLE_EMBEDDING_BATCHING", false))
                .preferences(OracleEmbeddingPreferences.builder()
                        .provider("database")
                        .model(AllMiniLmL12V2EmbeddingModel.MODEL_NAME)
                        .build())
                .build();

        return OracleEmbeddingModel.builder(dataSource)
                .defaultOptions(options)
                .initializeOnStartup(true)
                .onnxModelName(AllMiniLmL12V2EmbeddingModel.MODEL_NAME)
                .onnxUri(AllMiniLmL12V2EmbeddingModel.modelUri())
                .build();
    }

    private static ChatModel buildOllamaChatModel() {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(env("OLLAMA_CHAT_MODEL", DEFAULT_OLLAMA_CHAT_MODEL))
                .disableThinking()
                .maxTokens(DEFAULT_OLLAMA_MAX_TOKENS)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(buildOllamaApi())
                .options(options)
                .build();
    }

    private static OllamaApi buildOllamaApi() {
        return OllamaApi.builder().baseUrl(env("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL)).build();
    }

    private static DriverManagerDataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(requiredEnv("ORACLE_JDBC_URL"));
        dataSource.setUsername(requiredEnv("ORACLE_USERNAME"));
        dataSource.setPassword(requiredEnv("ORACLE_PASSWORD"));
        return dataSource;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : defaultValue;
    }

    private static boolean envBoolean(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }
}
