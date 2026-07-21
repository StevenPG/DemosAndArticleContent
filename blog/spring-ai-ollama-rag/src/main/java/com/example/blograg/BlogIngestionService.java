package com.example.blograg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads every markdown post from the corpus directory, splits it into
 * token-sized chunks, embeds each chunk via Ollama, and writes it into the
 * vector store. Runs once on startup.
 */
@Component
public class BlogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BlogIngestionService.class);

    private final VectorStore vectorStore;
    private final Path corpusDir;

    public BlogIngestionService(VectorStore vectorStore,
                                @Value("${blog.corpus-dir:./corpus}") Path corpusDir) {
        this.vectorStore = vectorStore;
        this.corpusDir = corpusDir;
    }

    @Bean
    ApplicationRunner ingest() {
        return args -> {
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(400)
                    .withMinChunkSizeChars(200)
                    .build();

            try (Stream<Path> files = Files.list(corpusDir)) {
                List<Document> chunks = files
                        .filter(p -> p.toString().endsWith(".md"))
                        .flatMap(p -> splitter.split(toDocument(p)).stream())
                        .toList();

                log.info("Embedding {} chunks from {} — first run downloads take a while",
                        chunks.size(), corpusDir);
                vectorStore.add(chunks);
                log.info("Ingestion complete");
            }
        };
    }

    private Document toDocument(Path path) {
        try {
            return new Document(Files.readString(path),
                    Map.of("source", path.getFileName().toString()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
