package com.pdasilem.contactwork.ui;

import com.pdasilem.contactwork.api.ImportContactsResponse;
import com.pdasilem.contactwork.api.SendStatusResponse;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactImportService;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.mail.MailHealthService;
import com.pdasilem.contactwork.mail.SendCoordinator;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.router.Route;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Route("app")
@CssImport("./styles/contactwork-app.css")
public class ProjectAdminView extends Composite<Div> {

    private final ProjectService projectService;
    private final ContactService contactService;
    private final ContactImportService contactImportService;
    private final SendCoordinator sendCoordinator;
    private final MailHealthService mailHealthService;
    private final InboxSyncService inboxSyncService;

    private final VerticalLayout projectList = new VerticalLayout();
    private final Main workspace = new Main();
    private final Div systemDrawer = new Div();
    private final Span selectedProjectName = new Span("No project selected");

    private Project selectedProject;
    private Grid<Contact> contactsGrid;

    private final TextField projectName = new TextField("Project name");
    private final TextArea projectDescription = new TextArea("Description");
    private final Select<ProjectStatus> projectStatus = new Select<>();
    private final TextField letterTemplate = new TextField("Letter template");
    private final TextField pitchDeck = new TextField("Pitch deck");
    private final TextField mailSubject = new TextField("Email subject");
    private final TextArea mailBody = new TextArea("Email body");
    private final TextField letterAttachmentFilename = new TextField("Letter attachment name");
    private final TextField pitchDeckAttachmentFilename = new TextField("Pitch deck attachment name");
    private final TextField mailFrom = new TextField("Sender address");
    private final IntegerField sendDelayMs = new IntegerField("Send delay, ms");
    private final TextField inboxSyncCron = new TextField("Inbox sync cron");
    private final TextField gmailUsername = new TextField("Gmail username");
    private final PasswordField gmailAppPassword = new PasswordField("Gmail app password");

    public ProjectAdminView(
            ProjectService projectService,
            ContactService contactService,
            ContactImportService contactImportService,
            SendCoordinator sendCoordinator,
            MailHealthService mailHealthService,
            InboxSyncService inboxSyncService
    ) {
        this.projectService = projectService;
        this.contactService = contactService;
        this.contactImportService = contactImportService;
        this.sendCoordinator = sendCoordinator;
        this.mailHealthService = mailHealthService;
        this.inboxSyncService = inboxSyncService;
        buildShell();
        renderWelcome();
        refreshProjectList();
    }

    private void buildShell() {
        Div root = getContent();
        root.addClassName("cw-shell");

        Div sidebar = new Div();
        sidebar.addClassName("cw-sidebar");
        sidebar.add(sidebarHeader(), projectList, newProjectButton());

        workspace.addClassName("cw-workspace");
        systemDrawer.addClassName("cw-system-drawer");

        root.add(sidebar, workspace, systemDrawer);
    }

    private VerticalLayout sidebarHeader() {
        H1 title = new H1("ContactWork");
        title.addClassName("cw-logo");
        Span label = new Span("Projects");
        label.addClassName("cw-section-label");
        VerticalLayout header = new VerticalLayout(title, label);
        header.setPadding(false);
        header.setSpacing(false);
        return header;
    }

    private Button newProjectButton() {
        Button button = new Button("New project", VaadinIcon.PLUS.create(), event -> openNewProjectDialog());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClassName("cw-sidebar-action");
        return button;
    }

    private void refreshProjectList() {
        projectList.removeAll();
        projectList.setPadding(false);
        projectList.setSpacing(false);
        projectList.addClassName("cw-project-list");
        for (Project project : projectService.findAll()) {
            Button button = new Button(project.getName(), new Icon(VaadinIcon.FOLDER_OPEN), event -> selectProject(project.getId()));
            button.addClassName("cw-project-button");
            if (selectedProject != null && selectedProject.getId().equals(project.getId())) {
                button.addClassName("selected");
            }
            projectList.add(button);
        }
    }

    private void renderWelcome() {
        workspace.removeAll();
        systemDrawer.removeAll();
        systemDrawer.removeClassName("open");
        selectedProject = null;

        Div empty = new Div();
        empty.addClassName("cw-empty-state");
        H2 title = new H2("Select a project");
        Span text = new Span("Choose a project on the left to see monitoring, contacts, campaign setup, and system settings.");
        Button create = new Button("Create project", VaadinIcon.PLUS.create(), event -> openNewProjectDialog());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        empty.add(title, text, create);
        workspace.add(empty);
    }

