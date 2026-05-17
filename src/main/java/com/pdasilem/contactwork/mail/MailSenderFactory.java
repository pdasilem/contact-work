package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.project.Project;
import org.springframework.mail.javamail.JavaMailSenderImpl;

public interface MailSenderFactory {
    JavaMailSenderImpl create(Project project);
}
