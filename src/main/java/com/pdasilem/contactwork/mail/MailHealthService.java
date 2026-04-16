package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.inbox.InboxSyncService;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class MailHealthService {

    private final JavaMailSenderImpl javaMailSender;
    private final InboxSyncService inboxSyncService;

    public MailHealthService(JavaMailSender javaMailSender, InboxSyncService inboxSyncService) {
        if (!(javaMailSender instanceof JavaMailSenderImpl sender)) {
            throw new IllegalStateException("Expected JavaMailSenderImpl");
        }
        this.javaMailSender = sender;
        this.inboxSyncService = inboxSyncService;
    }

    public void verifyConnections() {
        try {
            javaMailSender.testConnection();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect to SMTP", ex);
        }
        inboxSyncService.verifyConnections();
    }
}
