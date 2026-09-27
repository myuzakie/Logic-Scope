package com.logicscope.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({RootController.class, DashboardConfigController.class})
@Import(ApiExceptionHandler.class)
@TestPropertySource(properties = "logicscope.target-project=/tmp/sample-project")
class RootControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootReturnsServiceInfo() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                            "service": "LogicScope",
                            "status": "UP",
                            "endpoints": {
                                "health": "/api/health",
                                "scan": "POST /api/scan"
                            }
                        }
                        """));
    }

    @Test
    void dashboardConfigReturnsConfiguredTargetProject() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetProject").value("/tmp/sample-project"));
    }

    @Test
    void dashboardServesStaticPage() throws Exception {
        mockMvc.perform(get("/dashboard/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/html")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Scan Project")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/scan")));
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/unknown-route"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {"error":"NOT_FOUND","message":"Endpoint not found"}
                        """));
    }
}
