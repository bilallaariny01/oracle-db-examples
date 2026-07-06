/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package sample.org.springframework.ai.oracle;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.oracle.embedding.OracleEmbeddingModel;
import org.springframework.ai.oracle.embedding.OracleEmbeddingOptions;
import org.springframework.ai.oracle.embedding.OracleEmbeddingPreferences;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.ai.session.jdbc.OracleJdbcSessionRepositoryDialect;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StringUtils;

@Configuration
class OracleSampleConfiguration {

    static final String VECTOR_TABLE_NAME = "SPRING_AI_ORACLE_SAMPLE_STORE";
    static final String DEFAULT_SOURCE_RESOURCE = "sample-documents/oracle-sample.md";
    static final int DEFAULT_EMBEDDING_DIMENSIONS = 384;
    static final int DEFAULT_ADD_BATCH_SIZE = 16;

    static final String DEFAULT_CHUNK_BY = "words";
    static final int DEFAULT_CHUNK_MAX = 80;
    static final int DEFAULT_CHUNK_OVERLAP = 16;
    static final String DEFAULT_CHUNK_SPLIT = "sentence";

    static final String DEFAULT_SESSION_ID = "oracle-sample-session";
    static final String DEFAULT_USER_ID = "oracle-sample-user";

    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_CHAT_MODEL = "qwen3:8b";
    private static final int DEFAULT_OLLAMA_MAX_TOKENS = 150;

    @Bean
    DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(requiredEnv("ORACLE_JDBC_URL"));
        dataSource.setUsername(requiredEnv("ORACLE_USERNAME"));
        dataSource.setPassword(requiredEnv("ORACLE_PASSWORD"));
        return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    SessionRepository sessionRepository(DataSource dataSource) {
        initializeSessionSchema(dataSource);
        return JdbcSessionRepository.builder()
                .dataSource(dataSource)
                .dialect(new OracleJdbcSessionRepositoryDialect())
                .build();
    }

    @Bean
    SessionService sessionService(SessionRepository sessionRepository) {
        return DefaultSessionService.builder().sessionRepository(sessionRepository).build();
    }

    @Bean
    SessionMemoryAdvisor sessionMemoryAdvisor(SessionService sessionService) {
        return SessionMemoryAdvisor.builder(sessionService)
                .defaultUserId(DEFAULT_USER_ID)
                .build();
    }

    @Bean
    ChatModel chatModel() {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(env("OLLAMA_CHAT_MODEL", DEFAULT_OLLAMA_CHAT_MODEL))
                .disableThinking()
                .maxTokens(DEFAULT_OLLAMA_MAX_TOKENS)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(env("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL)).build())
                .options(options)
                .build();
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel, SessionMemoryAdvisor sessionMemoryAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(sessionMemoryAdvisor)
                .build();
    }

    @Bean
    OracleEmbeddingModel embeddingModel(DataSource dataSource) {
        int dimensions = envInt("ORACLE_EMBEDDING_DIMENSIONS", DEFAULT_EMBEDDING_DIMENSIONS);
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

    @Bean
    OracleVectorStore oracleVectorStore(JdbcTemplate jdbcTemplate, OracleEmbeddingModel embeddingModel) {
        int dimensions = envInt("ORACLE_EMBEDDING_DIMENSIONS", DEFAULT_EMBEDDING_DIMENSIONS);
        return OracleVectorStore.builder(jdbcTemplate, embeddingModel)
                .tableName(VECTOR_TABLE_NAME)
                .dimensions(dimensions)
                .distanceType(OracleVectorStore.OracleVectorStoreDistanceType.COSINE)
                .forcedNormalization(true)
                .initializeSchema(true)
                .removeExistingVectorStoreTable(true)
                .build();
    }

    @Bean
    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(OracleVectorStore oracleVectorStore) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(oracleVectorStore)
                        .topK(3)
                        .similarityThreshold(0.2)
                        .build())
                .build();
    }

    static void initializeSessionSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("session.sql"));
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize session schema from session.sql", ex);
        }
    }

    static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : defaultValue;
    }

    static boolean envBoolean(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }
}
