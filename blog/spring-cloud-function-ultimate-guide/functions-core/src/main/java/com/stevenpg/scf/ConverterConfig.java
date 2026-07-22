package com.stevenpg.scf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers custom {@code MessageConverter}s with Spring Cloud Function.
 *
 * <p>SCF builds a composite message converter from every {@code MessageConverter}
 * bean in the context plus its built-in JSON/byte[]/String ones. So contributing
 * a new wire format is just declaring a bean — no function has to change.
 */
@Configuration
public class ConverterConfig {

    @Bean
    public CsvOrderMessageConverter csvOrderMessageConverter() {
        return new CsvOrderMessageConverter();
    }
}
