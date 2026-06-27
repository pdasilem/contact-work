package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.project.Project;
import jakarta.activation.DataSource;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class GmailImapService {
    private static final Logger log = LoggerFactory.getLogger(GmailImapService.class);
    private static final String IMAP_HOST = "imap.gmail.com";
    private static final int IMAP_PORT = 993;

    private final AppProperties appProperties;

    public GmailImapService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Store createImapStore(Project project) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", IMAP_HOST);
        properties.put("mail.imaps.port", String.valueOf(IMAP_PORT));
        properties.put("mail.imaps.ssl.enable", "true");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imaps");
        store.connect(IMAP_HOST, project.getGmailUsername(), project.getGmailAppPassword());
        return store;
    }

    public void appendToSentFolder(Project project, MailEnvelope envelope, String messageId) {
        if (project.getGmailUsername() == null || project.getGmailUsername().isBlank()
                || project.getGmailAppPassword() == null || project.getGmailAppPassword().isBlank()) {
            log.debug("Gmail credentials not configured, skipping sent folder append");
            return;
        }

        Store store = null;
        Folder sentFolder = null;
        try {
            store = createImapStore(project);
            String folderName = resolveSentFolderName();
            sentFolder = store.getFolder(folderName);
            sentFolder.open(Folder.READ_WRITE);

            MimeMessage message = buildMimeMessage(envelope, messageId);
            message.setFlag(Flags.Flag.SEEN, true);
            sentFolder.appendMessages(new Message[]{message});

            log.info("Saved sent copy to Gmail folder '{}' for {}", folderName, envelope.toEmail());
        } catch (Exception ex) {
            log.warn("Failed to save sent copy to Gmail for {}: {}", envelope.toEmail(), ex.getMessage(), ex);
        } finally {
            closeQuietly(sentFolder);
            closeQuietly(store);
        }
    }

    private MimeMessage buildMimeMessage(MailEnvelope envelope, String messageId) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(envelope.toEmail());
        setFrom(helper, envelope);
        helper.setSubject(envelope.subject() != null ? envelope.subject() : "");
        helper.setText(envelope.body() != null ? envelope.body() : "", false);

        for (MailFileAttachment attachment : envelope.attachments()) {
            DataSource dataSource = new ByteArrayDataSource(attachment.content(), "application/octet-stream");
            helper.addAttachment(attachment.filename(), dataSource);
        }

        GmailSmtpMailTransport.applyThreadHeaders(message, envelope);
        message.saveChanges();
        if (messageId != null && !messageId.isBlank()) {
            message.setHeader("Message-ID", messageId);
        }
        return message;
    }

    private void setFrom(MimeMessageHelper helper, MailEnvelope envelope)
            throws jakarta.mail.MessagingException, UnsupportedEncodingException {
        if (envelope.fromEmail() == null || envelope.fromEmail().isBlank()) {
            return;
        }
        if (envelope.fromName() != null && !envelope.fromName().isBlank()) {
            helper.setFrom(new InternetAddress(envelope.fromEmail(), envelope.fromName(), "UTF-8"));
        } else {
            helper.setFrom(envelope.fromEmail());
        }
    }

    private String resolveSentFolderName() {
        if (appProperties.mail().gmail() != null) {
            String folder = appProperties.mail().gmail().sentFolder();
            if (folder != null && !folder.isBlank()) {
                return folder;
            }
        }
        return "[Gmail]/Sent Mail";
    }

    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (Exception ignored) {
            }
        }
    }

    private void closeQuietly(Store store) {
        if (store != null) {
            try {
                store.close();
            } catch (Exception ignored) {
            }
        }
    }
}
