# Spring AI Oracle Sample (Vocabulary Chunking)

This project is a runnable sample app that demonstrates an end-to-end Oracle RAG flow with Spring AI:

1. Load a classpath document with `OracleDocumentReader`.
2. Split it with `OracleDocumentSplitter` (default: `by=vocabulary`).
3. Embed chunks with `OracleEmbeddingModel`.
4. Store vectors in Oracle with `OracleVectorStore`.
5. Start an interactive terminal chat (Ollama) that uses retrieved chunks as context.

The sample main class is:
`sample.org.springframework.ai.oracle.OracleEmbeddingVectorStoreSample`

## Prerequisites

1. Java 17+
2. Maven 3.9+
3. Oracle Database with vector features enabled
4. A DB user that can run `DBMS_VECTOR_CHAIN` operations used by this sample
5. Ollama running locally (default base URL: `http://localhost:11434`)

Session schema setup options:

1. Run `src/main/resources/session.sql` manually in your Oracle schema before startup.
2. Or let the sample execute `session.sql` automatically at startup (current default behavior).

## Required Environment Variables

Set these before running:

```bash
export ORACLE_JDBC_URL='jdbc:oracle:thin:@//<host>:<port>/<service>'
export ORACLE_USERNAME='<db_user>'
export ORACLE_PASSWORD='<db_password>'
```

## Vocabulary Setup (Required for Default Chunking Mode)

This sample defaults to `ORACLE_CHUNK_BY=vocabulary`, so make sure your vocabulary exists first.

Example setup:

```sql
CREATE TABLE DOC_VOCABTAB (
  TOKEN VARCHAR2(4000)
);

-- Insert one tokenizer token per row (must match your embedding tokenizer)
INSERT INTO DOC_VOCABTAB (TOKEN) VALUES ('[PAD]');
INSERT INTO DOC_VOCABTAB (TOKEN) VALUES ('[UNK]');
COMMIT;

DECLARE
  params CLOB := '{
    "table_name":"DOC_VOCABTAB",
    "column_name":"TOKEN",
    "vocabulary_name":"DOC_VOCAB_1",
    "format":"bert",
    "cased":false
  }';
BEGIN
  DBMS_VECTOR_CHAIN.CREATE_VOCABULARY(JSON(params));
END;
/

SELECT vocab_name FROM user_vector_vocab ORDER BY vocab_name;
```

Set the vocabulary name explicitly when you run:

```bash
export ORACLE_CHUNK_BY='vocabulary'
export ORACLE_CHUNK_VOCABULARY='DOC_VOCAB_1'
```

## Run the Sample

```bash
mvn -DskipTests exec:java
```

After startup, ask questions in the terminal. Type `exit` or `quit` to stop.

## Common Optional Variables

```bash
# Source document on classpath (default: sample-documents/oracle-sample.md)
export ORACLE_SOURCE_DOCUMENT_RESOURCE='sample-documents/oracle-sample.md'

# Embedding model settings
export ORACLE_EMBEDDING_MODEL='ALL_MINILM_L12_V2'
export ORACLE_EMBEDDING_DIMENSIONS='384'
export ORACLE_EMBEDDING_BATCHING='false'

# Chunking settings
export ORACLE_CHUNK_MAX='80'
export ORACLE_CHUNK_OVERLAP='16'
export ORACLE_CHUNK_SPLIT='sentence'

# Vector-store insert batching
export ORACLE_VECTORSTORE_ADD_BATCH_SIZE='16'

# Chat/Ollama settings
export OLLAMA_BASE_URL='http://localhost:11434'
export OLLAMA_CHAT_MODEL='qwen3:8b'
```

## Optional ONNX Model Load on Startup

Enable:

```bash
export ORACLE_ONNX_LOAD_ON_STARTUP='true'
```

Then choose exactly one mode:

1. Local Oracle directory mode

```bash
export ORACLE_ONNX_DIRECTORY_ALIAS='DM_DUMP'
export ORACLE_ONNX_FILE='all_minilm_l12_v2.onnx'
```

2. Cloud object storage mode

```bash
export ORACLE_ONNX_URI='https://objectstorage.<region>.oraclecloud.com/.../all_minilm_l12_v2.onnx'
export ORACLE_ONNX_CREDENTIAL='MY_OCI_CREDENTIAL' # optional
```

Do not mix local and cloud ONNX variables in the same run.

## Notes

1. The sample recreates vector-store table `SPRING_AI_ORACLE_SAMPLE_STORE` on startup.
2. If `ORACLE_SOURCE_DOCUMENT_RESOURCE` is invalid, startup fails with a classpath resource error.
3. If vocabulary mode is enabled and the vocabulary name is missing/invalid, chunking fails.
4. Session-memory tables come from `src/main/resources/session.sql`; you can pre-run the script or use startup auto-initialization.
