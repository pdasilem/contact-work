package com.pdasilem.contactwork.ui;

import com.pdasilem.contactwork.mail.MailHealthService;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import java.util.UUID;

@Route("app")
public class ProjectAdminView extends VerticalLayout {

    private final ProjectService projectService;
    private final MailHealthService mailHealthService;
    private final Grid<Project> grid = new Grid<>(Project.class, false);

    private Project selectedProject;
    private final TextField name = new TextField("Name");
    private final TextArea description = new TextArea("Description");
    private final Select<ProjectStatus> status = new Select<>();
    private final TextField letterTemplate = new TextField("Letter template");
    private final TextField pitchDeck = new TextField("Pitch deck");
    private final TextField mailSubject = new TextField("Subject");
    private final TextArea mailBody = new TextArea("Body");
    private final TextField letterAttachmentFilename = new TextField("Letter attachment filename");
    private final TextField pitchDeckAttachmentFilename = new TextField("Pitch deck attachment filename");
    private final TextField mailFrom = new TextField("Sender address");
    private final IntegerField sendDelayMs = new IntegerField("Send delay, ms");
    private final TextField inboxSyncCron = new TextField("Inbox sync cron");
    private final TextField gmailUsername = new TextField("Gmail username");
    private final PasswordField gmailAppPassword = new PasswordField("Gmail app password");

    public ProjectAdminView(ProjectService projectService, MailHealthService mailHealthService) {
        this.projectService = projectService;
        this.mailHealthService = mailHealthService;
        configureLayout();
        configureGrid();
        configureForm();
        refreshProjects();
    }

    private void configureLayout() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("project-admin-view");

