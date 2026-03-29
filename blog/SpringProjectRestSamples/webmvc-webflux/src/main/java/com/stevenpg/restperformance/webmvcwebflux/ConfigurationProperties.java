package com.stevenpg.restperformance.webmvcwebflux;

import org.springframework.context.annotation.Configuration;

@Configuration
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "endpoint")
public class ConfigurationProperties
{
    private String ok;
    private String notfound;

    public String getOk() {
        return ok;
    }

    public void setOk(String ok) {
        this.ok = ok;
    }

    public String getNotfound() {
        return notfound;
    }

    public void setNotfound(String notfound) {
        this.notfound = notfound;
    }

}
