/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package org.springframwork.ai.oracle.springaiautoconfigurationsample;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sample.oracle")
public class OracleSampleProperties {

    private String vectorTableName = "SPRING_AI_ORACLE_SAMPLE_STORE";

    private String sourceDocumentResource = "classpath:/sample-documents/oracle-sample.md";

    private int vectorStoreAddBatchSize = 16;

    private String sessionId = "oracle-sample-session";

    private String userId = "oracle-sample-user";

    private int retrievalTopK = 3;

    private double retrievalSimilarityThreshold = 0.2;

    private String ollamaBaseUrl = "http://localhost:11434";

    private String ollamaChatModel = "qwen3:8b";

    private Integer ollamaChatMaxTokens = 512;

    private boolean ollamaChatThinkingEnabled;

    public String getVectorTableName() {
        return this.vectorTableName;
    }

    public void setVectorTableName(String vectorTableName) {
        this.vectorTableName = vectorTableName;
    }

    public String getSourceDocumentResource() {
        return this.sourceDocumentResource;
    }

    public void setSourceDocumentResource(String sourceDocumentResource) {
        this.sourceDocumentResource = sourceDocumentResource;
    }

    public int getVectorStoreAddBatchSize() {
        return this.vectorStoreAddBatchSize;
    }

    public void setVectorStoreAddBatchSize(int vectorStoreAddBatchSize) {
        this.vectorStoreAddBatchSize = vectorStoreAddBatchSize;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getRetrievalTopK() {
        return this.retrievalTopK;
    }

    public void setRetrievalTopK(int retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }

    public double getRetrievalSimilarityThreshold() {
        return this.retrievalSimilarityThreshold;
    }

    public void setRetrievalSimilarityThreshold(double retrievalSimilarityThreshold) {
        this.retrievalSimilarityThreshold = retrievalSimilarityThreshold;
    }

    public String getOllamaBaseUrl() {
        return this.ollamaBaseUrl;
    }

    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public String getOllamaChatModel() {
        return this.ollamaChatModel;
    }

    public void setOllamaChatModel(String ollamaChatModel) {
        this.ollamaChatModel = ollamaChatModel;
    }

    public Integer getOllamaChatMaxTokens() {
        return this.ollamaChatMaxTokens;
    }

    public void setOllamaChatMaxTokens(Integer ollamaChatMaxTokens) {
        this.ollamaChatMaxTokens = ollamaChatMaxTokens;
    }

    public boolean isOllamaChatThinkingEnabled() {
        return this.ollamaChatThinkingEnabled;
    }

    public void setOllamaChatThinkingEnabled(boolean ollamaChatThinkingEnabled) {
        this.ollamaChatThinkingEnabled = ollamaChatThinkingEnabled;
    }

}
