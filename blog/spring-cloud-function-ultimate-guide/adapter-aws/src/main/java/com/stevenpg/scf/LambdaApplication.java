package com.stevenpg.scf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The AWS Lambda surface.
 *
 * <p>On AWS you do NOT run this {@code main} — you configure the Lambda's handler
 * to {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker}. That
 * handler boots this application (it finds it via the {@code MAIN_CLASS} env var
 * / jar manifest), then routes each invocation's input stream to the function
 * named by {@code spring.cloud.function.definition} (see
 * {@code application.properties}) and writes the result to the output stream.
 *
 * <p>The {@code main} method exists only so this is a normal, testable Spring
 * Boot app; {@link com.stevenpg.scf} scanning pulls in every functions-core bean.
 */
@SpringBootApplication
public class LambdaApplication {

    public static void main(String[] args) {
        SpringApplication.run(LambdaApplication.class, args);
    }
}
