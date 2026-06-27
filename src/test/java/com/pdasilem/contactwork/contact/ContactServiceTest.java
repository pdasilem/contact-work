package com.pdasilem.contactwork.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.history.ContactMessageRepository;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @Mock
    private ContactCustomFieldRepository contactCustomFieldRepository;

    @Test
    void shouldCreateContactWithCustomFieldValues() {
        Project project = project();
        ContactService service = service();
        when(contactRepository.existsByProjectIdAndEmail(project.getId(), "new@example.com")).thenReturn(false);
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Contact contact = service.createContact(
                project,
                " Org ",
                " Person ",
                " New@Example.com ",
                " Manual note ",
                Map.of("country", "GE", "notes", "CSV note")
        );

        assertThat(contact.getOrganizationName()).isEqualTo("Org");
        assertThat(contact.getContactName()).isEqualTo("Person");
        assertThat(contact.getEmail()).isEqualTo("new@example.com");
        assertThat(contact.getNote()).isEqualTo("Manual note");
        assertThat(contact.getStatus()).isEqualTo(ContactStatus.NEW);

        ArgumentCaptor<ContactCustomField> customFieldCaptor = ArgumentCaptor.forClass(ContactCustomField.class);
        verify(contactCustomFieldRepository, org.mockito.Mockito.times(2)).save(customFieldCaptor.capture());
        assertThat(customFieldCaptor.getAllValues())
                .anySatisfy(field -> {
                    assertThat(field.getFieldKey()).isEqualTo("country");
                    assertThat(field.getFieldValue()).isEqualTo("GE");
                    assertThat(field.getContact()).isEqualTo(contact);
                })
                .anySatisfy(field -> {
                    assertThat(field.getFieldKey()).isEqualTo("notes");
                    assertThat(field.getFieldValue()).isEqualTo("CSV note");
                    assertThat(field.getContact()).isEqualTo(contact);
                });
    }

    @Test
    void shouldUpdateEditableFieldsAndStatus() {
        Project project = project();
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setProject(project);
        contact.setOrganizationName("Original Org");
        contact.setContactName("Old Name");
        contact.setEmail("old@example.com");
        contact.setNote("Old note");
        contact.setStatus(ContactStatus.NEW);

        ContactService service = service();
        when(contactRepository.findByProjectIdAndId(project.getId(), contact.getId())).thenReturn(Optional.of(contact));
        when(contactRepository.findByProjectIdAndEmail(project.getId(), "new@example.com")).thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Contact updated = service.updateEditableFields(
                project.getId(),
                contact.getId(),
                "New Name",
                "new@example.com",
                ContactStatus.SEND_FAILED,
                "New note"
        );

        assertThat(updated.getOrganizationName()).isEqualTo("Original Org");
        assertThat(updated.getContactName()).isEqualTo("New Name");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getStatus()).isEqualTo(ContactStatus.SEND_FAILED);
        assertThat(updated.getNote()).isEqualTo("New note");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void searchContactsReturnsRepositoryResultsAndFiltersDeletedContacts() {
        Project project = project();
        Contact active = contact(project);
        ContactService service = service();
        when(contactRepository.findAll(any(Specification.class))).thenReturn(List.of(active));

        List<Contact> contacts = service.searchContacts(project.getId(), "");

        assertThat(contacts).containsExactly(active);
        ArgumentCaptor<Specification<Contact>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(contactRepository).findAll(specificationCaptor.capture());

        Root<Contact> root = org.mockito.Mockito.mock(Root.class);
        CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = org.mockito.Mockito.mock(CriteriaBuilder.class);
        Path<Object> projectPath = org.mockito.Mockito.mock(Path.class);
        Path<Object> projectIdPath = org.mockito.Mockito.mock(Path.class);
        Path<Object> deletedAtPath = org.mockito.Mockito.mock(Path.class);
        Predicate projectPredicate = org.mockito.Mockito.mock(Predicate.class);
        Predicate activePredicate = org.mockito.Mockito.mock(Predicate.class);

        when(root.get("project")).thenReturn((Path) projectPath);
        when(projectPath.get("id")).thenReturn((Path) projectIdPath);
        when(root.get("deletedAt")).thenReturn((Path) deletedAtPath);
        when(criteriaBuilder.equal(projectIdPath, project.getId())).thenReturn(projectPredicate);
        when(criteriaBuilder.isNull(deletedAtPath)).thenReturn(activePredicate);

        specificationCaptor.getValue().toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).isNull(deletedAtPath);
    }

    @Test
    void shouldHardDeleteUnusedContactAndLetCustomFieldsCascade() {
        Project project = project();
        Contact contact = contact(project);
        ContactService service = service();
        when(contactRepository.findByProjectIdAndId(project.getId(), contact.getId())).thenReturn(Optional.of(contact));
        when(contactMessageRepository.findByProjectIdAndContactIdOrderByMessageTimestampAsc(project.getId(), contact.getId()))
                .thenReturn(List.of());

        service.deleteContact(project.getId(), contact.getId());

        verify(contactRepository).delete(contact);
        verify(contactCustomFieldRepository, never()).delete(any());
    }

    @Test
    void shouldSoftDeleteUsedContactAndKeepCustomFields() {
        Project project = project();
        Contact contact = contact(project);
        contact.setOutboundMessageId("message-id");
        ContactService service = service();
        when(contactRepository.findByProjectIdAndId(project.getId(), contact.getId())).thenReturn(Optional.of(contact));
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteContact(project.getId(), contact.getId());

        assertThat(contact.getDeletedAt()).isNotNull();
        verify(contactRepository).save(contact);
        verify(contactCustomFieldRepository, never()).delete(any());
    }

    private ContactService service() {
        ProjectService projectService = org.mockito.Mockito.mock(ProjectService.class);
        org.mockito.Mockito.lenient()
                .when(projectService.getProject(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenReturn(project());
        return new ContactService(contactRepository, contactMessageRepository, contactCustomFieldRepository, projectService);
    }

    private Project project() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Default Project");
        return project;
    }

    private Contact contact(Project project) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setProject(project);
        contact.setOrganizationName("Org");
        contact.setContactName("Name");
        contact.setEmail("person@example.com");
        contact.setStatus(ContactStatus.NEW);
        return contact;
    }
}
