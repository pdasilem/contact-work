package com.pdasilem.contactwork.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdasilem.contactwork.project.Project;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;

class MailSyncStateTest {

    @Test
    void newStateReportsProjectIdAsPersistableIdAndIsNew() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        MailSyncState state = new MailSyncState();

        state.setProject(project);

        assertThat(state).isInstanceOf(Persistable.class);
        assertThat(state.getId()).isEqualTo(projectId);
        assertThat(state.getProjectId()).isEqualTo(projectId);
        assertThat(state.isNew()).isTrue();
    }

    @Test
    void persistedOrLoadedStateReportsNotNew() {
        MailSyncState state = new MailSyncState();

        state.markNotNew();

        assertThat(state.isNew()).isFalse();
    }
}
