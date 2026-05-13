package com.lol.analyzer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the main matchup form at {@code /}.
 */
@Controller
public class WebController {
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