        H1 title = new H1("ContactWork Projects");
        title.getStyle()
                .set("font-size", "28px")
                .set("margin", "0");
        Span subtitle = new Span("Project-scoped outreach settings, mailbox health, and campaign assets.");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout header = new VerticalLayout(title, subtitle);
        header.setPadding(true);
        header.setSpacing(false);
        header.setWidthFull();
        add(header);
    }

    private void configureGrid() {
        grid.addColumn(Project::getName).setHeader("Project").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(Project::getStatus).setHeader("Status").setAutoWidth(true);
        grid.addColumn(Project::getGmailUsername).setHeader("Mailbox").setAutoWidth(true);
        grid.addColumn(Project::getUpdatedAt).setHeader("Updated").setAutoWidth(true);
        grid.asSingleSelect().addValueChangeListener(event -> editProject(event.getValue()));
        grid.setHeightFull();
    }

    private void configureForm() {
        status.setLabel("Status");
        status.setItems(ProjectStatus.values());
        status.setValue(ProjectStatus.ACTIVE);
        sendDelayMs.setMin(0);
        sendDelayMs.setStep(500);

        name.setRequiredIndicatorVisible(true);
        letterTemplate.setRequiredIndicatorVisible(true);
        pitchDeck.setRequiredIndicatorVisible(true);
        mailSubject.setRequiredIndicatorVisible(true);
        mailBody.setRequiredIndicatorVisible(true);
        letterAttachmentFilename.setRequiredIndicatorVisible(true);
        pitchDeckAttachmentFilename.setRequiredIndicatorVisible(true);
        inboxSyncCron.setRequiredIndicatorVisible(true);

        Button newProject = new Button("New", event -> clearForm());
        Button save = new Button("Save", event -> saveProject());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button checkMailbox = new Button("Check Mailbox", event -> checkMailbox());

        HorizontalLayout actions = new HorizontalLayout(newProject, save, checkMailbox);
        actions.setWidthFull();

        VerticalLayout form = new VerticalLayout(
                name,
                description,
                status,
                letterTemplate,
                pitchDeck,
                mailSubject,
                mailBody,
                letterAttachmentFilename,
                pitchDeckAttachmentFilename,
                mailFrom,
                sendDelayMs,
                inboxSyncCron,
                gmailUsername,
                gmailAppPassword,
                actions
        );
        form.setWidth("420px");
        form.setPadding(true);
        form.getStyle().set("border-left", "1px solid var(--lumo-contrast-10pct)");

        HorizontalLayout body = new HorizontalLayout(grid, form);
        body.setSizeFull();
        body.setPadding(false);
        body.setSpacing(false);
        add(body);
        expand(body);
    }

    private void refreshProjects() {
        grid.setItems(projectService.findAll());
    }

    private void editProject(Project project) {
        selectedProject = project;
        if (project == null) {
            clearForm();
            return;
        }
        name.setValue(value(project.getName()));
        description.setValue(value(project.getDescription()));
        status.setValue(project.getStatus() == null ? ProjectStatus.ACTIVE : project.getStatus());
        letterTemplate.setValue(value(project.getLetterTemplate()));
        pitchDeck.setValue(value(project.getPitchDeck()));
        mailSubject.setValue(value(project.getMailSubject()));
        mailBody.setValue(value(project.getMailBody()));
        letterAttachmentFilename.setValue(value(project.getLetterAttachmentFilename()));
        pitchDeckAttachmentFilename.setValue(value(project.getPitchDeckAttachmentFilename()));
        mailFrom.setValue(value(project.getMailFrom()));
        sendDelayMs.setValue((int) Math.max(0, project.getSendDelayMs()));
        inboxSyncCron.setValue(value(project.getInboxSyncCron()));
        gmailUsername.setValue(value(project.getGmailUsername()));
        gmailAppPassword.clear();
    }

    private void clearForm() {
        selectedProject = null;
        name.clear();
        description.clear();
        status.setValue(ProjectStatus.ACTIVE);
        letterTemplate.clear();
        pitchDeck.clear();
        mailSubject.clear();
        mailBody.clear();
        letterAttachmentFilename.clear();
        pitchDeckAttachmentFilename.clear();
        mailFrom.clear();
        sendDelayMs.setValue(0);
        inboxSyncCron.clear();
        gmailUsername.clear();
        gmailAppPassword.clear();
        grid.deselectAll();
    }

    private void saveProject() {
        try {
            Project project = new Project();
            project.setName(required(name, "Name"));
            project.setDescription(blankToNull(description.getValue()));
            project.setStatus(status.getValue());
            project.setLetterTemplate(required(letterTemplate, "Letter template"));
            project.setPitchDeck(required(pitchDeck, "Pitch deck"));
            project.setMailSubject(required(mailSubject, "Subject"));
            project.setMailBody(required(mailBody, "Body"));
            project.setLetterAttachmentFilename(required(letterAttachmentFilename, "Letter attachment filename"));
            project.setPitchDeckAttachmentFilename(required(pitchDeckAttachmentFilename, "Pitch deck attachment filename"));
            project.setMailFrom(blankToNull(mailFrom.getValue()));
            project.setSendDelayMs(sendDelayMs.getValue() == null ? 0 : sendDelayMs.getValue());
            project.setInboxSyncCron(required(inboxSyncCron, "Inbox sync cron"));
            project.setGmailUsername(blankToNull(gmailUsername.getValue()));
            project.setGmailAppPassword(resolvePassword());

            Project saved = selectedProject == null
                    ? projectService.create(project)
                    : projectService.update(selectedProject.getId(), project);
            refreshProjects();
            grid.select(saved);
            Notification.show("Project saved", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private void checkMailbox() {
        if (selectedProject == null) {
            Notification.show("Select a project first", 2500, Position.BOTTOM_START);
            return;
        }
        try {
            UUID projectId = selectedProject.getId();
            mailHealthService.verifyConnections(projectId);
            Notification.show("Mailbox connection OK", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private String resolvePassword() {
        String password = blankToNull(gmailAppPassword.getValue());
        if (password != null || selectedProject == null) {
            return password;
        }
        return selectedProject.getGmailAppPassword();
    }

    private String required(TextField field, String label) {
        String value = blankToNull(field.getValue());
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private String required(TextArea field, String label) {
        String value = blankToNull(field.getValue());
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
