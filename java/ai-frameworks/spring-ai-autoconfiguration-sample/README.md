# Spring AI Oracle autoconfiguration sample

This sample uses `spring-ai-starter-model-oracle` to auto-configure the Oracle
document reader, splitter, embedding model, and related components.

## Source document resource

The document reader is configured by
`spring.ai.oracle.document-loader.resource`. The sample maps
`ORACLE_SOURCE_DOCUMENT_RESOURCE` directly to that property and defaults to the
packaged classpath document:

```shell
export ORACLE_SOURCE_DOCUMENT_RESOURCE='classpath:/sample-documents/oracle-sample.md'
```

It also accepts file and URL resources:

```shell
export ORACLE_SOURCE_DOCUMENT_RESOURCE='file:/absolute/path/to/document.md'
export ORACLE_SOURCE_DOCUMENT_RESOURCE='https://example.com/document.md'
```

File resources retain path metadata. Classpath and URL resources are read through
an `InputStream`. Use a Spring resource prefix for filesystem paths because a bare
path is treated as a classpath resource by Spring's default resource loader.
