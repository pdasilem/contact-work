package com.pdasilem.contactwork.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import com.pdasilem.contactwork.project.asset.ProjectAsset;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import com.pdasilem.contactwork.project.asset.ProjectAssetType;
import com.pdasilem.contactwork.template.GeneratedLetter;
import com.pdasilem.contactwork.template.TemplateService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SendCoordinatorTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private TemplateService templateService;

    @Mock
    private OutboundMailService outboundMailService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectAssetService projectAssetService;

    @Test
    void batchWithUnlimitedSendsAllNewContacts() {
        Project project = activeProject(null);
        Contact first = contactWithoutReadableProject(ContactStatus.NEW);
        Contact second = contactWithoutReadableProject(ContactStatus.NEW);
        SendCoordinator coordinator = coordinator();
        stubSendReady(project);
        stubNoInProgress(project);
        when(contactRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(project.getId(), ContactStatus.NEW))
                .thenReturn(List.of(first, second));
        when(contactRepository.findByProjectIdAndId(project.getId(), first.getId())).thenReturn(Optional.of(first));
        when(contactRepository.findByProjectIdAndId(project.getId(), second.getId())).thenReturn(Optional.of(second));

        coordinator.start(project.getId());

        verify(outboundMailService, times(2)).send(any(), any(), any(), any());
        assertThat(first.getStatus()).isEqualTo(ContactStatus.SENT);
        assertThat(second.getStatus()).isEqualTo(ContactStatus.SENT);
    }

    @Test
    void batchWithMaxSendsOnlyFirstContactsByCreatedOrder() {
        Project project = activeProject(2);
        Contact first = contactWithoutReadableProject(ContactStatus.NEW);
        Contact second = contactWithoutReadableProject(ContactStatus.NEW);
        Contact third = contactWithoutReadableProject(ContactStatus.NEW);
        SendCoordinator coordinator = coordinator();
        stubSendReady(project);
        stubNoInProgress(project);
        when(contactRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(project.getId(), ContactStatus.NEW))
                .thenReturn(List.of(first, second, third));
        when(contactRepository.findByProjectIdAndId(project.getId(), first.getId())).thenReturn(Optional.of(first));
        when(contactRepository.findByProjectIdAndId(project.getId(), second.getId())).thenReturn(Optional.of(second));

        coordinator.start(project.getId());

        verify(outboundMailService, times(2)).send(any(), any(), any(), any());
        verify(contactRepository, never()).findByProjectIdAndId(project.getId(), third.getId());
        assertThat(first.getStatus()).isEqualTo(ContactStatus.SENT);
        assertThat(second.getStatus()).isEqualTo(ContactStatus.SENT);
        assertThat(third.getStatus()).isEqualTo(ContactStatus.NEW);
    }

    @Test
    void statusEligibleBatchCountHonorsConfiguredCap() {
        Project project = activeProject(2);
        SendCoordinator coordinator = coordinator();
        when(projectService.getProject(project.getId())).thenReturn(project);
        stubNoInProgress(project);
        when(contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(project.getId(), ContactStatus.NEW)).thenReturn(5L);

        var status = coordinator.getStatus(project.getId());

        assertThat(status.newCount()).isEqualTo(5);
        assertThat(status.eligibleBatchCount()).isEqualTo(2);
    }

    @Test
    void normalSingleSendSkipsNonEligibleStatus() {
        Project project = activeProject(null);
        Contact contact = contact(project, ContactStatus.SENT);
        SendCoordinator coordinator = coordinator();
        stubReadinessOnly(project);
        when(contactRepository.findByProjectIdAndId(project.getId(), contact.getId())).thenReturn(Optional.of(contact));

        coordinator.sendSingle(project.getId(), contact.getId(), false);

        verify(outboundMailService, never()).send(any(), any(), any(), any());
        assertThat(contact.getStatus()).isEqualTo(ContactStatus.SENT);
    }

    @Test
    void forcedSingleSendSendsNonEligibleStatus() {
        Project project = activeProject(null);
        Contact contact = contactWithoutReadableProject(ContactStatus.REPLIED);
        SendCoordinator coordinator = coordinator();
        stubSendReady(project);
        when(contactRepository.findByProjectIdAndId(project.getId(), contact.getId())).thenReturn(Optional.of(contact));

        coordinator.sendSingle(project.getId(), contact.getId(), true);

        verify(outboundMailService).send(project, contact, generatedLetter(), List.of());
        assertThat(contact.getStatus()).isEqualTo(ContactStatus.SENT);
    }

    @Test
    void sendFailureMarksContactFailed() {
        Project project = activeProject(null);
        Contact contact = contactWithoutReadableProject(ContactStatus.NEW);
        SendCoordinator coordinator = coordinator();
        stubReadinessOnly(project);
        when(contactRepository.findByProjectIdAndId(project.getId(), contact.getId())).thenReturn(Optional.of(contact));
        when(projectAssetService.activeLetterResource(project.getId())).thenReturn(new ByteArrayResource(new byte[0]));
        when(projectAssetService.activeMailAttachments(project.getId())).thenReturn(List.of());
        when(templateService.generateLetterPdf(any(), any(), any())).thenReturn(generatedLetter());
        when(outboundMailService.send(any(), any(), any(), any())).thenThrow(new IllegalStateException("smtp failed"));

        coordinator.sendSingle(project.getId(), contact.getId(), false);

        assertThat(contact.getStatus()).isEqualTo(ContactStatus.SEND_FAILED);
        assertThat(contact.getLastErrorMessage()).isEqualTo("smtp failed");
    }

    @Test
    void recoverInProgressWithSentMarkersMarksSent() {
        Project project = activeProject(null);
        Contact contact = contact(project, ContactStatus.IN_PROGRESS);
        contact.setOutboundMessageId("<message-id>");
        contact.setSentAt(java.time.OffsetDateTime.now());
        ContactSendProcessor processor = processor();
        when(contactRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(project.getId(), ContactStatus.IN_PROGRESS))
                .thenReturn(List.of(contact));

        int recovered = processor.recoverStuckInProgress(project.getId());

        assertThat(recovered).isEqualTo(1);
        assertThat(contact.getStatus()).isEqualTo(ContactStatus.SENT);
    }

    @Test
    void recoverStaleInProgressWithoutSentMarkersMarksFailed() {
        Project project = activeProject(null);
        Contact contact = contact(project, ContactStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(contact, "updatedAt", java.time.OffsetDateTime.now().minusMinutes(20));
        ContactSendProcessor processor = processor();
        when(contactRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(project.getId(), ContactStatus.IN_PROGRESS))
                .thenReturn(List.of(contact));

        int recovered = processor.recoverStuckInProgress(project.getId());

        assertThat(recovered).isEqualTo(1);
        assertThat(contact.getStatus()).isEqualTo(ContactStatus.SEND_FAILED);
        assertThat(contact.getLastErrorMessage()).contains("IN_PROGRESS");
    }

    @Test
    void coordinatorHasNoTransactionalMethods() {
        assertThat(SendCoordinator.class.getDeclaredMethods())
                .noneSatisfy(method -> assertThat(method.isAnnotationPresent(Transactional.class)).isTrue());
    }

    private SendCoordinator coordinator() {
        return new SendCoordinator(
                contactRepository,
                projectService,
                projectAssetService,
                processor(),
                Runnable::run
        );
    }

    private ContactSendProcessor processor() {
        return new ContactSendProcessor(
                contactRepository,
                templateService,
                outboundMailService,
                projectService,
                projectAssetService
        );
    }

    private void stubSendReady(Project project) {
        stubReadinessOnly(project);
        when(projectAssetService.activeLetterResource(project.getId())).thenReturn(new ByteArrayResource(new byte[0]));
        when(projectAssetService.activeMailAttachments(project.getId())).thenReturn(List.of());
        when(templateService.generateLetterPdf(any(), any(), any())).thenReturn(generatedLetter());
        when(outboundMailService.send(any(), any(), any(), any())).thenReturn("message-id");
    }

    private void stubReadinessOnly(Project project) {
        ProjectAsset asset = new ProjectAsset();
        asset.setId(UUID.randomUUID());
        asset.setProject(project);
        asset.setType(ProjectAssetType.LETTER_TEMPLATE);
        asset.setOriginalFilename("letter.docx");
        asset.setStoredPath("/tmp/letter.docx");
        when(projectService.getProject(project.getId())).thenReturn(project);
        when(projectAssetService.activeLetter(project.getId())).thenReturn(Optional.of(asset));
    }

    private void stubNoInProgress(Project project) {
        when(contactRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                project.getId(),
                ContactStatus.IN_PROGRESS
        )).thenReturn(List.of());
    }

    private GeneratedLetter generatedLetter() {
        return new GeneratedLetter(Path.of("/tmp/letter.docx"), Path.of("/tmp/letter.pdf"));
    }

    private Project activeProject(Integer maxMessagesPerBatch) {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Project");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setMailSubject("Subject");
        project.setMailBody("Body");
        project.setGmailUsername("user@example.com");
        project.setGmailAppPassword("password");
        project.setSendDelayMs(0);
        project.setInboxSyncCron("0 */5 * * * *");
        project.setMaxMessagesPerBatch(maxMessagesPerBatch);
        return project;
    }

    private Contact contact(Project project, ContactStatus status) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setProject(project);
        contact.setOrganizationName("Org");
        contact.setContactName("Person");
        contact.setEmail(contact.getId() + "@example.com");
        contact.setStatus(status);
        return contact;
    }

    private Contact contactWithoutReadableProject(ContactStatus status) {
        Contact contact = new Contact() {
            @Override
            public Project getProject() {
                throw new AssertionError("send path must not read Contact.project");
            }
        };
        contact.setId(UUID.randomUUID());
        contact.setOrganizationName("Org");
        contact.setContactName("Person");
        contact.setEmail(contact.getId() + "@example.com");
        contact.setStatus(status);
        return contact;
    }
}