    private void selectProject(UUID projectId) {
        selectedProject = projectService.getProject(projectId);
        refreshProjectList();
        renderWorkspace();
    }

    private void renderWorkspace() {
        workspace.removeAll();
        systemDrawer.removeAll();
        systemDrawer.removeClassName("open");
        selectedProjectName.setText(selectedProject.getName());

        workspace.add(topBar(), monitoring(), contactsSection(), setupSection());
        buildSystemDrawer();
        loadProjectSettings(selectedProject);
        refreshContacts();
    }

    private HorizontalLayout topBar() {
        H2 title = new H2(selectedProject.getName());
        title.addClassName("cw-page-title");
        Span status = pill(selectedProject.getStatus().name(), "neutral");
        Button settings = new Button("System", VaadinIcon.COG.create(), event -> toggleSystemDrawer());
        Button refresh = new Button("Refresh", VaadinIcon.REFRESH.create(), event -> renderWorkspace());

        HorizontalLayout left = new HorizontalLayout(title, status);
        left.setAlignItems(Alignment.CENTER);
        left.setSpacing(true);

        HorizontalLayout actions = new HorizontalLayout(refresh, settings);
        actions.setAlignItems(Alignment.CENTER);

        HorizontalLayout bar = new HorizontalLayout(left, actions);
        bar.addClassName("cw-topbar");
        bar.setWidthFull();
        bar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        return bar;
    }

    private Div monitoring() {
        SendStatusResponse status = sendCoordinator.getStatus(selectedProject.getId());
        Div cards = new Div();
        cards.addClassName("cw-monitoring");
        cards.add(
                metric("New", status.newCount(), "Contacts waiting"),
                metric("Sent", status.sentCount(), "SMTP accepted"),
                metric("Replied", status.repliedCount(), "Inbound replies"),
                metric("Bounced", status.bouncedCount(), "Delivery failures"),
                metric("Failed", status.sendFailedCount(), "Send errors"),
                metric(status.running() ? "Running" : "Idle", status.eligibleBatchCount(), "Batch state")
        );
        return cards;
    }

    private Div metric(String label, long value, String detail) {
        Div card = new Div();
        card.addClassName("cw-metric");
        Span labelText = new Span(label);
        labelText.addClassName("cw-metric-label");
        Span valueText = new Span(String.valueOf(value));
        valueText.addClassName("cw-metric-value");
        Span detailText = new Span(detail);
        detailText.addClassName("cw-metric-detail");
        card.add(labelText, valueText, detailText);
        return card;
    }

    private Div metric(String label, String value, String detail) {
        Div card = new Div();
        card.addClassName("cw-metric");
        Span labelText = new Span(label);
        labelText.addClassName("cw-metric-label");
        Span valueText = new Span(value);
        valueText.addClassName("cw-metric-value");
        Span detailText = new Span(detail);
        detailText.addClassName("cw-metric-detail");
        card.add(labelText, valueText, detailText);
        return card;
    }

    private Div contactsSection() {
        Div section = section("Contacts", "Edit core contact data and run project send actions.");
        TextField filter = new TextField();
        filter.setPlaceholder("Filter by email or organization");
        filter.setPrefixComponent(VaadinIcon.SEARCH.create());
        filter.addValueChangeListener(event -> refreshContacts(event.getValue()));

        Button sync = new Button("Sync inbox", VaadinIcon.INBOX.create(), event -> runInboxSync());
        Button sendBatch = new Button("Start batch", VaadinIcon.PAPERPLANE.create(), event -> startBatch());
        Button edit = new Button("Edit selected", VaadinIcon.EDIT.create(), event -> editSelectedContact());

        HorizontalLayout toolbar = new HorizontalLayout(filter, edit, sync, sendBatch);
        toolbar.addClassName("cw-toolbar");
        toolbar.setWidthFull();
        toolbar.expand(filter);

        contactsGrid = new Grid<>(Contact.class, false);
        contactsGrid.addClassName("cw-contacts-grid");
        contactsGrid.addColumn(Contact::getContactName).setHeader("Contact").setAutoWidth(true).setFlexGrow(1);
        contactsGrid.addColumn(Contact::getEmail).setHeader("Email").setAutoWidth(true).setFlexGrow(1);
        contactsGrid.addColumn(Contact::getOrganizationName).setHeader("Organization").setAutoWidth(true).setFlexGrow(1);
        contactsGrid.addColumn(Contact::getCountry).setHeader("Country").setAutoWidth(true).setFlexGrow(0);
        contactsGrid.addColumn(Contact::getStatus).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        contactsGrid.addColumn(Contact::getSentAt).setHeader("Sent").setAutoWidth(true).setFlexGrow(0);
        contactsGrid.setHeight("340px");
        contactsGrid.asSingleSelect().addValueChangeListener(event -> edit.setEnabled(event.getValue() != null));
        contactsGrid.addItemDoubleClickListener(event -> openContactDialog(event.getItem()));
        edit.setEnabled(false);

        section.add(toolbar, contactsGrid);
        return section;
    }

