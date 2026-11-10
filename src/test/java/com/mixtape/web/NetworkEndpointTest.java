package com.mixtape.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:networkdb;DB_CLOSE_DELAY=-1",
        "server.port=0",
        "share.host=192.168.1.10"})
@AutoConfigureMockMvc
class NetworkEndpointTest {
    @Autowired
    MockMvc mvc;

    private static final RequestPostProcessor OWNER = request -> {
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Host", "127.0.0.1:8080");
        return request;
    };

    @Test
    void configuredHostComesFirstAndIsReportedSeparately() throws Exception {
        mvc.perform(get("/api/network").with(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value("192.168.1.10"))
                .andExpect(jsonPath("$.addresses[0].ip").value("192.168.1.10"))
                .andExpect(jsonPath("$.addresses[0].name").value("Configured"))
                .andExpect(jsonPath("$.port").exists());
    }
}
