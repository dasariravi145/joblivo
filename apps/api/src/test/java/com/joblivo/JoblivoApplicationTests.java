package com.joblivo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("isDatabaseReachable")
class JoblivoApplicationTests {

    static boolean isDatabaseReachable() {
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/joblivo_db"));
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME",
                System.getProperty("spring.datasource.username", "postgres"));
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD",
                System.getProperty("spring.datasource.password", "postgres"));
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void actuatorHealthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
