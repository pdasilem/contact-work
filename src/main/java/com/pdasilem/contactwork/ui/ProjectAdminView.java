package com.pdasilem.contactwork.ui;

import com.pdasilem.contactwork.api.ImportContactsResponse;
import com.pdasilem.contactwork.api.SendStatusResponse;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactColumnSource;
import com.pdasilem.contactwork.contact.ContactImportService;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.contact.ProjectContactColumn;
import com.pdasilem.contactwork.contact.ProjectContactColumnService;
import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.mail.GmailAuthorizationRequiredException;
import com.pdasilem.contactwork.mail.MailHealthService;
import com.pdasilem.contactwork.mail.SendCoordinator;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import com.pdasilem.contactwork.project.asset.ProjectAsset;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import com.pdasilem.contactwork.project.asset.ProjectAssetType;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
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
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.TransferContext;
import com.vaadin.flow.server.streams.TransferProgressListener;
import com.vaadin.flow.server.streams.UploadHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route("app")
@CssImport("./styles/contactwork-app.css")
public class ProjectAdminView extends Composite<Div> {
    private static final Logger log = LoggerFactory.getLogger(ProjectAdminView.class);

    private final ProjectService projectService;
    private final ContactService contactService;
    private final ContactImportService contactImportService;
    private final SendCoordinator sendCoordinator;
    private final MailHealthService mailHealthService;
    private final InboxSyncService inboxSyncService;
    private final ProjectAssetService projectAssetService;
    private final ProjectContactColumnService projectContactColumnService;
    private final AppProperties appProperties;

    private final Div sidebar = new Div();
    private final VerticalLayout projectList = new VerticalLayout();
    private final Main workspace = new Main();
    private final Div systemDrawer = new Div();
    private final Div letterAssetList = new Div();
    private final Div attachmentAssetList = new Div();
    private final Span contactImportResult = new Span();
    private final Span readinessState = new Span();
    private final Span selectedProjectName = new Span("No project selected");
    private final Button projectDrawerToggle = new Button();
    private final H3 contactsTitle = new H3("Contacts");

    private Project selectedProject;
    private Grid<Contact> contactsGrid;
    private boolean projectDrawerCollapsed;

    private final Select<ProjectStatus> projectStatus = new Select<>();
    private final TextField mailSubject = new TextField("Email subject");
    private final TextArea mailBody = new TextArea("Email body");
    private final TextField letterAttachmentFilename = new TextField("Letter attachment name");
    private final TextField mailFrom = new TextField("Sender address");
    private final TextField mailFromName = new TextField("Sender name");
    private final Span senderAliasStatus = new Span("Synced from Gmail default alias");
    private final IntegerField sendDelayMs = new IntegerField("Send delay, ms");
    private final IntegerField maxMessagesPerBatch = new IntegerField("Max messages per batch");
    private final TextField inboxSyncCron = new TextField("Inbox sync cron");
    private final TextField gmailUsername = new TextField("Gmail username");
    private final PasswordField gmailAppPassword = new PasswordField("Gmail app password");

    public ProjectAdminView(
            ProjectService projectService,
            ContactService contactService,
            ContactImportService contactImportService,
            SendCoordinator sendCoordinator,
            MailHealthService mailHealthService,
            InboxSyncService inboxSyncService,
            ProjectAssetService projectAssetService,
            ProjectContactColumnService projectContactColumnService,
            AppProperties appProperties
    ) {
        this.projectService = projectService;
        this.contactService = contactService;
        this.contactImportService = contactImportService;
        this.sendCoordinator = sendCoordinator;
        this.mailHealthService = mailHealthService;
        this.inboxSyncService = inboxSyncService;
        this.projectAssetService = projectAssetService;
        this.projectContactColumnService = projectContactColumnService;
        this.appProperties = appProperties;
        buildShell();
        renderWelcome();
        refreshProjectList();
    }

