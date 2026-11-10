package com.mixtape.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/join/{token}")
    public String join() {
        return "forward:/join.html";
    }
}
