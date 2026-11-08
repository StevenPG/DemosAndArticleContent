package com.example.boot42;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RedirectController {
    @GetMapping("/old")
    public String old() {
        return "redirect:/hello";
    }
}
