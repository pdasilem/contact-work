package com.pdasilem.contactwork.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.project.Project;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContactMessageServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @Test
    void recordOutboundStoresExplicitProjectWithoutReadingContactProject() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        Contact contact = new Contact() {
            @Override
            public Project getProject() {
                throw new AssertionError("outbound history must not read Contact.project");
            }
        };
        contact.setId(UUID.randomUUID());
        OffsetDateTime timestamp = OffsetDateTime.now();
        when(contactMessageRepository.save(any(ContactMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactMessage message = new ContactMessageService(contactMessageRepository).recordOutbound(
                project,
                contact,
                "message-id",
                "Subject",
                "Body",
                "sender@example.com",
                "receiver@example.com",
                timestamp
        );

        assertThat(message.getProject()).isSameAs(project);
        assertThat(message.getContact()).isSameAs(contact);
        assertThat(message.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(message.getEventType()).isEqualTo(MessageEventType.EMAIL);
        assertThat(message.getMessageTimestamp()).isEqualTo(timestamp);
    }
}
