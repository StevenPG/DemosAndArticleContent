package com.stevenpg.restperformance.webmvc;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Service
{
    RestTemplate restTemplate;
    ConfigurationProperties configurationProperties;

    public Service(RestTemplate restTemplate, ConfigurationProperties properties) {
        this.restTemplate = restTemplate;
        this.configurationProperties = properties;
    }

    public ResponseEntity goodResponse() {
        restTemplate.getForEntity(configurationProperties.getOk(), String.class);
        return new ResponseEntity(HttpStatus.OK);
    }

    public ResponseEntity badResponse() {
        try {
            restTemplate.getForEntity(configurationProperties.getNotfound(), String.class);
            return new ResponseEntity(HttpStatus.OK);
        } catch (HttpClientErrorException e) {
            if(e instanceof HttpClientErrorException.NotFound) {
                return new ResponseEntity("Entity not found.", HttpStatus.NOT_FOUND);
            } else {
                return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }
}
