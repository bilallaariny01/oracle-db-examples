/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package org.springframwork.ai.oracle.springaiautoconfigurationsample;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;

@Configuration(proxyBeanMethods = false)
class OracleSampleConfiguration {

    @Bean
    SessionMemoryAdvisor sessionMemoryAdvisor(SessionService sessionService, OracleSampleProperties properties) {
        return SessionMemoryAdvisor.builder(sessionService)
                .defaultUserId(properties.getUserId())
                .build();
    }

    @Bean
    OllamaApi ollamaApi(OracleSampleProperties properties) {
        return OllamaApi.builder()
                .baseUrl(properties.getOllamaBaseUrl())
                .build();
    }

    @Bean
    ChatModel ollamaChatModel(OllamaApi ollamaApi, OracleSampleProperties properties,
            ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<RetryTemplate> retryTemplate) {
        OllamaChatOptions.Builder chatOptionsBuilder = OllamaChatOptions.builder()
                .model(properties.getOllamaChatModel())
                .maxTokens(properties.getOllamaChatMaxTokens());

        if (properties.isOllamaChatThinkingEnabled()) {
            chatOptionsBuilder.enableThinking();
        }
        else {
            chatOptionsBuilder.disableThinking();
        }

        OllamaChatOptions chatOptions = chatOptionsBuilder.build();

        OllamaChatModel.Builder builder = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(chatOptions)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

        RetryTemplate uniqueRetryTemplate = retryTemplate.getIfUnique();
        if (uniqueRetryTemplate != null) {
            builder.retryTemplate(uniqueRetryTemplate);
        }

        return builder.build();
    }

    @Bean
    ChatClient.Builder chatClientBuilder(ChatModel chatModel, SessionMemoryAdvisor sessionMemoryAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(sessionMemoryAdvisor);
    }

    @Bean
    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore, OracleSampleProperties properties) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(properties.getRetrievalTopK())
                        .similarityThreshold(properties.getRetrievalSimilarityThreshold())
                        .build())
                .build();
    }

}
