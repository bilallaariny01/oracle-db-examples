/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package org.springframwork.ai.oracle.springaiautoconfigurationsample;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.oracle.loader.OracleDocumentReader;
import org.springframework.ai.model.oracle.autoconfigure.OracleDocumentLoaderAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAutoconfigurationSampleApplicationTests {

    @Test
    void defaultSourceDocumentUsesExplicitClasspathLocation() {
        assertThat(new OracleSampleProperties().getSourceDocumentResource())
                .isEqualTo("classpath:/sample-documents/oracle-sample.md");
    }

    @Test
    void autoConfiguredReaderLoadsPackagedClasspathResource() throws Exception {
        byte[] expectedContent = new ClassPathResource("sample-documents/oracle-sample.md").getContentAsByteArray();
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        Blob blob = mock(Blob.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.createBlob()).thenReturn(blob);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("text")).thenReturn("converted classpath content");
        when(resultSet.getString("metadata")).thenReturn("<html><head></head></html>");

        new ApplicationContextRunner()
                .withBean(DataSource.class, () -> dataSource)
                .withConfiguration(AutoConfigurations.of(OracleDocumentLoaderAutoConfiguration.class))
                .withPropertyValues("spring.ai.model.embedding=oracle",
                        "spring.ai.oracle.document-loader.resource=classpath:/sample-documents/oracle-sample.md",
                        "spring.ai.oracle.document-loader.preferences.format=TEXT")
                .run(context -> {
                    OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
                    assertThat(reader.get()).singleElement().satisfies(document -> {
                        assertThat(document.getText()).isEqualTo("converted classpath content");
                        assertThat(document.getMetadata())
                                .containsEntry("file_name", "oracle-sample.md");
                        assertThat(document.getMetadata().get("source").toString())
                                .endsWith("/sample-documents/oracle-sample.md");
                    });
                });

        verify(blob).setBytes(eq(1L), eq(expectedContent));
        verify(blob).free();
    }

}
