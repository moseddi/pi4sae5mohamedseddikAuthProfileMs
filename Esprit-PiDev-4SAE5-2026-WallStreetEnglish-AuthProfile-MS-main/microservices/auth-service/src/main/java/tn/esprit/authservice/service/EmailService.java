package tn.esprit.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendResetLink(String to, String resetLink) {
        try {
            log.info("📧 Préparation d'envoi d'email à: {}", to);
            log.info("🔗 Lien: {}", resetLink);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("seddik202209@gmail.com");
            message.setTo(to);
            message.setSubject("🔐 Reset your Wall Street English password");
            message.setText(
                    "Hello,\n\n" +
                            "You requested to reset your password.\n\n" +
                            "Click this link to reset your password: " + resetLink + "\n\n" +
                            "This link will expire in 24 hours.\n\n" +
                            "If you didn't request this, ignore this email.\n\n" +
                            "Wall Street English Team"
            );

            mailSender.send(message);
            log.info("✅ Email envoyé avec succès à: {}", to);

        } catch (Exception e) {
            log.error("❌ ÉCHEC d'envoi d'email à {}: {}", to, e.getMessage());
            e.printStackTrace(); // Pour voir l'erreur complète
        }
    }
}