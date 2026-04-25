package tn.esprit.usermanagementservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("All Configuration Tests")
class AllConfigsTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

    @Nested
    @DisplayName("RabbitMQ Configuration Tests")
    class RabbitMQTests {

        @Test
        @DisplayName("RabbitMQConfig bean should exist")
        void rabbitMQConfig_ShouldExist() {
            RabbitMQConfig config = applicationContext.getBean(RabbitMQConfig.class);
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("Login queue should be configured correctly")
        void loginQueue_ShouldBeConfigured() {
            Queue queue = applicationContext.getBean(Queue.class);
            assertThat(queue).isNotNull();
            assertThat(queue.getName()).isEqualTo("login-events");
            assertThat(queue.isDurable()).isTrue();
        }

        @Test
        @DisplayName("Message converter should be Jackson2JsonMessageConverter")
        void messageConverter_ShouldBeJackson() {
            MessageConverter converter = applicationContext.getBean(MessageConverter.class);
            assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
        }

        @Test
        @DisplayName("RabbitTemplate should have JSON converter")
        void rabbitTemplate_ShouldHaveJsonConverter() {
            RabbitTemplate template = applicationContext.getBean(RabbitTemplate.class);
            assertThat(template).isNotNull();
            assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
        }
    }

    @Nested
    @DisplayName("WebSocket Configuration Tests")
    class WebSocketTests {

        @Test
        @DisplayName("WebSocketConfig bean should exist")
        void webSocketConfig_ShouldExist() {
            WebSocketConfig config = applicationContext.getBean(WebSocketConfig.class);
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("WebSocketConfig should implement WebSocketMessageBrokerConfigurer")
        void webSocketConfig_ShouldImplementConfigurer() {
            WebSocketConfig config = applicationContext.getBean(WebSocketConfig.class);
            assertThat(config).isInstanceOf(org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer.class);
        }
    }

    @Nested
    @DisplayName("RestTemplate Configuration Tests")
    class RestTemplateTests {

        @Test
        @DisplayName("RestTemplateConfig bean should exist")
        void restTemplateConfig_ShouldExist() {
            RestTemplateConfig config = applicationContext.getBean(RestTemplateConfig.class);
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("RestTemplate bean should be created")
        void restTemplate_ShouldBeCreated() {
            RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);
            assertThat(restTemplate).isNotNull();
            assertThat(restTemplate).isInstanceOf(RestTemplate.class);
        }
    }
}