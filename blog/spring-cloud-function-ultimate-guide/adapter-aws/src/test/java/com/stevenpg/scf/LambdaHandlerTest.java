package com.stevenpg.scf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.function.adapter.aws.FunctionInvoker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the REAL AWS Lambda handler ({@link FunctionInvoker}) in-process, with
 * no AWS account. This is exactly what Lambda does at runtime: hand the handler
 * an input stream, read the result off the output stream. If this passes, the
 * deployable unit works — see the README for how to then ship it with
 * {@code sam local invoke} / a real deploy.
 */
class LambdaHandlerTest {

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void pointHandlerAtThisApp() {
        // FunctionInvoker discovers the Spring app via the MAIN_CLASS env/property
        // (in a real jar it comes from the manifest's Start-Class).
        System.setProperty("MAIN_CLASS", LambdaApplication.class.getName());
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty("MAIN_CLASS");
    }

    @Test
    void invokesComposedPipelineThroughLambdaHandler() throws Exception {
        FunctionInvoker invoker = new FunctionInvoker("enrichOrder|validateOrder");

        String order = "{\"orderId\":\"ord-1\",\"customerId\":\"cust-alice\","
                + "\"amount\":199.99,\"currency\":\"USD\",\"itemCount\":2}";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        invoker.handleRequest(
                new ByteArrayInputStream(order.getBytes(StandardCharsets.UTF_8)),
                out,
                null); // Lambda Context — unused by the function

        JsonNode decision = json.readTree(out.toByteArray());
        assertThat(decision.get("orderId").asText()).isEqualTo("ord-1");
        assertThat(decision.get("outcome").asText())
                .isIn("APPROVED", "REJECTED", "REVIEW");
    }
}
