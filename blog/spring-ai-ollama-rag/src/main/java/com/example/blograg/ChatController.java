package com.example.blograg;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
                .defaultSystem("""
                        You answer questions about StevenPG's blog posts.
                        Only answer from the provided context. If the context
                        doesn't contain the answer, say you don't know rather
                        than guessing. Mention which post the answer came from.
                        """)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                .topK(5)
                                .similarityThreshold(0.4)
                                .build())
                        .build())
                .build();
    }

    record Question(String question) {
    }

    record Answer(String answer) {
    }

    @PostMapping("/chat")
    Answer chat(@RequestBody Question question) {
        String answer = chatClient.prompt()
                .user(question.question())
                .call()
                .content();
        return new Answer(answer);
    }
}
