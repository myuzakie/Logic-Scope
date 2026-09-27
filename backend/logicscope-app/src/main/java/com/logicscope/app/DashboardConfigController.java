package com.logicscope.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class DashboardConfigController {
    private final String targetProject;

    DashboardConfigController(@Value("${logicscope.target-project:}") String targetProject) {
        this.targetProject = targetProject;
    }

    @GetMapping("/api/config")
    Map<String, String> config() {
        return Map.of("targetProject", targetProject);
    }

    @GetMapping(value = {"/dashboard", "/dashboard/"}, produces = MediaType.TEXT_HTML_VALUE)
    Resource dashboard() {
        return new ClassPathResource("static/dashboard/index.html");
    }
}