    private Div setupSection() {
        Div section = section("Project setup", "Campaign text, templates, attachments, and contact import.");
        section.addClassName("cw-setup");

        configureProjectFields();
        Upload upload = contactsUpload();
        Button save = new Button("Save project setup", VaadinIcon.CHECK.create(), event -> saveProjectSettings(false));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Div form = new Div();
        form.addClassName("cw-setup-grid");
        form.add(projectName, projectDescription, letterTemplate, pitchDeck, mailSubject, mailBody,
                letterAttachmentFilename, pitchDeckAttachmentFilename, upload);

        section.add(form, save);
        return section;
    }

    private void configureProjectFields() {
        projectName.setRequiredIndicatorVisible(true);
        letterTemplate.setRequiredIndicatorVisible(true);
        pitchDeck.setRequiredIndicatorVisible(true);
        mailSubject.setRequiredIndicatorVisible(true);
        mailBody.setRequiredIndicatorVisible(true);
        letterAttachmentFilename.setRequiredIndicatorVisible(true);
        pitchDeckAttachmentFilename.setRequiredIndicatorVisible(true);
        mailBody.setMinHeight("150px");
        projectDescription.setMaxHeight("120px");
    }

    private Upload contactsUpload() {
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv", "text/csv");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span("Drop contacts CSV"));
        upload.addSucceededListener(event -> {
            try {
                ImportContactsResponse response = contactImportService.importContacts(
                        selectedProject.getId(),
                        event.getFileName(),
                        buffer.getInputStream()
                );
                refreshContacts();
                Notification.show("Imported " + response.inserted() + " contacts", 3000, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        });
        return upload;
    }

    private void buildSystemDrawer() {
        systemDrawer.removeAll();
        H3 title = new H3("System settings");
        Span helper = new Span("Mailbox credentials, sender identity, delays, and scheduler.");
        helper.addClassName("cw-muted");
        projectStatus.setLabel("Project status");
        projectStatus.setItems(ProjectStatus.values());
        sendDelayMs.setMin(0);
        sendDelayMs.setStep(500);
        inboxSyncCron.setRequiredIndicatorVisible(true);

        Button health = new Button("Check mailbox", VaadinIcon.CONNECT.create(), event -> checkMailbox());
        Button save = new Button("Save system settings", VaadinIcon.CHECK.create(), event -> saveProjectSettings(true));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button close = new Button("Close", VaadinIcon.CLOSE_SMALL.create(), event -> systemDrawer.removeClassName("open"));

        VerticalLayout content = new VerticalLayout(title, helper, projectStatus, mailFrom, gmailUsername,
                gmailAppPassword, sendDelayMs, inboxSyncCron, health, save, close);
        content.setPadding(false);
        content.setSpacing(true);
        systemDrawer.add(content);
    }

    private Div section(String title, String subtitle) {
        Div section = new Div();
        section.addClassName("cw-section");
        H3 heading = new H3(title);
        Span text = new Span(subtitle);
        text.addClassName("cw-muted");
        section.add(heading, text);
        return section;
    }

    private Span pill(String text, String tone) {
        Span pill = new Span(text);
        pill.addClassNames("cw-pill", "cw-pill-" + tone);
        return pill;
    }

    private void loadProjectSettings(Project project) {
        projectName.setValue(value(project.getName()));
        projectDescription.setValue(value(project.getDescription()));
        projectStatus.setValue(project.getStatus() == null ? ProjectStatus.ACTIVE : project.getStatus());
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

    private void saveProjectSettings(boolean systemOnly) {
        try {
            Project updates = new Project();
            updates.setName(required(projectName, "Project name"));
            updates.setDescription(blankToNull(projectDescription.getValue()));
            updates.setStatus(projectStatus.getValue());
            updates.setLetterTemplate(required(letterTemplate, "Letter template"));
            updates.setPitchDeck(required(pitchDeck, "Pitch deck"));
            updates.setMailSubject(required(mailSubject, "Email subject"));
            updates.setMailBody(required(mailBody, "Email body"));
            updates.setLetterAttachmentFilename(required(letterAttachmentFilename, "Letter attachment name"));
            updates.setPitchDeckAttachmentFilename(required(pitchDeckAttachmentFilename, "Pitch deck attachment name"));
            updates.setMailFrom(blankToNull(mailFrom.getValue()));
            updates.setSendDelayMs(sendDelayMs.getValue() == null ? 0 : sendDelayMs.getValue());
            updates.setInboxSyncCron(required(inboxSyncCron, "Inbox sync cron"));
            updates.setGmailUsername(blankToNull(gmailUsername.getValue()));
            updates.setGmailAppPassword(resolvePassword());
            selectedProject = projectService.update(selectedProject.getId(), updates);
            refreshProjectList();
            loadProjectSettings(selectedProject);
            selectedProjectName.setText(selectedProject.getName());
            Notification.show(systemOnly ? "System settings saved" : "Project setup saved", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private void refreshContacts() {
        refreshContacts("");
    }

    private void refreshContacts(String filter) {
        if (contactsGrid == null || selectedProject == null) {
            return;
        }
        String value = blankToNull(filter);
        List<Contact> contacts = contactService.searchContacts(selectedProject.getId(), value);
        contactsGrid.setItems(contacts);
    }

    private void editSelectedContact() {
        Contact contact = contactsGrid.asSingleSelect().getValue();
        if (contact != null) {
            openContactDialog(contact);
        }
    }

    private void openContactDialog(Contact contact) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit contact");
        dialog.setWidth("560px");

        TextField organization = new TextField("Organization");
        TextField country = new TextField("Country");
        TextField contactName = new TextField("Contact name");
        EmailField email = new EmailField("Email");
        TextArea notes = new TextArea("Preclinical notes");
        TextArea manualNote = new TextArea("Manual note");

        organization.setValue(value(contact.getOrganizationName()));
        country.setValue(value(contact.getCountry()));
        contactName.setValue(value(contact.getContactName()));
        email.setValue(value(contact.getEmail()));
        notes.setValue(value(contact.getPreclinicalNotes()));
        manualNote.setValue(value(contact.getNote()));

        Div form = new Div();
        form.addClassName("cw-dialog-form");
        form.add(organization, country, contactName, email, notes, manualNote);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            try {
                contact.setOrganizationName(required(organization, "Organization"));
                contact.setCountry(blankToNull(country.getValue()));
                contact.setContactName(required(contactName, "Contact name"));
                contact.setEmail(required(email, "Email"));
                contact.setPreclinicalNotes(blankToNull(notes.getValue()));
                contact.setNote(blankToNull(manualNote.getValue()));
                contactService.save(contact);
                refreshContacts();
                renderWorkspace();
                dialog.close();
                Notification.show("Contact saved", 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.add(form);
        dialog.open();
    }

    private void openNewProjectDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New project");
        TextField name = new TextField("Name");
        TextArea description = new TextArea("Description");
        name.setRequiredIndicatorVisible(true);
        Div form = new Div(name, description);
        form.addClassName("cw-dialog-form");

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button create = new Button("Create", event -> {
            try {
                Project project = new Project();
                project.setName(required(name, "Name"));
                project.setDescription(blankToNull(description.getValue()));
                Project saved = projectService.create(project);
                dialog.close();
                selectProject(saved.getId());
                Notification.show("Project created", 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(form);
        dialog.getFooter().add(cancel, create);
        dialog.open();
    }

    private void startBatch() {
        try {
            sendCoordinator.start(selectedProject.getId());
            renderWorkspace();
            Notification.show("Batch started", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private void runInboxSync() {
        try {
            inboxSyncService.syncInbox(selectedProject.getId());
            refreshContacts();
            renderWorkspace();
            Notification.show("Inbox synced", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private void checkMailbox() {
        try {
            mailHealthService.verifyConnections(selectedProject.getId());
            Notification.show("Mailbox connection OK", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private void toggleSystemDrawer() {
        if (systemDrawer.getClassNames().contains("open")) {
            systemDrawer.removeClassName("open");
        } else {
            systemDrawer.addClassName("open");
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

    private String required(EmailField field, String label) {
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
