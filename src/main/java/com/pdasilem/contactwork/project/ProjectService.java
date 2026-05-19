package com.pdasilem.contactwork.project;

import com.pdasilem.contactwork.auth.CurrentUserService;
import com.pdasilem.contactwork.config.AppProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    public static final String DEFAULT_AI_SYSTEM_PROMPT = """
            You are ContactWork AI, a read-only assistant for email outreach operations. You help users understand projects, contacts, mailbox history, replies, bounces, send failures, readiness, summaries, and next operational actions. Use available project, contact, mailbox, and saved summary data as the source of truth. Do not invent facts, counts, contacts, messages, credentials, or external claims. Never send emails, mutate contacts, change statuses, edit projects, or store credentials. If data is missing, say what is missing and what should be configured or synced next. Answer in the same language the user used. Keep answers concise, practical, and explicit about uncertainty.
            """.trim();
    private final ProjectRepository projectRepository;
    private final AppProperties appProperties;
    private final CurrentUserService currentUserService;

    public ProjectService(
            ProjectRepository projectRepository,
            AppProperties appProperties,
            CurrentUserService currentUserService
    ) {
        this.projectRepository = projectRepository;
        this.appProperties = appProperties;
        this.currentUserService = currentUserService;
    }

    public List<Project> findAll() {
        return currentUserService.filterVisibleProjects(projectRepository.findAllByOrderByCreatedAtAsc());
    }

    public List<Project> findAllForAdmin() {
        currentUserService.requireAdmin();
        return findAllUnrestricted();
    }

    public List<Project> findAllForSystem() {
        return findAllUnrestricted();
    }

    public Project getProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        currentUserService.requireProjectAccess(project);
        return project;
    }

    public Project getProjectForSystem(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    public boolean canCurrentUserAccess(UUID projectId) {
        return currentUserService.canAccessProjectId(projectId);
    }

    @Transactional
    public Project create(Project project) {
        currentUserService.requireAdmin();
        applyDefaults(project);
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(UUID projectId, Project updates) {
        Project project = getProject(projectId);
        if (updates.getStatus() != null) {
            project.setStatus(updates.getStatus());
        }
        if (updates.getMailTransport() != null) {
            project.setMailTransport(updates.getMailTransport());
        }
        if (updates.getLetterTemplate() != null) {
            project.setLetterTemplate(updates.getLetterTemplate());
        }
        if (updates.getMailSubject() != null) {
            project.setMailSubject(updates.getMailSubject());
        }
        if (updates.getMailBody() != null) {
            project.setMailBody(updates.getMailBody());
        }
        if (updates.getLetterAttachmentFilename() != null) {
            project.setLetterAttachmentFilename(updates.getLetterAttachmentFilename());
        }
        project.setMailFrom(updates.getMailFrom());
        project.setMailFromName(updates.getMailFromName());
        if (updates.getSendDelayMs() >= 0) {
            project.setSendDelayMs(updates.getSendDelayMs());
        }
        project.setMaxMessagesPerBatch(updates.getMaxMessagesPerBatch());
        if (updates.getInboxSyncCron() != null) {
            project.setInboxSyncCron(updates.getInboxSyncCron());
        }
        project.setGmailUsername(updates.getGmailUsername());
        project.setGmailAppPassword(updates.getGmailAppPassword());
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateSenderIdentity(
            UUID projectId,
            String mailFrom,
            String mailFromName
    ) {
        Project project = getProject(projectId);
        project.setMailFrom(mailFrom);
        project.setMailFromName(mailFromName);
        return projectRepository.save(project);
    }

    @Transactional
    public Project markMailSynced(UUID projectId, OffsetDateTime syncedAt) {
        Project project = getProject(projectId);
        project.setLastMailSyncAt(syncedAt);
        return projectRepository.save(project);
    }

    @Transactional
    public Project markMailSyncedForSystem(UUID projectId, OffsetDateTime syncedAt) {
        Project project = getProjectForSystem(projectId);
        project.setLastMailSyncAt(syncedAt);
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateAiSystemPrompt(UUID projectId, String aiSystemPrompt) {
        Project project = getProject(projectId);
        project.setAiSystemPrompt(resolveAiSystemPrompt(aiSystemPrompt));
        return projectRepository.save(project);
    }

    @Transactional
    public Project resetAiSystemPrompt(UUID projectId) {
        return updateAiSystemPrompt(projectId, DEFAULT_AI_SYSTEM_PROMPT);
    }

    public String aiSystemPrompt(Project project) {
        return resolveAiSystemPrompt(project == null ? null : project.getAiSystemPrompt());
    }

    private void applyDefaults(Project project) {
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.NEW);
        }
        if (project.getSendDelayMs() <= 0) {
            project.setSendDelayMs(appProperties.mail().sendDelayMs());
        }
        if (project.getInboxSyncCron() == null) {
            project.setInboxSyncCron(appProperties.mail().inboxSyncCron());
        }
        project.setAiSystemPrompt(resolveAiSystemPrompt(project.getAiSystemPrompt()));
    }

    private String resolveAiSystemPrompt(String aiSystemPrompt) {
        if (aiSystemPrompt == null || aiSystemPrompt.isBlank()) {
            return DEFAULT_AI_SYSTEM_PROMPT;
        }
        return aiSystemPrompt.trim();
    }

    private List<Project> findAllUnrestricted() {
        return projectRepository.findAllByOrderByCreatedAtAsc();
    }
}