    private void buildShell() {
        Div root = getContent();
        root.addClassName("cw-shell");

        sidebar.addClassName("cw-sidebar");
        sidebar.add(sidebarHeader(), projectList, newProjectButton());

        workspace.addClassName("cw-workspace");
        systemDrawer.addClassName("cw-system-drawer");

        root.add(appTopBar(), sidebar, workspace, systemDrawer);
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
        sidebar.setClassName("cw-sidebar");
        getContent().setClassName("cw-shell");
        if (projectDrawerCollapsed) {
            getContent().addClassName("sidebar-hidden");
        }
        projectDrawerToggle.setText(projectDrawerCollapsed ? "Show projects" : "Hide projects");
        projectDrawerToggle.setIcon(projectDrawerCollapsed ? VaadinIcon.MENU.create() : VaadinIcon.CLOSE_SMALL.create());
        projectList.setPadding(false);
        projectList.setSpacing(false);
        projectList.addClassName("cw-project-list");
        for (Project project : projectService.findAll()) {
            Button button = new Button(project.getName(),
                    new Icon(VaadinIcon.FOLDER_OPEN), event -> selectProject(project.getId()));
            button.addClassName("cw-project-button");
            if (selectedProject != null && selectedProject.getId().equals(project.getId())) {
                button.addClassName("selected");
            }
            projectList.add(button);
        }
    }

    private void toggleProjectDrawer() {
        projectDrawerCollapsed = !projectDrawerCollapsed;
        refreshProjectList();
    }

    private void renderWelcome() {
        workspace.removeAll();
        systemDrawer.removeAll();
        systemDrawer.removeClassName("open");
        selectedProject = null;
        selectedProjectName.setText("No project selected");

        Div empty = new Div();
        empty.addClassName("cw-empty-state");
        H2 title = new H2("Select a project");
        Span text = new Span("Choose a project on the left to see monitoring, contacts, campaign setup, and system settings.");
        Button create = new Button("Create project", VaadinIcon.PLUS.create(), event -> openNewProjectDialog());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        empty.add(title, text, create);
        workspace.add(empty);
    }

