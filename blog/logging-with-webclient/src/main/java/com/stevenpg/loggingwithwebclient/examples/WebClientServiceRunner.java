package com.stevenpg.loggingwithwebclient.examples;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebClientServiceRunner implements ApplicationRunner {

    final WebClientService webClientService;

    @Autowired
    public WebClientServiceRunner(WebClientService webClientService) {
        this.webClientService = webClientService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Testing JettyClientLogging...");
        webClientService.testJettyClient();
    }
}
