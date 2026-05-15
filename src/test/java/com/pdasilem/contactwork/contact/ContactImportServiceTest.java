package com.pdasilem.contactwork.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.api.ImportContactsResponse;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ContactImportServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private ProjectService projectService;

    @Test
    void shouldImportOnlyNewValidContacts() {
        UUID projectId = Project.DEFAULT_PROJECT_ID;
        Project project = new Project();
        project.setId(projectId);
        project.setName("Default Project");
        ContactImportService service = new ContactImportService(contactRepository, projectService);
        AtomicBoolean existingSeen = new AtomicBoolean(false);

        when(projectService.getProject(projectId)).thenReturn(project);
        when(contactRepository.existsByProjectIdAndEmail(projectId, "existing@example.com")).thenReturn(true);
        when(contactRepository.existsByProjectIdAndEmail(projectId, "new@example.com")).thenReturn(false);
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String csv = "\uFEFFidx,organization,country,contact,email,notes\n"
                + "1,Org One,GE,Existing Person,existing@example.com,Existing row\n"
                + "2,Org Two,GE,New Person,new@example.com,New row\n"
                + "3,Org Three,GE,Bad Person,bad-email,Bad row\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contacts.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        ImportContactsResponse response = service.importContacts(projectId, file);

        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.inserted()).isEqualTo(1);
        assertThat(response.skippedExisting()).isEqualTo(1);
        assertThat(response.skippedInvalid()).isEqualTo(1);

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository, times(1)).save(captor.capture());
        Contact savedContact = captor.getValue();
        assertThat(savedContact.getOrganizationName()).isEqualTo("Org Two");
        assertThat(savedContact.getContactName()).isEqualTo("New Person");
        assertThat(savedContact.getEmail()).isEqualTo("new@example.com");
        assertThat(savedContact.getProject()).isEqualTo(project);
        assertThat(savedContact.getStatus()).isEqualTo(ContactStatus.NEW);
    }
}