    private HorizontalLayout appTopBar() {
        projectDrawerToggle.addClickListener(event -> toggleProjectDrawer());
        projectDrawerToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Span today = new Span(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        today.addClassName("cw-topbar-date");
        H1 appName = new H1("ContactWork");
        appName.addClassName("cw-app-title");
        HorizontalLayout left = new HorizontalLayout(projectDrawerToggle, today, appName);
        left.setAlignItems(Alignment.CENTER);

        selectedProjectName.addClassName("cw-selected-project");

        Button refresh = new Button("Refresh", VaadinIcon.REFRESH.create(), event -> refreshSelectedProject());
        Button settings = new Button("System", VaadinIcon.COG.create(), event -> toggleSystemDrawer());
        HorizontalLayout right = new HorizontalLayout(refresh, settings);
        right.setAlignItems(Alignment.CENTER);

        HorizontalLayout bar = new HorizontalLayout(left, selectedProjectName, right);
        bar.addClassName("cw-app-topbar");
        bar.setWidthFull();
        bar.setAlignItems(Alignment.CENTER);
        bar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        return bar;
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

        workspace.add(monitoring(), contactsSection(), setupSection());
        buildSystemDrawer();
        loadProjectSettings(selectedProject);
        refreshContacts();
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
        Div section = new Div();
        section.addClassName("cw-section");
        contactsTitle.setText("Contacts (0 records)");
        Span text = new Span("Edit core contact data and run project send actions.");
        text.addClassName("cw-muted");
        section.add(contactsTitle, text);
        TextField filter = new TextField();
        filter.setPlaceholder("Filter by email or organization");
        filter.setPrefixComponent(VaadinIcon.SEARCH.create());
        filter.addValueChangeListener(event -> refreshContacts(event.getValue()));

        Button sync = new Button("Sync inbox", VaadinIcon.INBOX.create(), event -> runInboxSync());
        Button send = new Button("Send...", VaadinIcon.PAPERPLANE.create(), event -> openSendDialog());
        Button add = new Button("Add row", VaadinIcon.PLUS.create(), event -> openAddContactDialog());
        Button edit = new Button("Edit selected", VaadinIcon.EDIT.create(), event -> editSelectedContact());
        Button preview = new Button("Preview letter", VaadinIcon.FILE_TEXT.create(), event -> previewSelectedContact());
        Button delete = new Button("Delete selected", VaadinIcon.TRASH.create(), event -> confirmDeleteSelectedContact());

        HorizontalLayout toolbar = new HorizontalLayout(filter, add, edit, preview, delete, sync, send);
        toolbar.addClassName("cw-toolbar");
        toolbar.setWidthFull();
        toolbar.expand(filter);

        contactsGrid = new Grid<>(Contact.class, false);
        contactsGrid.addClassName("cw-contacts-grid");
        rebuildContactGridColumns();
        contactsGrid.setHeight("340px");
        contactsGrid.asSingleSelect().addValueChangeListener(event -> {
            boolean selected = event.getValue() != null;
            edit.setEnabled(selected);
            preview.setEnabled(selected);
            delete.setEnabled(selected);
        });
        contactsGrid.addItemDoubleClickListener(event -> openContactDialog(event.getItem()));
        edit.setEnabled(false);
        preview.setEnabled(false);
        delete.setEnabled(false);

        section.add(toolbar, contactsGrid);
        return section;
    }

    private void rebuildContactGridColumns() {
        contactsGrid.removeAllColumns();
        configureContactColumns();
        contactsGrid.addColumn(Contact::getStatus).setHeader("Status").setAutoWidth(true).setFlexGrow(0).setResizable(true);
        contactsGrid.addColumn(Contact::getSentAt).setHeader("Sent").setAutoWidth(true).setFlexGrow(0).setResizable(true);
    }

    private void configureContactColumns() {
        List<ProjectContactColumn> columns = projectContactColumnService.findVisibleColumns(selectedProject.getId());
        if (columns.isEmpty()) {
            contactsGrid.addColumn(Contact::getContactName).setHeader("Contact").setAutoWidth(true).setFlexGrow(1).setResizable(true);
            contactsGrid.addColumn(Contact::getEmail).setHeader("Email").setAutoWidth(true).setFlexGrow(1).setResizable(true);
            contactsGrid.addColumn(Contact::getOrganizationName).setHeader("Organization").setAutoWidth(true).setFlexGrow(1).setResizable(true);
            return;
        }
        for (ProjectContactColumn column : columns) {
            contactsGrid.addColumn(contact -> valueForColumn(contact, column))
                    .setHeader(column.getDisplayLabel())
                    .setAutoWidth(true)
                    .setFlexGrow(1)
                    .setResizable(true);
        }
    }

    private Div setupSection() {
        Div section = section("Project setup", value(selectedProject.getDescription()));
        section.addClassName("cw-setup");

        configureProjectFields();
        Upload upload = contactsUpload();
        Upload letterUpload = assetUpload("Upload letter", ProjectAssetType.LETTER_TEMPLATE, ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        Upload attachmentUpload = assetUpload("Upload attachment", ProjectAssetType.ATTACHMENT, ".pdf", "application/pdf");
        Button save = new Button("Save project setup", VaadinIcon.CHECK.create(), event -> saveProjectSettings(false));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        contactImportResult.setText("");

        Div form = new Div();
        form.addClassName("cw-setup-grid");
        form.add(mailSubject, mailBody, letterAttachmentFilename);

        Div uploads = new Div();
        uploads.addClassName("cw-upload-grid");
        uploads.add(uploadPanel(upload, contactImportResult), uploadPanel(letterUpload, letterAssetList),
                uploadPanel(attachmentUpload, attachmentAssetList));

        contactImportResult.addClassName("cw-muted");
        letterAssetList.addClassName("cw-asset-list");
        attachmentAssetList.addClassName("cw-asset-list");
        refreshAssets();
        section.add(form, uploads, readinessState, save);
        return section;
    }

    private void configureProjectFields() {
        mailSubject.setRequiredIndicatorVisible(false);
        mailBody.setRequiredIndicatorVisible(false);
        letterAttachmentFilename.setPlaceholder("letter.pdf");
        mailBody.setMinHeight("150px");
    }

    private Div uploadPanel(Upload upload, com.vaadin.flow.component.Component fileList) {
        Div panel = new Div(upload, fileList);
        panel.addClassName("cw-upload-panel");
        return panel;
    }

    private Upload assetUpload(String label, ProjectAssetType type, String... acceptedTypes) {
        Upload[] uploadRef = new Upload[1];
        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            try {
                projectAssetService.store(
                        selectedProject.getId(),
                        type,
                        metadata.fileName(),
                        metadata.contentType(),
                        new ByteArrayInputStream(bytes)
                );
                refreshAssets();
                uploadRef[0].clearFileList();
                Notification.show("Uploaded " + metadata.fileName(), 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        }, uploadFailureNotifier()));
        uploadRef[0] = upload;
        upload.setAcceptedFileTypes(acceptedTypes);
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span(label));
        return upload;
    }

