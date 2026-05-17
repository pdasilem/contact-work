package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.project.Project;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailSenderFactory implements MailSenderFactory {
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;

    @Override
    public JavaMailSenderImpl create(Project project) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(SMTP_HOST);
        sender.setPort(SMTP_PORT);
        sender.setUsername(project.getGmailUsername());
        sender.setPassword(project.getGmailAppPassword());
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
