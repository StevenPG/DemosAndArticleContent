package com.stevenpg.restperformance.springintegration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class NotFoundExceptionHandler
{
    public Message<?> transform(ErrorMessage message) {
        if (message.getPayload().getCause() instanceof HttpClientErrorException.NotFound) {
            var entity = new ResponseEntity<>("Entity not found.", HttpStatus.NOT_FOUND);
            return MessageBuilder.withPayload(entity).copyHeaders(message.getHeaders()).build();
        }
        else {
            // This is taking many many minutes just to handle the exception properly...
            return MessageBuilder.withPayload(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))
                    .copyHeaders(message.getHeaders()).build();
        }
    }
}