    private void updateReadinessState() {
        readinessState.setClassName("cw-pill");
        if (selectedProject == null) {
            readinessState.setText("Not ready: no project selected");
            readinessState.addClassName("cw-pill-warning");
            return;
        }
        List<String> reasons = new java.util.ArrayList<>();
        if (selectedProject.getStatus() != ProjectStatus.ACTIVE) {
            reasons.add("project is not active");
        }
        if (projectAssetService.activeLetter(selectedProject.getId()).isEmpty()) {
            reasons.add("letter template missing");
        }
        if (value(selectedProject.getMailSubject()).isBlank()) {
            reasons.add("subject missing");
        }
        if (value(selectedProject.getMailBody()).isBlank()) {
            reasons.add("body missing");
        }
        if (!hasEffectiveCredential(selectedProject.getGmailUsername(), appProperties.mail().gmail().username())
                || !hasEffectiveCredential(selectedProject.getGmailAppPassword(), appProperties.mail().gmail().appPassword())) {
            reasons.add("system credentials missing");
        }
        if (reasons.isEmpty()) {
            readinessState.setText("Ready for sending");
            readinessState.addClassName("cw-pill-success");
        } else {
            readinessState.setText("Not ready: " + String.join(", ", reasons));
            readinessState.addClassName("cw-pill-warning");
        }
    }

    private void refreshAssets() {
        letterAssetList.removeAll();
        attachmentAssetList.removeAll();
        if (selectedProject == null) {
            return;
        }
        selectedProject = projectService.getProject(selectedProject.getId());
        updateReadinessState();
        projectAssetService.activeLetter(selectedProject.getId())
                .ifPresentOrElse(asset -> letterAssetList.add(assetRow(asset)),
                        () -> letterAssetList.add(emptyAssetText("No letter uploaded.")));
        List<ProjectAsset> attachments = projectAssetService.activeAttachments(selectedProject.getId());
        if (attachments.isEmpty()) {
            attachmentAssetList.add(emptyAssetText("No attachments uploaded."));
            return;
        }
        attachments.forEach(asset -> attachmentAssetList.add(assetRow(asset)));
    }

    private Div assetRow(ProjectAsset asset) {
        Button remove = new Button("Remove", VaadinIcon.CLOSE_SMALL.create(), event -> {
            projectAssetService.delete(selectedProject.getId(), asset.getId());
            refreshAssets();
        });
        Span file = new Span(asset.getOriginalFilename() + " - " + sizeKb(asset.getSizeBytes()) + " KB");
        Div row = new Div(file, remove);
        row.addClassName("cw-asset-row");
        return row;
    }

    private Span emptyAssetText(String text) {
        Span empty = new Span(text);
        empty.addClassName("cw-muted");
        return empty;
    }

    private long sizeKb(long bytes) {
        return Math.max(1, Math.round(bytes / 1024.0));
    }

