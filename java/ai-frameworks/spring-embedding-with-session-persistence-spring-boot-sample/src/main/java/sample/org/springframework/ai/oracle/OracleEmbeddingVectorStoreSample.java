/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package sample.org.springframework.ai.oracle;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class OracleEmbeddingVectorStoreSample {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OracleEmbeddingVectorStoreSample.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
