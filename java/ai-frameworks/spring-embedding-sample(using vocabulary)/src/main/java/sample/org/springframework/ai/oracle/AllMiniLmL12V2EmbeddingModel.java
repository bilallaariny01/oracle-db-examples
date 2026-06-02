/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package sample.org.springframework.ai.oracle;

import org.springframework.util.StringUtils;

final class AllMiniLmL12V2EmbeddingModel {

    static final String MODEL_NAME = "ALL_MINILM_L12_V2";

    private static final String DEFAULT_MODEL_URI =
            "https://adwc4pm.objectstorage.us-ashburn-1.oci.customer-oci.com/p/"
                    + "iPX9W0MZeRkwJKWdFmdJCemmN-iKAl_bFvNGYLW7YqIrw4kKsukL24J2q93Beb9S/n/"
                    + "adwc4pm/b/OML-ai-models/o/all_MiniLM_L12_v2.onnx";

    private AllMiniLmL12V2EmbeddingModel() {
    }

    static String modelUri() {
        String configuredUri = System.getenv("ORACLE_ONNX_URI");
        return StringUtils.hasText(configuredUri) ? configuredUri.trim() : DEFAULT_MODEL_URI;
    }

}
