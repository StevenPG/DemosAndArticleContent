package com.example.blograg;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfiguration {

    /**
     * SimpleVectorStore keeps everything in memory and can persist to a JSON
     * file — perfect for a corpus the size of a blog. Swap for the pgvector
     * starter when the corpus outgrows RAM.
     */
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
