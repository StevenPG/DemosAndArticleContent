package com.example.boot42;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@ResponseBody
public class DemoController {

    public record Search(String term, int limit) {}

    
    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("database password is hunter2");
    }

    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }
}
