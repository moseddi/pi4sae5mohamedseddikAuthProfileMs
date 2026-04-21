package tn.esprit.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private String testEmail;
    private String testResetLink;

    @BeforeEach
    void setUp() {
        testEmail = "student@test.com";
        testResetLink = "http://localhost:4200/reset-password?token=abc123";
    }

    @Test
    @DisplayName("Should send reset password email successfully")
    void sendResetLink_Success_ShouldSendEmail() {
        // Arrange
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        emailService.sendResetLink(testEmail, testResetLink);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals(testEmail, sentMessage.getTo()[0]);
        assertEquals("seddik202209@gmail.com", sentMessage.getFrom());
        assertTrue(sentMessage.getSubject().contains("Reset your Wall Street English password"));
        assertTrue(sentMessage.getText().contains(testResetLink));
    }

    @Test
    @DisplayName("Should handle email sending failure gracefully")
    void sendResetLink_EmailFails_ShouldNotThrowException() {
        // Arrange
        doThrow(new RuntimeException("SMTP server down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> emailService.sendResetLink(testEmail, testResetLink));

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}