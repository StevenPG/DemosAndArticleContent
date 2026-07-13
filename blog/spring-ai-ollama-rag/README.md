# Spring AI + Ollama RAG Chatbot

Companion project for the article [Ultimate Guide to Spring AI with Local Models (Ollama)](https://stevenpg.com/posts/ultimate-guide-spring-ai-ollama).

A fully local RAG chatbot that answers questions about StevenPG's blog posts —
no API keys, no cloud calls. Chat model and embeddings both run on a local
Ollama server; the vector store is Spring AI's in-memory `SimpleVectorStore`.

## Running

1. Install and start [Ollama](https://ollama.com) (`ollama serve`). The app
   pulls `qwen3:8b` and `nomic-embed-text` automatically on first startup
   (`pull-model-strategy: when_missing`).
2. Put some markdown files in `./corpus/` (e.g. a checkout of the blog's
   `src/content/blog/` directory).
3. `./gradlew bootRun`
4. Ask it something:

```bash
curl -s localhost:8080/chat \
  -H 'Content-Type: application/json' \
  -d '{"question": "Why is UUID4 a bad primary key?"}'
```

## Notes

- Spring AI 1.1.x on Spring Boot 3.5.x (Spring AI's Boot 4 line wasn't GA at
  time of writing).
- Swap `SimpleVectorStore` for the pgvector starter when the corpus outgrows
  memory — the article covers this.