    private Upload contactsUpload() {
        Upload[] uploadRef = new Upload[1];
        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            try {
                log.info("Vaadin contacts CSV upload started: projectId={}, filename={}",
                        selectedProject.getId(), metadata.fileName());
                ImportContactsResponse response = contactImportService.importContacts(
                        selectedProject.getId(),
                        metadata.fileName(),
                        new ByteArrayInputStream(bytes)
                );
                rebuildContactGridColumns();
                refreshContacts();
                String result = "Import complete: inserted " + response.inserted()
                        + ", skipped existing " + response.skippedExisting()
                        + ", skipped invalid " + response.skippedInvalid();
                contactImportResult.setText(result);
                uploadRef[0].clearFileList();
                log.info("Vaadin contacts CSV upload succeeded: projectId={}, filename={}, inserted={}, skippedExisting={}, skippedInvalid={}",
                        selectedProject.getId(), metadata.fileName(), response.inserted(), response.skippedExisting(),
                        response.skippedInvalid());
                Notification.show(result, 5000, Position.BOTTOM_START);
            } catch (Exception ex) {
                log.warn("Vaadin contacts CSV upload failed: projectId={}, filename={}",
                        selectedProject == null ? null : selectedProject.getId(), metadata.fileName(), ex);
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        }, uploadFailureNotifier()));
        uploadRef[0] = upload;
        upload.setAcceptedFileTypes(".csv", "text/csv");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span("Drop contacts CSV"));
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
        maxMessagesPerBatch.setMin(1);
        inboxSyncCron.setRequiredIndicatorVisible(true);
        mailFrom.setReadOnly(true);
        mailFromName.setReadOnly(true);
        senderAliasStatus.addClassName("cw-muted");

        Button health = new Button("Check mailbox", VaadinIcon.CONNECT.create(), event -> checkMailbox());
        Button save = new Button("Save system settings", VaadinIcon.CHECK.create(), event -> saveProjectSettings(true));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button close = new Button("Close", VaadinIcon.CLOSE_SMALL.create(), event -> systemDrawer.removeClassName("open"));

        VerticalLayout content = new VerticalLayout(title, helper, projectStatus, mailFromName, mailFrom, senderAliasStatus, gmailUsername,
                gmailAppPassword, sendDelayMs, maxMessagesPerBatch, inboxSyncCron, health, save, close);
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
        projectStatus.setItems(ProjectStatus.values());
        projectStatus.setValue(project.getStatus() == null ? ProjectStatus.NEW : project.getStatus());
        mailSubject.setValue(value(project.getMailSubject()));
        mailBody.setValue(value(project.getMailBody()));
        letterAttachmentFilename.setValue(value(project.getLetterAttachmentFilename()));
        mailFrom.setValue(value(project.getMailFrom()));
        mailFromName.setValue(value(project.getMailFromName()));
        sendDelayMs.setValue((int) Math.max(0, project.getSendDelayMs()));
        maxMessagesPerBatch.setValue(project.getMaxMessagesPerBatch());
        inboxSyncCron.setValue(value(project.getInboxSyncCron()));
        gmailUsername.setValue(value(blankToNull(project.getGmailUsername())));
        gmailAppPassword.setValue(value(blankToNull(project.getGmailAppPassword())));
    }

    private void saveProjectSettings(boolean systemOnly) {
        try {
            Project updates = new Project();
            updates.setStatus(projectStatus.getValue());
            updates.setMailSubject(blankToNull(mailSubject.getValue()));
            updates.setMailBody(blankToNull(mailBody.getValue()));
            updates.setLetterAttachmentFilename(blankToNull(letterAttachmentFilename.getValue()));
            updates.setMailFrom(blankToNull(mailFrom.getValue()));
            updates.setMailFromName(blankToNull(mailFromName.getValue()));
            updates.setSendDelayMs(sendDelayMs.getValue() == null ? 0 : sendDelayMs.getValue());
            updates.setMaxMessagesPerBatch(maxMessagesPerBatch.getValue());
            updates.setInboxSyncCron(required(inboxSyncCron, "Inbox sync cron"));
            updates.setGmailUsername(blankToNull(gmailUsername.getValue()));
            updates.setGmailAppPassword(resolvePassword());
            selectedProject = projectService.update(selectedProject.getId(), updates);
            refreshProjectList();
            loadProjectSettings(selectedProject);
            selectedProjectName.setText(selectedProject.getName());
            updateReadinessState();
            Notification.show(systemOnly ? "System settings saved" : "Project setup saved", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private void refreshSelectedProject() {
        if (selectedProject == null) {
            refreshProjectList();
            buildSystemDrawer();
            return;
        }
        selectedProject = projectService.getProject(selectedProject.getId());
        selectedProjectName.setText(selectedProject.getName());
        refreshProjectList();
        loadProjectSettings(selectedProject);
        rebuildContactGridColumns();
        refreshContacts();
        refreshAssets();
        buildSystemDrawer();
        Notification.show("Project refreshed", 2000, Position.BOTTOM_START);
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
        contactsTitle.setText("Contacts (" + contacts.size() + " records)");
    }

    private void editSelectedContact() {
        Contact contact = contactsGrid.asSingleSelect().getValue();
        if (contact != null) {
            openContactDialog(contact);
        }
    }

    private void previewSelectedContact() {
        Contact contact = contactsGrid.asSingleSelect().getValue();
        if (contact == null) {
            return;
        }
        String url = "/api/v1/projects/" + selectedProject.getId() + "/contacts/" + contact.getId() + "/letter/pdf";
        UI.getCurrent().getPage().open(url, "_blank");
    }

    private void openSendDialog() {
        Contact contact = contactsGrid.asSingleSelect().getValue();
        SendStatusResponse status = sendCoordinator.getStatus(selectedProject.getId());
        List<String> blockers = sendingBlockers();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Send");
        dialog.setWidth("640px");

        Select<SendMode> mode = new Select<>();
        mode.setLabel("Send mode");
        mode.setItems(SendMode.SELECTED_CONTACT, SendMode.BATCH);
        mode.setItemLabelGenerator(SendMode::label);
        mode.setValue(contact == null ? SendMode.BATCH : SendMode.SELECTED_CONTACT);

        Span error = new Span();
        error.addClassName("cw-error");

        Div body = new Div();
        body.addClassName("cw-dialog-form");
        body.add(mode, sendReadinessSummary(blockers), sendCounterSummary(status), batchRuleSummary(status),
                selectedContactSummary(contact), attachmentSummary(), error);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button sendContact = new Button(contactRequiresForce(contact) ? "Send again" : "Send contact", event -> {
            if (contact == null) {
                error.setText("Select a contact before sending.");
                return;
            }
            boolean force = contactRequiresForce(contact);
            if (executeSendContact(contact, force, error)) {
                dialog.close();
            }
        });
        Button startBatch = new Button("Start batch", event -> {
            if (executeStartBatch(status.eligibleBatchCount(), error)) {
                dialog.close();
            }
        });
        sendContact.setIcon(VaadinIcon.ENVELOPE.create());
        startBatch.setIcon(VaadinIcon.PAPERPLANE.create());
        startBatch.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (contactRequiresForce(contact)) {
            sendContact.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        } else {
            sendContact.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        }
        sendContact.setEnabled(contact != null && blockers.isEmpty());
        startBatch.setEnabled(blockers.isEmpty() && !status.running() && status.eligibleBatchCount() > 0);
        mode.addValueChangeListener(event -> {
            boolean selectedMode = event.getValue() == SendMode.SELECTED_CONTACT;
            sendContact.setVisible(selectedMode);
            startBatch.setVisible(!selectedMode);
        });
        sendContact.setVisible(mode.getValue() == SendMode.SELECTED_CONTACT);
        startBatch.setVisible(mode.getValue() == SendMode.BATCH);

        dialog.add(body);
        dialog.getFooter().add(cancel, sendContact, startBatch);
        dialog.open();
    }

    private boolean executeSendContact(Contact contact, boolean force, Span error) {
        try {
            sendCoordinator.sendSingle(selectedProject.getId(), contact.getId(), force);
            refreshContacts();
            renderWorkspace();
            Notification.show("Contact send processed", 2500, Position.BOTTOM_START);
            return true;
        } catch (Exception ex) {
            refreshContacts();
            renderWorkspace();
            String message = rootCauseMessage("Failed to send contact", ex);
            error.setText(message);
            Notification.show(message, 5000, Position.BOTTOM_START);
            return false;
        }
    }

    private boolean executeStartBatch(long eligibleBatchCount, Span error) {
        try {
            sendCoordinator.start(selectedProject.getId());
            refreshContacts();
            renderWorkspace();
            Notification.show("Batch started: " + eligibleBatchCount + " eligible contacts", 2500, Position.BOTTOM_START);
            return true;
        } catch (Exception ex) {
            String message = rootCauseMessage("Failed to start batch", ex);
            error.setText(message);
            Notification.show(message, 5000, Position.BOTTOM_START);
            return false;
        }
    }

    private Div sendReadinessSummary(List<String> blockers) {
        Div summary = new Div();
        summary.addClassName("cw-send-summary");
        if (blockers.isEmpty()) {
            summary.add(new Span("Ready for sending"));
            return summary;
        }
        summary.add(new Span("Blocked: " + String.join(", ", blockers)));
        return summary;
    }

    private Div sendCounterSummary(SendStatusResponse status) {
        Div summary = new Div();
        summary.addClassName("cw-send-summary");
        summary.add(new Span("New: " + status.newCount()));
        summary.add(new Span("Eligible batch: " + status.eligibleBatchCount()));
        summary.add(new Span("Sent: " + status.sentCount()));
        summary.add(new Span("Failed: " + status.sendFailedCount()));
        summary.add(new Span("Replied: " + status.repliedCount()));
        summary.add(new Span("Bounced: " + status.bouncedCount()));
        return summary;
    }

    private Div batchRuleSummary(SendStatusResponse status) {
        Div summary = new Div();
        summary.addClassName("cw-send-summary");
        summary.add(new Span(status.running() ? "Batch is running." : "Batch is idle."));
        summary.add(new Span("Batch sends only NEW contacts."));
        summary.add(new Span("Max batch cap: " + selectedProject.getMaxMessagesPerBatch()));
        summary.add(new Span("Send delay: " + selectedProject.getSendDelayMs() + " ms"));
        return summary;
    }

    private Div selectedContactSummary(Contact contact) {
        Div summary = new Div();
        summary.addClassName("cw-send-summary");
        if (contact == null) {
            summary.add(new Span("Selected contact: none"));
            return summary;
        }
        summary.add(new Span("Selected contact: " + value(contact.getEmail())));
        summary.add(new Span("Name: " + value(contact.getContactName())));
        summary.add(new Span("Organization: " + value(contact.getOrganizationName())));
        summary.add(new Span("Status: " + contact.getStatus()));
        summary.add(new Span("Sent at: " + value(contact.getSentAt())));
        summary.add(new Span("Outbound message id: " + value(contact.getOutboundMessageId())));
        if (contactRequiresForce(contact)) {
            summary.add(new Span("Warning: this will send another email to " + contact.getEmail() + "."));
        }
        return summary;
    }

    private Div attachmentSummary() {
        Div summary = new Div();
        summary.addClassName("cw-send-summary");
        String letter = projectAssetService.activeLetter(selectedProject.getId())
                .map(ProjectAsset::getOriginalFilename)
                .orElse("missing");
        List<ProjectAsset> attachments = projectAssetService.activeAttachments(selectedProject.getId());
        summary.add(new Span("Active letter template: " + letter));
        summary.add(new Span("Mail attachments: " + attachments.size()));
        attachments.forEach(asset -> summary.add(new Span(asset.getOriginalFilename())));
        return summary;
    }

    private List<String> sendingBlockers() {
        List<String> blockers = new java.util.ArrayList<>();
        if (selectedProject.getStatus() != ProjectStatus.ACTIVE) {
            blockers.add("inactive project");
        }
        if (value(selectedProject.getMailSubject()).isBlank()) {
            blockers.add("missing subject");
        }
        if (value(selectedProject.getMailBody()).isBlank()) {
            blockers.add("missing body");
        }
        if (!hasEffectiveCredential(selectedProject.getGmailUsername(), appProperties.mail().gmail().username())
                || !hasEffectiveCredential(selectedProject.getGmailAppPassword(), appProperties.mail().gmail().appPassword())) {
            blockers.add("missing Gmail credentials");
        }
        if (projectAssetService.activeLetter(selectedProject.getId()).isEmpty()) {
            blockers.add("missing active letter template");
        }
        return blockers;
    }

    private boolean contactRequiresForce(Contact contact) {
        return contact != null && contact.getStatus() != ContactStatus.NEW && contact.getStatus() != ContactStatus.SEND_FAILED;
    }

    private void confirmDeleteSelectedContact() {
        Contact contact = contactsGrid.asSingleSelect().getValue();
        if (contact == null) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delete contact");
        dialog.add(new Span("Delete " + contact.getEmail() + "? History is kept when the contact was already used."));
        Button cancel = new Button("Cancel", event -> dialog.close());
        Button delete = new Button("Delete", event -> {
            try {
                contactService.deleteContact(selectedProject.getId(), contact.getId());
                refreshContacts();
                dialog.close();
                Notification.show("Contact deleted", 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, delete);
        dialog.open();
    }

    private String valueForColumn(Contact contact, ProjectContactColumn column) {
        return switch (column.getColumnKey()) {
            case "email" -> value(contact.getEmail());
            case "organization_name" -> value(contact.getOrganizationName());
            case "contact_name" -> value(contact.getContactName());
            case "note" -> value(contact.getNote());
            default -> contactService.findCustomFields(selectedProject.getId(), contact.getId()).stream()
                    .filter(field -> Objects.equals(field.getFieldKey(), column.getColumnKey()))
                    .map(field -> value(field.getFieldValue()))
                    .findFirst()
                    .orElse("");
        };
    }

    private void openContactDialog(Contact contact) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit contact");
        dialog.setWidth("560px");

        TextField organization = new TextField("Organization");
        TextField contactName = new TextField("Contact name");
        EmailField email = new EmailField("Email");
        TextArea manualNote = new TextArea("Note");

        organization.setValue(value(contact.getOrganizationName()));
        organization.setReadOnly(true);
        contactName.setValue(value(contact.getContactName()));
        email.setValue(value(contact.getEmail()));
        manualNote.setValue(value(contact.getNote()));

        Div form = new Div();
        form.addClassName("cw-dialog-form");
        form.add(organization, contactName, email, manualNote);
        addReadOnlyCustomFields(form, contact);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            try {
                contactService.updateEditableFields(
                        selectedProject.getId(),
                        contact.getId(),
                        contactName.getValue(),
                        required(email, "Email"),
                        manualNote.getValue()
                );
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

    private void openAddContactDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add contact");
        dialog.setWidth("560px");

        TextField organization = new TextField("Organization");
        TextField contactName = new TextField("Contact name");
        EmailField email = new EmailField("Email");
        TextArea note = new TextArea("Note");
        organization.setRequiredIndicatorVisible(true);
        email.setRequiredIndicatorVisible(true);

        Map<String, TextField> customInputs = new LinkedHashMap<>();
        Div form = new Div();
        form.addClassName("cw-dialog-form");
        form.add(organization, contactName, email, note);
        for (ProjectContactColumn column : customColumns()) {
            TextField field = new TextField(column.getDisplayLabel());
            customInputs.put(column.getColumnKey(), field);
            form.add(field);
        }

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            try {
                Map<String, String> customValues = new LinkedHashMap<>();
                customInputs.forEach((key, field) -> customValues.put(key, field.getValue()));
                contactService.createContact(
                        selectedProject,
                        required(organization, "Organization"),
                        contactName.getValue(),
                        required(email, "Email"),
                        note.getValue(),
                        customValues
                );
                refreshContacts();
                renderWorkspace();
                dialog.close();
                Notification.show("Contact added", 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.add(form);
        dialog.open();
    }

    private void addReadOnlyCustomFields(Div form, Contact contact) {
        for (ProjectContactColumn column : customColumns()) {
            TextField field = new TextField(column.getDisplayLabel());
            field.setValue(valueForColumn(contact, column));
            field.setReadOnly(true);
            form.add(field);
        }
    }

    private List<ProjectContactColumn> customColumns() {
        return projectContactColumnService.findVisibleColumns(selectedProject.getId()).stream()
                .filter(column -> column.getSourceType() == ContactColumnSource.CUSTOM)
                .toList();
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

    private void runInboxSync() {
        try {
            inboxSyncService.syncInbox(selectedProject.getId());
            refreshContacts();
            renderWorkspace();
            Notification.show("Inbox synced", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            log.warn("Manual inbox sync failed for project {}", selectedProject.getId(), ex);
            Notification.show(rootCauseMessage("Failed to sync inbox", ex), 5000, Position.BOTTOM_START);
        }
    }

    private void checkMailbox() {
        try {
            selectedProject = mailHealthService.verifyConnectionsAndSyncAlias(selectedProject.getId());
            loadProjectSettings(selectedProject);
            updateReadinessState();
            Notification.show("Mailbox connection OK. Sender alias synced.", 2500, Position.BOTTOM_START);
        } catch (GmailAuthorizationRequiredException ex) {
            UI.getCurrent().getPage().setLocation(ex.getAuthorizationUrl());
        } catch (Exception ex) {
            Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
        }
    }

    private TransferProgressListener uploadFailureNotifier() {
        return new TransferProgressListener() {
            @Override
            public void onError(TransferContext context, IOException reason) {
                Notification.show(uploadFailureMessage(reason), 7000, Position.BOTTOM_START);
            }
        };
    }

    private String uploadFailureMessage(Exception reason) {
        if (reason == null || reason.getMessage() == null || reason.getMessage().isBlank()) {
            return "Upload failed";
        }
        return "Upload failed: " + reason.getMessage();
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
        if (password != null) {
            return password;
        }
        if (selectedProject == null) {
            return null;
        }
        return selectedProject.getGmailAppPassword();
    }

    private boolean hasEffectiveCredential(String value, String appDefault) {
        return blankToNull(value) != null || blankToNull(appDefault) != null;
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

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String rootCauseMessage(String prefix, Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = ex.getMessage();
        }
        if (detail == null || detail.isBlank()) {
            return prefix;
        }
        return prefix + ": " + detail;
    }

    private enum SendMode {
        SELECTED_CONTACT("Selected contact"),
        BATCH("Batch");

        private final String label;

        SendMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

}
