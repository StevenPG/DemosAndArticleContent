package com.example.boot42;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class Boot42ApplicationTests {

    @Autowired
    RestTestClient client;

    @Test
    void hello() {
        client.get().uri("/hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("hello");
    }

    @Test
    void querySearch() {
        client.method(HttpMethod.QUERY).uri("/products/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DemoController.Search("boots", 5))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("searching for 'boots' (limit 5)");
    }

    @Test
    void unhandledExceptionIsProblemDetail() {
        client.get().uri("/boom")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Internal Server Error")
                .jsonPath("$.detail").doesNotExist();
    }
}
