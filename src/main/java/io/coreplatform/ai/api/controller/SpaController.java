package io.coreplatform.ai.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping("/providers")
    public String providers() {
        return "forward:/index.html";
    }

    @GetMapping("/models")
    public String models() {
        return "forward:/index.html";
    }

    @GetMapping("/scenes")
    public String scenes() {
        return "forward:/index.html";
    }

    @GetMapping("/prompts")
    public String prompts() {
        return "forward:/index.html";
    }

    @GetMapping({"/tools", "/gateway", "/conversations", "/knowledge", "/agents", "/analytics"})
    public String remainingRuntimes() {
        return "forward:/index.html";
    }
}
