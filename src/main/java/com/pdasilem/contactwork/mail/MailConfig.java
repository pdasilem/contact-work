package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.config.AppProperties;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;

    @Bean
    public JavaMailSender javaMailSender(AppProperties appProperties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(SMTP_HOST);
        sender.setPort(SMTP_PORT);
        sender.setUsername(appProperties.mail().gmail().username());
        sender.setPassword(appProperties.mail().gmail().appPassword());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "15000");
        properties.put("mail.smtp.timeout", "15000");
        properties.put("mail.smtp.writetimeout", "15000");
        return sender;
    }
}
