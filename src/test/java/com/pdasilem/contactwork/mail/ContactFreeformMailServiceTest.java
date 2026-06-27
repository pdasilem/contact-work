package com.pdasilem.contactwork.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.ai.MessageVectorIndexer;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.conversation.MailboxDirection;
import com.pdasilem.contactwork.conversation.MailboxFolder;
import com.pdasilem.contactwork.conversation.MailboxMessage;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContactFreeformMailServiceTest {
    @Mock
    private ProjectService projectService;
    @Mock
    private ContactService contactService;
    @Mock
    private MailTransportRouter mailTransportRouter;
    @Mock
    private MailTransport mailTransport;
    @Mock
    private GmailImapService gmailImapService;
    @Mock
    private ContactMessageService contactMessageService;
    @Mock
    private MailboxMessageRepository mailboxMessageRepository;
    @Mock
    private MessageVectorIndexer messageVectorIndexer;

    @Test
    void sendNewSendsPlainMessageAndStoresMailboxHistory() {
        Project project = project();
        Contact contact = contact(project);
        when(projectService.getProject(project.getId())).thenReturn(project);
        when(contactService.getContact(project.getId(), contact.getId())).thenReturn(contact);
        when(mailTransportRouter.resolve(project)).thenReturn(mailTransport);
        when(mailTransport.send(any(), any())).thenReturn(new MailSendResult("<freeform-message-id>"));
        when(mailboxMessageRepository.save(any(MailboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailboxMessage saved = service().sendNew(project.getId(), contact.getId(), "Subject", "Body");

        ArgumentCaptor<MailEnvelope> envelope = ArgumentCaptor.forClass(MailEnvelope.class);
        verify(mailTransport).send(eq(project), envelope.capture());
        assertThat(envelope.getValue().subject()).isEqualTo("Subject");
        assertThat(envelope.getValue().body()).isEqualTo("Body");
        assertThat(envelope.getValue().attachments()).isEmpty();
        assertThat(envelope.getValue().inReplyToMessageId()).isNull();

        assertThat(saved.getFolder()).isEqualTo(MailboxFolder.SENT);
        assertThat(saved.getDirection()).isEqualTo(MailboxDirection.SENT);
        assertThat(saved.getNormalizedMessageId()).isEqualTo("<freeform-message-id>");
        verify(contactMessageService).recordOutbound(
                project,
                contact,
                "<freeform-message-id>",
                null,
                "Subject",
                "Body",
                "sender@example.com",
                "contact@example.com",
                saved.getServiceDate()
        );
        verify(messageVectorIndexer).index(saved);
    }

    @Test
    void sendReplyUsesParentMessageIdForThreadingAndHistoryRelation() {
        Project project = project();
        Contact contact = contact(project);
        MailboxMessage parent = parentMessage(project, contact);
        when(projectService.getProject(project.getId())).thenReturn(project);
        when(contactService.getContact(project.getId(), contact.getId())).thenReturn(contact);
        when(mailboxMessageRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(mailTransportRouter.resolve(project)).thenReturn(mailTransport);
        when(mailTransport.send(any(), any())).thenReturn(new MailSendResult("<reply-message-id>"));
        when(mailboxMessageRepository.save(any(MailboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sendReply(project.getId(), contact.getId(), parent.getId(), "Re: Subject", "Reply body");

        ArgumentCaptor<MailEnvelope> envelope = ArgumentCaptor.forClass(MailEnvelope.class);
        verify(mailTransport).send(eq(project), envelope.capture());
        assertThat(envelope.getValue().inReplyToMessageId()).isEqualTo("<parent-message-id>");
        assertThat(envelope.getValue().referencesMessageIds()).containsExactly("<parent-message-id>");
        verify(contactMessageService).recordOutbound(
                any(),
                any(),
                eq("<reply-message-id>"),
                eq("<parent-message-id>"),
                eq("Re: Subject"),
                eq("Reply body"),
                eq("sender@example.com"),
                eq("contact@example.com"),
                any()
        );
    }

    @Test
    void rejectsBlankBody() {
        Project project = project();
        Contact contact = contact(project);
        when(projectService.getProject(project.getId())).thenReturn(project);
        when(contactService.getContact(project.getId(), contact.getId())).thenReturn(contact);

        assertThatThrownBy(() -> service().sendNew(project.getId(), contact.getId(), "Subject", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Body is required");
    }

    private ContactFreeformMailService service() {
        return new ContactFreeformMailService(
                projectService,
                contactService,
                mailTransportRouter,
                gmailImapService,
                contactMessageService,
                mailboxMessageRepository,
                messageVectorIndexer
        );
    }

    private Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setMailTransport(MailTransportType.GMAIL);
        project.setMailFrom("sender@example.com");
        return project;
    }

    private Contact contact(Project project) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setProject(project);
        contact.setEmail("contact@example.com");
        contact.setContactName("Contact");
        contact.setOrganizationName("Org");
        return contact;
    }

    private MailboxMessage parentMessage(Project project, Contact contact) {
        MailboxMessage message = new MailboxMessage();
        message.setId(UUID.randomUUID());
        message.setProject(project);
        message.setContact(contact);
        message.setNormalizedMessageId("<parent-message-id>");
        message.setSubject("Subject");
        return message;
    }
}
