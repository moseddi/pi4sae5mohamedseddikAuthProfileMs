package tn.esprit.usermanagementservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RabbitMQConfig Tests")
class RabbitMQConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @Test
    @DisplayName("Should create loginQueue bean")
    void loginQueue_ShouldBeCreated() {
        Queue queue = applicationContext.getBean(Queue.class);

        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo(RabbitMQConfig.LOGIN_QUEUE);
        assertThat(queue.isDurable()).isTrue();
    }

    @Test
    @DisplayName("Should create jsonMessageConverter bean")
    void jsonMessageConverter_ShouldBeCreated() {
        MessageConverter converter = applicationContext.getBean(MessageConverter.class);

        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    @DisplayName("Should create rabbitTemplate bean with JSON converter")
    void rabbitTemplate_ShouldBeCreated() {
        RabbitTemplate template = applicationContext.getBean(RabbitTemplate.class);

        assertThat(template).isNotNull();
        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}