package tn.esprit.authservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public WebClient webClient() {
        // Return a mock WebClient that does nothing
        return WebClient.builder().build();
    }
}