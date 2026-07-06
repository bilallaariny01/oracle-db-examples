/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package org.springframwork.ai.oracle.springaiautoconfigurationsample;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OracleAutoconfigurationSample {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OracleAutoconfigurationSample.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

}
