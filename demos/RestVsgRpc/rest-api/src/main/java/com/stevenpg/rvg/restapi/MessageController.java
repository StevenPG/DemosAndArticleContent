package com.stevenpg.rvg.restapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageController {

    private final ObjectMapper objectMapper;

    public MessageController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/v1/create")
    public ResponseEntity<String> create(@RequestBody Message jsonMessage) throws JsonProcessingException {
        System.out.println("Received request " + jsonMessage.toString());
        return new ResponseEntity<>("{\"status\":\"created\"}", HttpStatus.CREATED);
    }

}
