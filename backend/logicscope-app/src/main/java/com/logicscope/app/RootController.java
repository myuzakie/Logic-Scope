package com.logicscope.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "service", "LogicScope",
                "status", "UP",
                "endpoints", Map.of(
                        "health", "/api/health",
                        "scan", "POST /api/scan"
                )
        );
    }
}
