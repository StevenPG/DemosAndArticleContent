package com.stevenpg.loggingwithwebclient.examples;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JettyWebClient {

    Logger logger = LoggerFactory.getLogger(JettyWebClient.class);

    // logging with jetty httpclient
    // Needs jetty-reactive-httpclient
    @Bean
    public WebClient jettyHttpClient() {
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        HttpClient httpClient = new HttpClient(sslContextFactory) {
            @Override
            public Request newRequest(URI uri) {
                Request request = super.newRequest(uri);
                return enhance(request);
            }
        };

        return WebClient.builder().clientConnector(new JettyClientHttpConnector(httpClient)).build();
    }

    /**
     * Method that adds logging to the incoming request at each interval
     */
    private Request enhance(Request inboundRequest) {
        StringBuilder log = new StringBuilder();
        // Request Logging
        inboundRequest.onRequestBegin(request ->
                log.append("Request: \n")
                .append("URI: ")
                .append(request.getURI())
                .append("\n")
                .append("Method: ")
                .append(request.getMethod()));
        inboundRequest.onRequestHeaders(request -> {
            log.append("\nHeaders:\n");
            for (HttpField header : request.getHeaders()) {
                log.append("\t\t" + header.getName() + " : " + header.getValue() + "\n");
            }
        });
        inboundRequest.onRequestContent((request, content) ->
                log.append("Body: \n\t")
                .append(content.toString()));
        log.append("\n");

        // Response Logging
        inboundRequest.onResponseBegin(response ->
                log.append("Response:\n")
                .append("Status: ")
                .append(response.getStatus())
                .append("\n"));
        inboundRequest.onResponseHeaders(response -> {
           log.append("Headers:\n");
           for (HttpField header : response.getHeaders()) {
               log.append("\t\t" + header.getName() + " : " + header.getValue() + "\n");
           }
        });
        inboundRequest.onResponseContent(((response, content) -> {
            var bufferAsString = StandardCharsets.UTF_8.decode(content).toString();
            log.append("Response Body:\n" + bufferAsString);
        }));

        // Add actual log invocation
        logger.info("HTTP ->\n");
        inboundRequest.onRequestSuccess(request -> logger.info(log.toString()));
        inboundRequest.onResponseSuccess(response -> logger.info(log.toString()));

        // Return original request
        return inboundRequest;
    }
}
