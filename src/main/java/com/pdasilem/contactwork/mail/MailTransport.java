package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.project.Project;

public interface MailTransport {
    MailSendResult send(Project project, MailEnvelope envelope);

    void verifyConnection(Project project);
}
