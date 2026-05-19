package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.project.Project;
import org.springframework.stereotype.Service;

@Service
public class MailTransportRouter {
    private final GmailSmtpMailTransport gmailTransport;
    private final BrevoApiMailTransport brevoTransport;

    public MailTransportRouter(
            GmailSmtpMailTransport gmailTransport,
            BrevoApiMailTransport brevoTransport
    ) {
        this.gmailTransport = gmailTransport;
        this.brevoTransport = brevoTransport;
    }

    public MailTransport resolve(Project project) {
        return switch (project.getMailTransport()) {
            case GMAIL -> gmailTransport;
            case BREVO -> brevoTransport;
        };
    }
}
