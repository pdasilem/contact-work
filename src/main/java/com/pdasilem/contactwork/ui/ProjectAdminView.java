package com.pdasilem.contactwork.ui;

import com.pdasilem.contactwork.api.ImportContactsResponse;
import com.pdasilem.contactwork.api.SendStatusResponse;
import com.pdasilem.contactwork.auth.AppRole;
import com.pdasilem.contactwork.auth.AppUser;
import com.pdasilem.contactwork.auth.AppUserService;
import com.pdasilem.contactwork.auth.CurrentUserService;
import com.pdasilem.contactwork.ai.AiChatMessage;
import com.pdasilem.contactwork.ai.AiChatRole;
import com.pdasilem.contactwork.ai.AiChatSession;
import com.pdasilem.contactwork.ai.AiModelCatalogService;
import com.pdasilem.contactwork.ai.AppAiSettings;
import com.pdasilem.contactwork.ai.AppAiSettingsService;
import com.pdasilem.contactwork.ai.LocalAiService;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactColumnSource;
import com.pdasilem.contactwork.contact.ContactImportService;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.contact.ProjectContactColumn;
import com.pdasilem.contactwork.contact.ProjectContactColumnService;
import com.pdasilem.contactwork.conversation.ContactConversationSummary;
import com.pdasilem.contactwork.conversation.MailboxMessage;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.mail.GmailAuthorizationRequiredException;
import com.pdasilem.contactwork.mail.GmailAliasService;
import com.pdasilem.contactwork.mail.MailHealthService;
import com.pdasilem.contactwork.mail.ContactFreeformMailService;
import com.pdasilem.contactwork.mail.SendCoordinator;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.AiProvider;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import com.pdasilem.contactwork.project.asset.ProjectAsset;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import com.pdasilem.contactwork.project.asset.ProjectAssetType;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
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
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.streams.TransferContext;
import com.vaadin.flow.server.streams.TransferProgressListener;
import com.vaadin.flow.server.streams.UploadHandler;
import jakarta.annotation.security.RolesAllowed;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.HtmlUtils;

@Route("app")
@RouteAlias("")
@CssImport("./styles/contactwork-app.css")
@RolesAllowed({"ADMIN", "USER"})
public class ProjectAdminView extends Composite<Div> implements BeforeEnterObserver {
    private static final Logger log = LoggerFactory.getLogger(ProjectAdminView.class);
    private static final String STORAGE_PROJECT_DRAWER_COLLAPSED = "contactwork.projectDrawerCollapsed";
    private static final String STORAGE_SETUP_EXPANDED = "contactwork.projectSetupExpanded";
    private static final String STORAGE_SELECTED_PROJECT_ID = "contactwork.selectedProjectId";
    private static final String STORAGE_GRID_WIDTH_PREFIX = "contactwork.contactGridWidth.";
    private static final String STORAGE_PROJECT_CHAT_HEIGHT = "contactwork.projectChatHeight";
    private static final String STORAGE_CONTACT_CHAT_HEIGHT = "contactwork.contactChatHeight";
    private static final String STORAGE_CONVERSATION_HEIGHT = "contactwork.conversationHeight";
    private static final String STORAGE_CONVERSATION_SUMMARY_HEIGHT = "contactwork.conversationSummaryHeight";
    private static final String STORAGE_CONTACTS_GRID_HEIGHT_PREFIX = "contactwork.contactsGridHeight.";

    private final ProjectService projectService;
    private final ContactService contactService;
    private final ContactImportService contactImportService;
    private final SendCoordinator sendCoordinator;
    private final MailHealthService mailHealthService;
    private final GmailAliasService gmailAliasService;
    private final InboxSyncService inboxSyncService;
    private final ContactFreeformMailService contactFreeformMailService;
    private final ProjectAssetService projectAssetService;
    private final ProjectContactColumnService projectContactColumnService;
    private final MailboxMessageRepository mailboxMessageRepository;
    private final LocalAiService localAiService;
    private final AiModelCatalogService modelCatalogService;
    private final AppAiSettingsService appAiSettingsService;
    private final CurrentUserService currentUserService;
    private final AppUserService appUserService;
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
    private boolean projectDrawerCollapsed = true;
    private boolean setupExpanded;

    private final Select<ProjectStatus> projectStatus = new Select<>();
    private final Select<MailTransportType> mailTransportSelect = new Select<>();
    private final Span brevoInfo = new Span("Uses global BREVO_API_KEY from environment");
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
            GmailAliasService gmailAliasService,
            InboxSyncService inboxSyncService,
            ContactFreeformMailService contactFreeformMailService,
            ProjectAssetService projectAssetService,
            ProjectContactColumnService projectContactColumnService,
            MailboxMessageRepository mailboxMessageRepository,
            LocalAiService localAiService,
            AiModelCatalogService modelCatalogService,
            AppAiSettingsService appAiSettingsService,
            CurrentUserService currentUserService,
            AppUserService appUserService,
            AppProperties appProperties
    ) {
        this.projectService = projectService;
        this.contactService = contactService;
        this.contactImportService = contactImportService;
        this.sendCoordinator = sendCoordinator;
        this.mailHealthService = mailHealthService;
        this.gmailAliasService = gmailAliasService;
        this.inboxSyncService = inboxSyncService;
        this.contactFreeformMailService = contactFreeformMailService;
        this.projectAssetService = projectAssetService;
        this.projectContactColumnService = projectContactColumnService;
        this.mailboxMessageRepository = mailboxMessageRepository;
        this.localAiService = localAiService;
        this.modelCatalogService = modelCatalogService;
        this.appAiSettingsService = appAiSettingsService;
        this.currentUserService = currentUserService;
        this.appUserService = appUserService;
        this.appProperties = appProperties;
        buildShell();
        renderWelcome();
        refreshProjectList();
        restoreUiStateFromLocalStorage();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> parameters = event.getLocation().getQueryParameters().getParameters();
        String result = first(parameters, "gmailAlias");
        if (result == null) {
            return;
        }
        String projectId = first(parameters, "projectId");
        if (projectId != null && !projectId.isBlank()) {
            try {
                selectedProject = projectService.getProject(UUID.fromString(projectId));
                selectedProjectName.setText(selectedProject.getName());
                refreshProjectList();
                renderWorkspace();
            } catch (Exception ex) {
                log.warn("Gmail alias OAuth return project reload failed: projectId={}", projectId, ex);
            }
        } else if (selectedProject != null) {
            selectedProject = projectService.getProject(selectedProject.getId());
            loadProjectSettings(selectedProject);
            updateReadinessState();
        }
        if ("success".equals(result)) {
            log.info("Gmail alias OAuth callback succeeded: projectId={}", projectId);
            Notification.show("Sender alias synced", 2500, Position.BOTTOM_START);
        } else {
            String message = first(parameters, "message");
            String detail = message == null || message.isBlank() ? "Sender alias sync failed" : message;
            log.warn("Gmail alias OAuth callback failed: projectId={}, message={}", projectId, detail);
            Notification.show(detail, 7000, Position.BOTTOM_START);
        }
    }

    private void buildShell() {
        Div root = getContent();
        root.addClassName("cw-shell");

        sidebar.addClassName("cw-sidebar");
        sidebar.add(sidebarHeader(), projectList);
        if (currentUserService.canCreateProjects()) {
            sidebar.add(newProjectButton());
        }

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
        persistUiState(STORAGE_PROJECT_DRAWER_COLLAPSED, Boolean.toString(projectDrawerCollapsed));
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
        empty.add(title, text);
        if (currentUserService.canCreateProjects()) {
            Button create = new Button("Create project", VaadinIcon.PLUS.create(), event -> openNewProjectDialog());
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            empty.add(create);
        }
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
        Button aiModel = new Button("AI model", VaadinIcon.MAGIC.create(), event -> openAiModelDialog());
        Button settings = new Button("System", VaadinIcon.COG.create(), event -> toggleSystemDrawer());
        Button logout = new Button("Log out", VaadinIcon.SIGN_OUT.create(), event ->
                UI.getCurrent().getPage().setLocation("/logout"));
        HorizontalLayout right;
        if (currentUserService.isAdmin()) {
            Button admin = new Button("Admin", VaadinIcon.USER.create(), event -> openAdminDialog());
            right = new HorizontalLayout(admin, refresh, aiModel, settings, logout);
        } else if (currentUserService.canUseGlobalSettings()) {
            right = new HorizontalLayout(refresh, aiModel, settings, logout);
        } else {
            right = new HorizontalLayout(refresh, settings, logout);
        }
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
        persistUiState(STORAGE_SELECTED_PROJECT_ID, projectId.toString());
        refreshProjectList();
        renderWorkspace();
    }

    private void restoreUiStateFromLocalStorage() {
        getElement().executeJs("""
                const drawer = window.localStorage.getItem($1);
                const setup = window.localStorage.getItem($2);
                const projectId = window.localStorage.getItem($3) || '';
                $0.$server.restoreUiState(drawer || '', setup || '', projectId);
                """, getElement(), STORAGE_PROJECT_DRAWER_COLLAPSED, STORAGE_SETUP_EXPANDED, STORAGE_SELECTED_PROJECT_ID);
    }

    @ClientCallable
    public void restoreUiState(String drawerCollapsedValue, String setupExpandedValue, String selectedProjectIdValue) {
        if (!drawerCollapsedValue.isBlank()) {
            projectDrawerCollapsed = Boolean.parseBoolean(drawerCollapsedValue);
        }
        if (!setupExpandedValue.isBlank()) {
            setupExpanded = Boolean.parseBoolean(setupExpandedValue);
        }
        UUID restoredProjectId = null;
        try {
            restoredProjectId = selectedProjectIdValue.isBlank() ? null : UUID.fromString(selectedProjectIdValue);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring invalid stored project id {}", selectedProjectIdValue);
        }
        UUID finalRestoredProjectId = restoredProjectId;
        projectService.findAll().stream()
                .filter(project -> project.getId().equals(finalRestoredProjectId))
                .findFirst()
                .ifPresent(project -> selectedProject = projectService.getProject(project.getId()));
        refreshProjectList();
        if (selectedProject != null) {
            renderWorkspace();
        }
    }

    private void persistUiState(String key, String value) {
        UI.getCurrent().getPage().executeJs("window.localStorage.setItem($0, $1)", key, value);
    }

    private void persistResizableHeight(Component component, String key) {
        component.getElement().executeJs("""
                const el = this;
                const stored = window.localStorage.getItem($0);
                if (stored) {
                  el.style.height = stored;
                }
                if (!el.__cwHeightObserver) {
                  let timer;
                  el.__cwHeightObserver = new ResizeObserver(() => {
                    window.clearTimeout(timer);
                    timer = window.setTimeout(() => {
                      if (el.offsetHeight > 0) {
                        window.localStorage.setItem($0, el.offsetHeight + 'px');
                      }
                    }, 150);
                  });
                  el.__cwHeightObserver.observe(el);
                }
                """, key);
    }

    private void configureAiPrompt(TextArea prompt, Button send) {
        prompt.setHelperText("Enter sends. Ctrl+Enter or Meta+Enter adds a new line.");
        prompt.getElement().executeJs("""
                const host = this;
                const sendButton = $0;
                if (host.__cwEnterSendBound) {
                  return;
                }
                host.__cwEnterSendBound = true;
                const bind = () => {
                  const input = host.shadowRoot && host.shadowRoot.querySelector('textarea');
                  if (!input || input.__cwEnterSendBound) {
                    return;
                  }
                  input.__cwEnterSendBound = true;
                  input.addEventListener('keydown', (event) => {
                    if (event.key !== 'Enter' || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
                      return;
                    }
                    event.preventDefault();
                    sendButton.click();
                  });
                };
                requestAnimationFrame(bind);
                host.addEventListener('focusin', bind);
                """, send.getElement());
    }

    private void scrollToBottom(Component target) {
        target.getElement().executeJs("""
                requestAnimationFrame(() => {
                  this.scrollTop = this.scrollHeight;
                });
                """);
    }

    private void renderWorkspace() {
        workspace.removeAll();
        systemDrawer.removeAll();
        systemDrawer.removeClassName("open");
        selectedProjectName.setText(selectedProject.getName());

        workspace.add(monitoring(), projectAiSection(), contactsSection(), setupSection());
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

    private Div projectAiSection() {
        Div section = section("Project AI", "Ask project-level questions using read-only project tools.");
        section.addClassName("cw-ai-section");
        Div history = new Div();
        history.addClassName("cw-chat-history");
        persistResizableHeight(history, STORAGE_PROJECT_CHAT_HEIGHT);
        Select<AiChatSession> sessions = new Select<>();
        sessions.setLabel("Chat");
        sessions.setItemLabelGenerator(this::chatSessionLabel);
        loadProjectChatSessions(sessions);
        renderProjectChat(history, sessions.getValue());

        TextArea question = new TextArea("Ask about this project");
        question.setWidthFull();
        Span status = new Span();
        status.addClassName("cw-muted");
        Button newChat = new Button("New chat", VaadinIcon.PLUS.create(), event -> {
            AiChatSession session = localAiService.newProjectChat(selectedProject.getId());
            selectChatSession(sessions, loadProjectChatSessions(sessions), session.getId());
            renderProjectChat(history, sessions.getValue());
        });
        Button compact = new Button("Compact", VaadinIcon.FILE_TEXT.create(), event -> {
            if (sessions.getValue() != null) {
                localAiService.compactSession(sessions.getValue().getId());
                renderProjectChat(history, sessions.getValue());
                Notification.show("Chat compacted", 2500, Position.BOTTOM_START);
            }
        });
        Button archive = new Button("Archive", VaadinIcon.ARCHIVE.create(), event -> {
            if (sessions.getValue() != null) {
                localAiService.archiveSession(sessions.getValue().getId());
                loadProjectChatSessions(sessions);
                renderProjectChat(history, sessions.getValue());
            }
        });
        Button delete = new Button("Delete", VaadinIcon.TRASH.create(), event -> {
            if (sessions.getValue() != null) {
                localAiService.deleteSession(sessions.getValue().getId());
                loadProjectChatSessions(sessions);
                renderProjectChat(history, sessions.getValue());
            }
        });
        Button rename = new Button("Rename", VaadinIcon.EDIT.create(), event ->
                openRenameChatDialog(sessions, () -> loadProjectChatSessions(sessions)));
        Button systemPrompt = new Button("System prompt", VaadinIcon.COG.create(), event -> openSystemPromptDialog());
        sessions.addValueChangeListener(event -> renderProjectChat(history, event.getValue()));
        Button send = new Button("Send", VaadinIcon.COMMENT_ELLIPSIS.create());
        send.addClickListener(event -> {
            String prompt = blankToNull(question.getValue());
            if (prompt == null) {
                return;
            }
            send.setEnabled(false);
            status.setText("Asking AI...");
            try {
                AiChatSession session = sessions.getValue();
                if (session == null) {
                    session = localAiService.newProjectChat(selectedProject.getId());
                    selectChatSession(sessions, loadProjectChatSessions(sessions), session.getId());
                }
                localAiService.askProject(selectedProject.getId(), session.getId(), prompt);
                question.clear();
                renderProjectChat(history, session);
                selectChatSession(sessions, loadProjectChatSessions(sessions), session.getId());
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("Project AI failed", ex), 5000, Position.BOTTOM_START);
            } finally {
                send.setEnabled(true);
                status.setText("");
            }
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        configureAiPrompt(question, send);
        HorizontalLayout sessionControls = new HorizontalLayout(sessions, newChat, rename, compact, archive, delete);
        sessionControls.setAlignItems(Alignment.BASELINE);
        HorizontalLayout controls = new HorizontalLayout(send, systemPrompt, status);
        controls.setAlignItems(Alignment.CENTER);
        section.add(sessionControls, history, question, controls);
        return section;
    }

    private void openAiModelDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("AI model");
        dialog.setWidth("520px");
        AppAiSettings settings = appAiSettingsService.current();

        Select<AiProvider> provider = new Select<>();
        provider.setLabel("Provider");
        provider.setWidthFull();
        provider.setItems(AiProvider.LOCAL_OLLAMA, AiProvider.GOOGLE_GENAI);
        provider.setItemLabelGenerator(this::aiProviderLabel);
        provider.setValue(settings.getProvider());

        Select<String> model = new Select<>();
        model.setLabel("Model");
        model.setWidthFull();
        model.setEmptySelectionAllowed(false);
        loadAiModels(provider.getValue(), settings.getModel(), model);

        NumberField temperature = new NumberField("Temperature");
        temperature.setWidthFull();
        temperature.setMin(AppAiSettingsService.MIN_AI_TEMPERATURE);
        temperature.setMax(AppAiSettingsService.MAX_AI_TEMPERATURE);
        temperature.setStep(0.1);
        temperature.setValue(settings.getTemperature());

        provider.addValueChangeListener(event -> loadAiModels(event.getValue(), model.getValue(), model));
        Button refreshModels = new Button("Refresh models", VaadinIcon.REFRESH.create(),
                event -> loadAiModels(provider.getValue(), model.getValue(), model));

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                appAiSettingsService.save(provider.getValue(), model.getValue(), temperature.getValue());
                Notification.show("AI model saved", 2500, Position.BOTTOM_START);
                dialog.close();
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("AI model save failed", ex), 5000, Position.BOTTOM_START);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Div content = new Div(provider, model, temperature, refreshModels);
        content.addClassName("cw-dialog-form");
        dialog.add(content);
        dialog.getFooter().add(save, new Button("Close", event -> dialog.close()));
        dialog.open();
    }

    private void loadAiModels(AiProvider provider, String currentModel, Select<String> model) {
        String current = currentModel == null || currentModel.isBlank()
                ? AppAiSettingsService.FALLBACK_LOCAL_AI_MODEL
                : currentModel.trim();
        try {
            List<String> models = modelCatalogService.modelsFor(provider, current);
            if (models.isEmpty()) {
                Notification.show("No AI models loaded", 3500, Position.BOTTOM_START);
            } else if (provider != AiProvider.GOOGLE_GENAI && !models.contains(current)) {
                models = new ArrayList<>(models);
                models.add(current);
            }
            model.setItems(models);
            model.setValue(models.contains(current) ? current : models.stream().findFirst().orElse(null));
        } catch (Exception ex) {
            model.setItems(current);
            model.setValue(current);
            Notification.show(rootCauseMessage("Model refresh failed", ex), 5000, Position.BOTTOM_START);
        }
    }

    private void openSystemPromptDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Project system prompt");
        dialog.setWidth("720px");

        TextArea prompt = new TextArea("System prompt");
        prompt.setWidthFull();
        prompt.setMinHeight("260px");
        prompt.setValue(projectService.aiSystemPrompt(selectedProject));

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                selectedProject = projectService.updateAiSystemPrompt(selectedProject.getId(), prompt.getValue());
                Notification.show("System prompt saved", 2500, Position.BOTTOM_START);
                dialog.close();
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("System prompt save failed", ex), 5000, Position.BOTTOM_START);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button reset = new Button("Reset", VaadinIcon.REFRESH.create(), event -> {
            try {
                selectedProject = projectService.resetAiSystemPrompt(selectedProject.getId());
                prompt.setValue(projectService.aiSystemPrompt(selectedProject));
                Notification.show("System prompt reset", 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("System prompt reset failed", ex), 5000, Position.BOTTOM_START);
            }
        });

        Div content = new Div(prompt);
        content.addClassName("cw-dialog-form");
        dialog.add(content);
        dialog.getFooter().add(reset, save, new Button("Close", event -> dialog.close()));
        dialog.open();
    }

    private List<AiChatSession> loadProjectChatSessions(Select<AiChatSession> sessions) {
        List<AiChatSession> items = localAiService.projectSessions(selectedProject.getId());
        sessions.setItems(items);
        sessions.setValue(items.stream().findFirst().orElse(null));
        return items;
    }

    private void renderProjectChat(Div history, AiChatSession session) {
        if (session == null) {
            renderChatMessages(history, List.of());
            return;
        }
        renderChatMessages(history, localAiService.projectMessages(selectedProject.getId(), session.getId()));
    }

    private String chatSessionLabel(AiChatSession session) {
        if (session == null) {
            return "No chat";
        }
        String title = session.getTitle();
        return title == null || title.isBlank() ? session.getId().toString() : title;
    }

    private void selectChatSession(Select<AiChatSession> sessions, List<AiChatSession> items, UUID sessionId) {
        if (sessionId == null) {
            sessions.setValue(null);
            return;
        }
        items.stream()
                .filter(session -> sessionId.equals(session.getId()))
                .findFirst()
                .ifPresent(sessions::setValue);
    }

    private void openRenameChatDialog(Select<AiChatSession> sessions, Supplier<List<AiChatSession>> reload) {
        AiChatSession session = sessions.getValue();
        if (session == null) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Rename chat");
        TextField title = new TextField("Chat name");
        title.setWidthFull();
        title.setMaxLength(120);
        title.setValue(value(session.getTitle()));
        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                AiChatSession renamed = localAiService.renameSession(session.getId(), title.getValue());
                selectChatSession(sessions, reload.get(), renamed.getId());
                Notification.show("Chat renamed", 2500, Position.BOTTOM_START);
                dialog.close();
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("Rename failed", ex), 5000, Position.BOTTOM_START);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div content = new Div(title);
        content.addClassName("cw-dialog-form");
        dialog.add(content);
        dialog.getFooter().add(save, new Button("Cancel", event -> dialog.close()));
        dialog.open();
    }

    private String aiProviderLabel(AiProvider provider) {
        return provider == AiProvider.GOOGLE_GENAI ? "Google GenAI" : "Local Ollama";
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
        filter.setValueChangeTimeout(300);
        filter.addValueChangeListener(event -> refreshContacts(event.getValue()));

        Button sync = new Button("Sync inbox", VaadinIcon.INBOX.create(), event -> runInboxSync());
        Button send = new Button("Send...", VaadinIcon.PAPERPLANE.create(), event -> openSendDialog());
        Button add = new Button("Add row", VaadinIcon.PLUS.create(), event -> openAddContactDialog());
        Button edit = new Button("Edit selected", VaadinIcon.EDIT.create(), event -> editSelectedContact());
        Button preview = new Button("Preview letter", VaadinIcon.FILE_TEXT.create(), event -> previewSelectedContact());
        Button conversation = new Button("Conversation", VaadinIcon.COMMENTS.create(), event -> openSelectedConversation());
        Button delete = new Button("Delete selected", VaadinIcon.TRASH.create(), event -> confirmDeleteSelectedContact());
        Span selectedCount = new Span("Selected: 0");
        selectedCount.addClassName("cw-muted");

        HorizontalLayout toolbar = new HorizontalLayout(filter, selectedCount, add, edit, preview, conversation, delete, sync, send);
        toolbar.addClassName("cw-toolbar");
        toolbar.setWidthFull();
        toolbar.expand(filter);

        contactsGrid = new Grid<>(Contact.class, false);
        contactsGrid.addClassName("cw-contacts-grid");
        rebuildContactGridColumns();
        contactsGrid.setHeightFull();
        Div contactsGridPanel = new Div(contactsGrid);
        contactsGridPanel.addClassName("cw-resizable-grid-panel");
        persistResizableHeight(contactsGridPanel, STORAGE_CONTACTS_GRID_HEIGHT_PREFIX + selectedProject.getId());
        contactsGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        contactsGrid.addSelectionListener(event -> {
            int count = event.getAllSelectedItems().size();
            boolean single = count == 1;
            selectedCount.setText("Selected: " + count);
            edit.setEnabled(single);
            preview.setEnabled(single);
            conversation.setEnabled(single);
            delete.setEnabled(count > 0);
        });
        contactsGrid.addItemDoubleClickListener(event -> openConversationDialog(event.getItem()));
        edit.setEnabled(false);
        preview.setEnabled(false);
        conversation.setEnabled(false);
        delete.setEnabled(false);

        section.add(toolbar, contactsGridPanel);
        return section;
    }

    private void rebuildContactGridColumns() {
        contactsGrid.removeAllColumns();
        configureContactColumns();
        configureContactColumn(contactsGrid.addColumn(Contact::getStatus), "status:Status", "Status", 0);
        configureContactColumn(contactsGrid.addColumn(Contact::getSentAt), "sent:Sent", "Sent", 0);
        contactsGrid.addColumnResizeListener(event -> {
            Grid.Column<Contact> column = event.getResizedColumn();
            if (selectedProject == null || column.getKey() == null) {
                return;
            }
            persistUiState(gridWidthStorageKey(column.getKey()), column.getWidth());
        });
    }

    private void configureContactColumns() {
        List<ProjectContactColumn> columns = projectContactColumnService.findVisibleColumns(selectedProject.getId());
        if (columns.isEmpty()) {
            configureContactColumn(contactsGrid.addColumn(Contact::getContactName), "contact:Contact", "Contact", 1);
            configureContactColumn(contactsGrid.addColumn(Contact::getEmail), "email:Email", "Email", 1);
            configureContactColumn(contactsGrid.addColumn(Contact::getOrganizationName), "organization:Organization", "Organization", 1);
            return;
        }
        for (ProjectContactColumn column : columns) {
            configureContactColumn(
                    contactsGrid.addColumn(contact -> valueForColumn(contact, column)),
                    "custom:" + column.getColumnKey() + ":" + column.getDisplayLabel(),
                    column.getDisplayLabel(),
                    1
            );
        }
    }

    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
            "email", "email",
            "organization", "organizationName",
            "status", "status"
    );

    private void configureContactColumn(Grid.Column<Contact> column, String key, String header, int flexGrow) {
        column.setKey(key)
                .setHeader(header)
                .setAutoWidth(true)
                .setFlexGrow(flexGrow)
                .setResizable(true);
        String columnPrefix = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
        String sortProperty = SORTABLE_COLUMNS.get(columnPrefix);
        if (sortProperty != null) {
            column.setSortable(true);
            column.setSortProperty(sortProperty);
        }
        column.getElement().executeJs("""
                const width = window.localStorage.getItem($0);
                if (width) {
                    this.width = width;
                    this.autoWidth = false;
                }
                """, gridWidthStorageKey(key));
    }

    private String gridWidthStorageKey(String columnKey) {
        return STORAGE_GRID_WIDTH_PREFIX + selectedProject.getId() + "." + columnKey;
    }

    private Div setupSection() {
        Div section = new Div();
        section.addClassName("cw-section");
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
        Div setupBody = new Div(form, uploads, readinessState, gmailSentFolderWarning(), save);
        setupBody.addClassName("cw-setup-body");
        Details details = new Details(setupSummary(), setupBody);
        details.setOpened(setupExpanded);
        details.addOpenedChangeListener(event -> {
            setupExpanded = event.isOpened();
            persistUiState(STORAGE_SETUP_EXPANDED, Boolean.toString(setupExpanded));
        });
        section.add(details);
        return section;
    }

    private Div setupSummary() {
        Div summary = new Div();
        summary.addClassName("cw-setup-summary");
        Div title = new Div();
        title.add(new H3("Project setup"), new Span(value(selectedProject.getDescription())));
        title.addClassName("cw-setup-summary-title");
        summary.add(title);
        summary.add(pill(selectedProject.getStatus().name(), selectedProject.getStatus() == ProjectStatus.ACTIVE ? "success" : "warning"));
        summary.add(pill(projectAssetService.activeLetter(selectedProject.getId()).isPresent() ? "Letter ready" : "Letter missing",
                projectAssetService.activeLetter(selectedProject.getId()).isPresent() ? "success" : "warning"));
        summary.add(pill(hasProjectCredential(selectedProject.getGmailUsername()) ? "Mailbox ready" : "Mailbox missing",
                hasProjectCredential(selectedProject.getGmailUsername()) ? "success" : "warning"));
        return summary;
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
        if (selectedProject.getMailTransport() == MailTransportType.GMAIL) {
            if (!hasProjectCredential(selectedProject.getGmailUsername())
                    || !hasProjectCredential(selectedProject.getGmailAppPassword())) {
                reasons.add("missing Gmail credentials");
            }
        } else {
            String brevoKey = appProperties.mail().brevo().apiKey();
            if (brevoKey == null || brevoKey.isBlank()) {
                reasons.add("BREVO_API_KEY not configured");
            }
        }
        if (reasons.isEmpty()) {
            readinessState.setText("Ready for sending");
            readinessState.addClassName("cw-pill-success");
        } else {
            readinessState.setText("Not ready: " + String.join(", ", reasons));
            readinessState.addClassName("cw-pill-warning");
        }
    }

    private void updateTransportFieldsVisibility() {
        boolean gmail = mailTransportSelect.getValue() == MailTransportType.GMAIL;
        gmailUsername.setVisible(gmail);
        gmailAppPassword.setVisible(gmail);
        brevoInfo.setVisible(!gmail);
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
        Div row = new Div(file);
        if (asset.getType() == ProjectAssetType.LETTER_TEMPLATE) {
            Button edit = new Button("Edit", VaadinIcon.EDIT.create(), event ->
                    UI.getCurrent().getPage().open("/onlyoffice/projects/" + selectedProject.getId() + "/editor", "_blank"));
            row.add(edit);
        }
        row.add(remove);
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
        mailTransportSelect.setLabel("Mail transport");
        mailTransportSelect.setItems(MailTransportType.values());
        mailTransportSelect.addValueChangeListener(event -> updateTransportFieldsVisibility());
        brevoInfo.addClassName("cw-muted");
        sendDelayMs.setMin(0);
        sendDelayMs.setStep(500);
        maxMessagesPerBatch.setMin(1);
        inboxSyncCron.setRequiredIndicatorVisible(true);
        mailFrom.setReadOnly(true);
        mailFromName.setReadOnly(true);
        senderAliasStatus.addClassName("cw-muted");

        Button health = new Button("Check mailbox", VaadinIcon.CONNECT.create(), event -> checkMailbox());
        Button syncAlias = new Button("Sync Gmail sender alias", VaadinIcon.USER_CHECK.create(), event -> syncGmailSenderAlias());
        Button save = new Button("Save system settings", VaadinIcon.CHECK.create(), event -> saveProjectSettings(true));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button close = new Button("Close", VaadinIcon.CLOSE_SMALL.create(), event -> systemDrawer.removeClassName("open"));

        VerticalLayout content = new VerticalLayout(title, helper, gmailSentFolderWarning(), projectStatus,
                mailTransportSelect, brevoInfo,
                mailFromName, mailFrom, senderAliasStatus, gmailUsername,
                gmailAppPassword, sendDelayMs, maxMessagesPerBatch, inboxSyncCron, health, syncAlias, save, close);
        updateTransportFieldsVisibility();
        content.setPadding(false);
        content.setSpacing(true);
        systemDrawer.add(content);
    }

    private void openAdminDialog() {
        currentUserService.requireAdmin();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Admin");
        dialog.setResizable(true);
        dialog.setWidth("900px");
        dialog.setMaxWidth("95vw");
        dialog.setMinHeight("520px");
        dialog.setMaxHeight("90vh");

        Component content = adminUserPanel();
        dialog.add(content);
        dialog.getFooter().add(new Button("Close", event -> dialog.close()));
        dialog.open();
    }

    private Component adminUserPanel() {
        Div panel = section("Users", "Create users, set roles, and assign active projects.");
        Grid<AppUser> userGrid = new Grid<>(AppUser.class, false);
        userGrid.addColumn(AppUser::getLogin).setHeader("Login").setAutoWidth(true);
        userGrid.addColumn(AppUser::getName).setHeader("Name").setAutoWidth(true);
        userGrid.addColumn(AppUser::getRole).setHeader("Role").setAutoWidth(true);
        userGrid.addColumn(AppUser::isActive).setHeader("Active").setAutoWidth(true);
        userGrid.addColumn(user -> value(user.getEmail())).setHeader("Email").setAutoWidth(true);
        userGrid.setWidthFull();
        userGrid.setHeight("240px");

        Select<AppUser> users = new Select<>();
        users.setLabel("User");
        users.setWidthFull();
        users.setItemLabelGenerator(user -> user == null ? "New user" : user.getLogin());

        TextField name = new TextField("Name");
        TextField login = new TextField("Login");
        PasswordField password = new PasswordField("Password");
        EmailField email = new EmailField("Email");
        Select<AppRole> role = new Select<>();
        role.setLabel("Role");
        role.setItems(AppRole.values());
        role.setValue(AppRole.USER);
        Checkbox active = new Checkbox("Active", true);
        MultiSelectComboBox<Project> assignedProjects = new MultiSelectComboBox<>("Assigned projects");
        assignedProjects.setWidthFull();
        List<Project> visibleProjects = projectService.findAllForAdmin();
        assignedProjects.setItems(visibleProjects);
        assignedProjects.setItemLabelGenerator(Project::getName);

        java.util.concurrent.atomic.AtomicReference<List<AppUser>> loadedUsers =
                new java.util.concurrent.atomic.AtomicReference<>(List.of());
        java.util.concurrent.atomic.AtomicBoolean syncingSelection = new java.util.concurrent.atomic.AtomicBoolean(false);

        Runnable clear = () -> {
            users.setValue(null);
            userGrid.deselectAll();
            name.clear();
            login.clear();
            password.clear();
            email.clear();
            role.setValue(AppRole.USER);
            active.setValue(true);
            assignedProjects.clear();
        };
        Runnable reloadUsers = () -> {
            List<AppUser> allUsers = appUserService.findAll();
            loadedUsers.set(allUsers);
            userGrid.setItems(allUsers);
            users.setItems(allUsers);
        };
        reloadUsers.run();

        java.util.function.Consumer<AppUser> selectUser = user -> {
            if (user == null) {
                clear.run();
                return;
            }
            AppUser loadedUser = loadedUsers.get().stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), user.getId()))
                    .findFirst()
                    .orElse(user);
            syncingSelection.set(true);
            try {
                if (users.getValue() == null || !Objects.equals(users.getValue().getId(), loadedUser.getId())) {
                    users.setValue(loadedUser);
                }
                userGrid.select(loadedUser);
            } finally {
                syncingSelection.set(false);
            }
            name.setValue(value(loadedUser.getName()));
            login.setValue(value(loadedUser.getLogin()));
            password.clear();
            email.setValue(value(loadedUser.getEmail()));
            role.setValue(loadedUser.getRole());
            active.setValue(loadedUser.isActive());
            Set<UUID> selectedProjectIds = loadedUser.getAssignedProjects().stream()
                    .map(Project::getId)
                    .collect(java.util.stream.Collectors.toSet());
            assignedProjects.setValue(visibleProjects.stream()
                    .filter(project -> selectedProjectIds.contains(project.getId()))
                    .collect(java.util.stream.Collectors.toSet()));
        };

        userGrid.asSingleSelect().addValueChangeListener(event -> {
            if (!syncingSelection.get()) {
                selectUser.accept(event.getValue());
            }
        });
        users.addValueChangeListener(event -> {
            if (syncingSelection.get()) {
                return;
            }
            AppUser user = event.getValue();
            if (user == null) {
                clear.run();
                return;
            }
            selectUser.accept(user);
        });

        Button save = new Button("Save user", VaadinIcon.CHECK.create(), event -> {
            try {
                AppUser selected = users.getValue();
                AppUser saved = appUserService.saveUser(
                        selected == null ? null : selected.getId(),
                        name.getValue(),
                        login.getValue(),
                        password.getValue(),
                        email.getValue(),
                        role.getValue(),
                        active.getValue(),
                        assignedProjects.getValue().stream().map(Project::getId).collect(java.util.stream.Collectors.toSet())
                );
                reloadUsers.run();
                loadedUsers.get().stream()
                        .filter(user -> user.getId().equals(saved.getId()))
                        .findFirst()
                        .ifPresent(selectUser);
                Notification.show("User saved", 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("User save failed", ex), 5000, Position.BOTTOM_START);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button newUser = new Button("New user", VaadinIcon.PLUS.create(), event -> clear.run());
        Button activate = new Button("Activate", VaadinIcon.CHECK_CIRCLE.create(), event -> {
            if (users.getValue() != null) {
                UUID userId = users.getValue().getId();
                appUserService.setActive(users.getValue().getId(), true);
                reloadUsers.run();
                loadedUsers.get().stream()
                        .filter(user -> user.getId().equals(userId))
                        .findFirst()
                        .ifPresent(selectUser);
            }
        });
        Button deactivate = new Button("Deactivate", VaadinIcon.BAN.create(), event -> {
            if (users.getValue() != null) {
                UUID userId = users.getValue().getId();
                appUserService.setActive(users.getValue().getId(), false);
                reloadUsers.run();
                loadedUsers.get().stream()
                        .filter(user -> user.getId().equals(userId))
                        .findFirst()
                        .ifPresent(selectUser);
            }
        });
        Button delete = new Button("Delete", VaadinIcon.TRASH.create(), event -> {
            if (users.getValue() != null) {
                appUserService.deleteUser(users.getValue().getId());
                reloadUsers.run();
                clear.run();
            }
        });
        HorizontalLayout actions = new HorizontalLayout(newUser, save, activate, deactivate, delete);
        actions.setAlignItems(Alignment.BASELINE);
        panel.add(userGrid, users, name, login, password, email, role, active, assignedProjects, actions);
        return panel;
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
        mailTransportSelect.setItems(MailTransportType.values());
        mailTransportSelect.setValue(project.getMailTransport() == null ? MailTransportType.BREVO : project.getMailTransport());
        updateTransportFieldsVisibility();
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
            updates.setMailTransport(mailTransportSelect.getValue());
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
        UUID projectId = selectedProject.getId();
        long totalCount = contactService.countContacts(projectId, value);
        contactsTitle.setText("Contacts (" + totalCount + " records)");
        contactsGrid.setItems(query -> {
            if (selectedProject == null || !selectedProject.getId().equals(projectId)) {
                return java.util.stream.Stream.empty();
            }
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                    query.getPage(), query.getPageSize(), toSpringSort(query.getSortOrders())
            );
            return contactService.searchContacts(projectId, value, pageable).getContent().stream();
        });
    }

    private static final java.util.Set<String> ALLOWED_SORT_PROPERTIES = java.util.Set.copyOf(SORTABLE_COLUMNS.values());

    private org.springframework.data.domain.Sort toSpringSort(List<com.vaadin.flow.data.provider.QuerySortOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return org.springframework.data.domain.Sort.by("createdAt").descending();
        }
        return org.springframework.data.domain.Sort.by(orders.stream()
                .filter(order -> ALLOWED_SORT_PROPERTIES.contains(order.getSorted()))
                .map(order -> order.getDirection() == com.vaadin.flow.data.provider.SortDirection.ASCENDING
                        ? org.springframework.data.domain.Sort.Order.asc(order.getSorted())
                        : org.springframework.data.domain.Sort.Order.desc(order.getSorted()))
                .toList());
    }

    private void editSelectedContact() {
        Contact contact = singleSelectedContact();
        if (contact != null) {
            openContactDialog(contact);
        }
    }

    private void openSelectedConversation() {
        Contact contact = singleSelectedContact();
        if (contact != null) {
            openConversationDialog(contact);
        }
    }

    private void previewSelectedContact() {
        Contact contact = singleSelectedContact();
        if (contact == null) {
            return;
        }
        String url = "/api/v1/projects/" + selectedProject.getId() + "/contacts/" + contact.getId() + "/letter/pdf";
        UI.getCurrent().getPage().open(url, "_blank");
    }

    private void openSendDialog() {
        List<Contact> selectedContacts = selectedContacts();
        Contact contact = selectedContacts.size() == 1 ? selectedContacts.get(0) : null;
        SendStatusResponse status = sendCoordinator.getStatus(selectedProject.getId());
        List<String> blockers = sendingBlockers();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Send");
        dialog.setWidth("640px");

        Select<SendMode> mode = new Select<>();
        mode.setLabel("Send mode");
        mode.setItems(SendMode.SELECTED_CONTACT, SendMode.BATCH);
        mode.setItemLabelGenerator(SendMode::label);
        mode.setValue(selectedContacts.isEmpty() ? SendMode.BATCH : SendMode.SELECTED_CONTACT);

        Span error = new Span();
        error.addClassName("cw-error");

        Div body = new Div();
        body.addClassName("cw-dialog-form");
        body.add(mode, sendReadinessSummary(blockers), sendCounterSummary(status), batchRuleSummary(status),
                selectedContactsSummary(selectedContacts), attachmentSummary(), error);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button sendContact = new Button(contactRequiresForce(contact) ? "Send again" : "Send selected", event -> {
            if (selectedContacts.isEmpty()) {
                error.setText("Select one or more contacts before sending.");
                return;
            }
            boolean force = selectedContacts.size() == 1 && contactRequiresForce(contact);
            if (executeSendContacts(selectedContacts, force, error)) {
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
        sendContact.setEnabled(!selectedContacts.isEmpty() && blockers.isEmpty());
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
        return executeSendContacts(List.of(contact), force, error);
    }

    private boolean executeSendContacts(List<Contact> contacts, boolean force, Span error) {
        try {
            for (Contact contact : contacts) {
                sendCoordinator.sendSingle(selectedProject.getId(), contact.getId(), force);
            }
            refreshContacts();
            updateReadinessState();
            Notification.show("Selected send processed: " + contacts.size(), 2500, Position.BOTTOM_START);
            return true;
        } catch (Exception ex) {
            refreshContacts();
            updateReadinessState();
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
            updateReadinessState();
            Notification.show("Batch started: " + eligibleBatchCount + " eligible contacts", 2500, Position.BOTTOM_START);
            return true;
        } catch (Exception ex) {
            refreshContacts();
            updateReadinessState();
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

    private Div selectedContactsSummary(List<Contact> contacts) {
        Div summary = new Div();
        summary.addClassName("cw-send-summary");
        if (contacts.isEmpty()) {
            summary.add(new Span("Selected contacts: none"));
            return summary;
        }
        if (contacts.size() > 1) {
            summary.add(new Span("Selected contacts: " + contacts.size()));
            summary.add(new Span("Bulk selected send processes NEW and SEND_FAILED contacts; already handled contacts are skipped."));
            return summary;
        }
        Contact contact = contacts.get(0);
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
        if (!hasProjectCredential(selectedProject.getGmailUsername())
                || !hasProjectCredential(selectedProject.getGmailAppPassword())) {
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
        List<Contact> contacts = selectedContacts();
        if (contacts.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delete contact");
        dialog.add(new Span(contacts.size() == 1
                ? "Delete " + contacts.get(0).getEmail() + "? History is kept when the contact was already used."
                : "Delete " + contacts.size() + " selected contacts? History is kept when contacts were already used."));
        Button cancel = new Button("Cancel", event -> dialog.close());
        Button delete = new Button("Delete", event -> {
            try {
                contacts.forEach(contact -> contactService.deleteContact(selectedProject.getId(), contact.getId()));
                refreshContacts();
                dialog.close();
                Notification.show("Contact(s) deleted: " + contacts.size(), 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, delete);
        dialog.open();
    }

    private Contact singleSelectedContact() {
        List<Contact> contacts = selectedContacts();
        return contacts.size() == 1 ? contacts.get(0) : null;
    }

    private List<Contact> selectedContacts() {
        if (contactsGrid == null) {
            return List.of();
        }
        return contactsGrid.getSelectedItems().stream().toList();
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

    private void openConversationDialog(Contact contact) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Conversation");
        dialog.setWidth("860px");
        dialog.setMaxWidth("95vw");
        dialog.setResizable(true);

        Div header = new Div();
        header.addClassName("cw-conversation-header");
        header.add(new H3(value(contact.getContactName())),
                new Span(value(contact.getEmail())),
                new Span(value(contact.getOrganizationName())),
                pill(contact.getStatus().name(), contact.getStatus() == ContactStatus.REPLIED ? "success" : "warning"));

        Div messages = new Div();
        messages.addClassName("cw-conversation-list");
        persistResizableHeight(messages, STORAGE_CONVERSATION_HEIGHT);

        Select<AiChatSession> chatSessions = new Select<>();
        chatSessions.setLabel("Chat");
        chatSessions.setItemLabelGenerator(this::chatSessionLabel);
        loadContactChatSessions(chatSessions, contact);
        Supplier<String> aiDraft = () -> latestAssistantDraft(contact, chatSessions.getValue());
        renderConversationMessages(messages, contact, aiDraft);

        Div summary = new Div();
        summary.addClassName("cw-summary-box");
        persistResizableHeight(summary, STORAGE_CONVERSATION_SUMMARY_HEIGHT);
        renderSummary(summary, contact);

        Span aiStatus = new Span();
        aiStatus.addClassName("cw-muted");
        Span syncStatus = new Span();
        syncStatus.addClassName("cw-muted");
        Button syncContactInbox = new Button("Sync inbox", VaadinIcon.INBOX.create(), event -> {
            syncStatus.setText("Syncing inbox for this contact...");
            int before = mailboxMessageRepository.findByProjectIdAndContactIdOrderByServiceDateAsc(
                    selectedProject.getId(), contact.getId()).size();
            try {
                inboxSyncService.syncInbox(selectedProject.getId(), contact.getId());
                Contact refreshed = contactService.getContact(selectedProject.getId(), contact.getId());
                renderConversationMessages(messages, refreshed, aiDraft);
                renderSummary(summary, refreshed);
                refreshContacts();
                int after = mailboxMessageRepository.findByProjectIdAndContactIdOrderByServiceDateAsc(
                        selectedProject.getId(), contact.getId()).size();
                String message = after > before
                        ? "Synced " + (after - before) + " new message(s) for this contact"
                        : "No new messages for this contact";
                syncStatus.setText(message);
                Notification.show(message, 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                syncStatus.setText("");
                Notification.show(rootCauseMessage("Contact inbox sync failed", ex), 5000, Position.BOTTOM_START);
            }
        });
        Button summarize = new Button("Summarize", VaadinIcon.MAGIC.create());
        summarize.addClickListener(event -> {
            summarize.setEnabled(false);
            aiStatus.setText("Summarizing...");
            try {
                localAiService.summarizeContact(selectedProject.getId(), contact.getId());
                renderSummary(summary, contact);
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("Summary failed", ex), 5000, Position.BOTTOM_START);
            } finally {
                summarize.setEnabled(true);
                aiStatus.setText("");
            }
        });
        Button newMessage = new Button("New message", VaadinIcon.ENVELOPE.create(),
                event -> openContactMessageDialog(contact, null, "", messages, aiDraft));
        Button newFromAi = new Button("New from AI draft", VaadinIcon.MAGIC.create(),
                event -> openContactMessageDialog(contact, null, aiDraft.get(), messages, aiDraft));

        Div chatHistory = new Div();
        chatHistory.addClassName("cw-chat-history");
        persistResizableHeight(chatHistory, STORAGE_CONTACT_CHAT_HEIGHT);
        renderContactChat(chatHistory, contact, chatSessions.getValue());
        TextArea question = new TextArea("Ask about this contact");
        question.setWidthFull();
        Button newChat = new Button("New chat", VaadinIcon.PLUS.create(), event -> {
            AiChatSession session = localAiService.newContactChat(selectedProject.getId(), contact.getId());
            selectChatSession(chatSessions, loadContactChatSessions(chatSessions, contact), session.getId());
            renderContactChat(chatHistory, contact, chatSessions.getValue());
        });
        Button compact = new Button("Compact", VaadinIcon.FILE_TEXT.create(), event -> {
            if (chatSessions.getValue() != null) {
                localAiService.compactSession(chatSessions.getValue().getId());
                renderContactChat(chatHistory, contact, chatSessions.getValue());
                Notification.show("Chat compacted", 2500, Position.BOTTOM_START);
            }
        });
        Button archive = new Button("Archive", VaadinIcon.ARCHIVE.create(), event -> {
            if (chatSessions.getValue() != null) {
                localAiService.archiveSession(chatSessions.getValue().getId());
                loadContactChatSessions(chatSessions, contact);
                renderContactChat(chatHistory, contact, chatSessions.getValue());
            }
        });
        Button deleteChat = new Button("Delete", VaadinIcon.TRASH.create(), event -> {
            if (chatSessions.getValue() != null) {
                localAiService.deleteSession(chatSessions.getValue().getId());
                loadContactChatSessions(chatSessions, contact);
                renderContactChat(chatHistory, contact, chatSessions.getValue());
            }
        });
        Button rename = new Button("Rename", VaadinIcon.EDIT.create(), event ->
                openRenameChatDialog(chatSessions, () -> loadContactChatSessions(chatSessions, contact)));
        chatSessions.addValueChangeListener(event -> renderContactChat(chatHistory, contact, event.getValue()));
        Button send = new Button("Send", VaadinIcon.COMMENT_ELLIPSIS.create());
        send.addClickListener(event -> {
            String prompt = blankToNull(question.getValue());
            if (prompt == null) {
                return;
            }
            send.setEnabled(false);
            aiStatus.setText("Asking AI...");
            try {
                AiChatSession session = chatSessions.getValue();
                if (session == null) {
                    session = localAiService.newContactChat(selectedProject.getId(), contact.getId());
                    selectChatSession(chatSessions, loadContactChatSessions(chatSessions, contact), session.getId());
                }
                localAiService.askContact(selectedProject.getId(), contact.getId(), session.getId(), prompt);
                question.clear();
                renderContactChat(chatHistory, contact, session);
                selectChatSession(chatSessions, loadContactChatSessions(chatSessions, contact), session.getId());
            } catch (Exception ex) {
                Notification.show(rootCauseMessage("Contact AI failed", ex), 5000, Position.BOTTOM_START);
            } finally {
                send.setEnabled(true);
                aiStatus.setText("");
            }
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        configureAiPrompt(question, send);

        HorizontalLayout chatControls = new HorizontalLayout(chatSessions, newChat, rename, compact, archive, deleteChat);
        chatControls.setAlignItems(Alignment.BASELINE);
        HorizontalLayout messageControls = new HorizontalLayout(syncContactInbox, newMessage, newFromAi, syncStatus);
        messageControls.setAlignItems(Alignment.CENTER);
        Div content = new Div(header, messageControls, messages, summary, summarize, aiStatus,
                chatControls, chatHistory, question, send);
        content.addClassName("cw-conversation-panel");
        dialog.add(content);
        dialog.getFooter().add(new Button("Close", event -> dialog.close()));
        dialog.open();
    }

    private void renderConversationMessages(Div target, Contact contact) {
        renderConversationMessages(target, contact, () -> "");
    }

    private void renderConversationMessages(Div target, Contact contact, Supplier<String> aiDraft) {
        target.removeAll();
        List<MailboxMessage> messages = mailboxMessageRepository.findByProjectIdAndContactIdOrderByServiceDateAsc(
                selectedProject.getId(), contact.getId());
        if (messages.isEmpty()) {
            Span empty = new Span("No synced mailbox messages.");
            empty.addClassName("cw-muted");
            target.add(empty);
            return;
        }
        for (MailboxMessage message : messages) {
            Div item = new Div();
            item.addClassName("cw-conversation-message");
            Button reply = new Button("Reply", VaadinIcon.REPLY.create(),
                    event -> openContactMessageDialog(contact, message, "", target, aiDraft));
            Button replyFromAi = new Button("Reply with AI draft", VaadinIcon.MAGIC.create(),
                    event -> openContactMessageDialog(contact, message, aiDraft.get(), target, aiDraft));
            item.add(new Span(value(message.getServiceDate())),
                    pill(message.getDirection().name(), message.getDirection().name().equals("SENT") ? "success" : "warning"),
                    new Span(value(message.getSubject())),
                    new Span(value(message.getBodyText())),
                    new HorizontalLayout(reply, replyFromAi));
            target.add(item);
        }
    }

    private void openContactMessageDialog(
            Contact contact,
            MailboxMessage parent,
            String draftBody,
            Div messages,
            Supplier<String> aiDraft
    ) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(parent == null ? "New message" : "Reply");
        dialog.setWidth("680px");

        TextField to = new TextField("To");
        to.setValue(value(contact.getEmail()));
        to.setReadOnly(true);
        TextField subject = new TextField("Subject");
        subject.setWidthFull();
        subject.setValue(parent == null ? "" : replySubject(parent.getSubject()));
        TextArea body = new TextArea("Body");
        body.setWidthFull();
        body.setMinHeight("260px");
        body.setValue(value(draftBody));
        Span error = new Span();
        error.addClassName("cw-error");

        Div form = new Div(to, subject, body, error);
        form.addClassName("cw-dialog-form");

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button send = new Button("Send", VaadinIcon.PAPERPLANE.create());
        send.addClickListener(event -> {
            send.setEnabled(false);
            try {
                if (parent == null) {
                    contactFreeformMailService.sendNew(selectedProject.getId(), contact.getId(), subject.getValue(), body.getValue());
                } else {
                    contactFreeformMailService.sendReply(selectedProject.getId(), contact.getId(), parent.getId(), subject.getValue(), body.getValue());
                }
                renderConversationMessages(messages, contact, aiDraft);
                refreshContacts();
                dialog.close();
                Notification.show("Message sent", 2500, Position.BOTTOM_START);
            } catch (Exception ex) {
                error.setText(rootCauseMessage("Message send failed", ex));
                send.setEnabled(true);
            }
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(form);
        dialog.getFooter().add(cancel, send);
        dialog.open();
    }

    private String replySubject(String subject) {
        String value = value(subject);
        return value.regionMatches(true, 0, "Re:", 0, 3) ? value : "Re: " + value;
    }

    private String latestAssistantDraft(Contact contact, AiChatSession session) {
        if (session == null) {
            return "";
        }
        List<AiChatMessage> messages = localAiService.contactMessages(selectedProject.getId(), contact.getId(), session.getId());
        for (int index = messages.size() - 1; index >= 0; index--) {
            AiChatMessage message = messages.get(index);
            if (message.getRole() == AiChatRole.ASSISTANT) {
                return value(message.getContent());
            }
        }
        return "";
    }

    private void renderSummary(Div target, Contact contact) {
        target.removeAll();
        String text = localAiService.findSummary(contact.getId())
                .map(ContactConversationSummary::getSummaryText)
                .orElse("No saved summary.");
        target.add(markdown(text));
    }

    private List<AiChatSession> loadContactChatSessions(Select<AiChatSession> sessions, Contact contact) {
        List<AiChatSession> items = localAiService.contactSessions(contact.getId());
        sessions.setItems(items);
        sessions.setValue(items.stream().findFirst().orElse(null));
        return items;
    }

    private void renderContactChat(Div history, Contact contact, AiChatSession session) {
        if (session == null) {
            renderChatMessages(history, List.of());
            return;
        }
        renderChatMessages(history, localAiService.contactMessages(selectedProject.getId(), contact.getId(), session.getId()));
    }

    private void renderChatMessages(Div target, List<AiChatMessage> messages) {
        target.removeAll();
        if (messages.isEmpty()) {
            Span empty = new Span("No AI messages yet.");
            empty.addClassName("cw-muted");
            target.add(empty);
            scrollToBottom(target);
            return;
        }
        for (AiChatMessage message : messages) {
            Div row = new Div();
            row.addClassName("cw-chat-message");
            row.addClassName(message.getRole() == AiChatRole.USER ? "user" : "assistant");
            row.add(new Span(message.getRole().name()), markdown(message.getContent()));
            target.add(row);
        }
        scrollToBottom(target);
    }

    private Component markdown(String text) {
        String value = text == null || text.isBlank() ? "" : text;
        StringBuilder html = new StringBuilder("<div class=\"cw-markdown\">");
        boolean listOpen = false;
        for (String rawLine : value.split("\\R", -1)) {
            String line = rawLine.strip();
            if (line.isBlank()) {
                if (listOpen) {
                    html.append("</ul>");
                    listOpen = false;
                }
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!listOpen) {
                    html.append("<ul>");
                    listOpen = true;
                }
                html.append("<li>").append(inlineMarkdown(line.substring(2))).append("</li>");
                continue;
            }
            if (listOpen) {
                html.append("</ul>");
                listOpen = false;
            }
            if (line.startsWith("### ")) {
                html.append("<h4>").append(inlineMarkdown(line.substring(4))).append("</h4>");
            } else if (line.startsWith("## ")) {
                html.append("<h3>").append(inlineMarkdown(line.substring(3))).append("</h3>");
            } else if (line.startsWith("# ")) {
                html.append("<h2>").append(inlineMarkdown(line.substring(2))).append("</h2>");
            } else {
                html.append("<p>").append(inlineMarkdown(line)).append("</p>");
            }
        }
        if (listOpen) {
            html.append("</ul>");
        }
        html.append("</div>");
        return new Html(html.toString());
    }

    private String inlineMarkdown(String text) {
        String escaped = HtmlUtils.htmlEscape(text == null ? "" : text);
        return escaped
                .replaceAll("`([^`]+)`", "<code>$1</code>")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
    }

    private void openContactDialog(Contact contact) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit contact");
        dialog.setWidth("560px");

        TextField organization = new TextField("Organization");
        TextField contactName = new TextField("Contact name");
        EmailField email = new EmailField("Email");
        Select<ContactStatus> status = new Select<>();
        TextArea manualNote = new TextArea("Note");

        organization.setValue(value(contact.getOrganizationName()));
        organization.setReadOnly(true);
        contactName.setValue(value(contact.getContactName()));
        email.setValue(value(contact.getEmail()));
        status.setLabel("Status");
        status.setItems(ContactStatus.values());
        status.setValue(contact.getStatus());
        manualNote.setValue(value(contact.getNote()));

        Div form = new Div();
        form.addClassName("cw-dialog-form");
        form.add(organization, contactName, email, status, manualNote);
        addReadOnlyCustomFields(form, contact);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            try {
                contactService.updateEditableFields(
                        selectedProject.getId(),
                        contact.getId(),
                        contactName.getValue(),
                        required(email, "Email"),
                        status.getValue(),
                        manualNote.getValue()
                );
                refreshContacts();
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
            selectedProject = projectService.getProject(selectedProject.getId());
            refreshContacts();
            updateReadinessState();
            Notification.show("Inbox synced", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            log.warn("Manual inbox sync failed for project {}", selectedProject.getId(), ex);
            Notification.show(rootCauseMessage("Failed to sync inbox", ex), 5000, Position.BOTTOM_START);
        }
    }

    private void checkMailbox() {
        try {
            log.info("Mailbox check started: projectId={}", selectedProject.getId());
            mailHealthService.verifyConnections(selectedProject.getId());
            selectedProject = projectService.getProject(selectedProject.getId());
            loadProjectSettings(selectedProject);
            updateReadinessState();
            log.info("Mailbox check succeeded: projectId={}", selectedProject.getId());
            Notification.show("Mailbox connection OK", 2500, Position.BOTTOM_START);
        } catch (Exception ex) {
            log.warn("Mailbox check failed: projectId={}", selectedProject == null ? null : selectedProject.getId(), ex);
            Notification.show(rootCauseMessage("Mailbox check failed", ex), 5000, Position.BOTTOM_START);
        }
    }

    private Span gmailSentFolderWarning() {
        String sentFolder = appProperties.mail().gmail().sentFolder();
        Span warning = new Span();
        warning.addClassNames("cw-pill", "cw-pill-warning");
        if (sentFolder == null || sentFolder.isBlank()) {
            warning.setText("Gmail Sent folder is blank; sent-message sync may miss outgoing mail.");
            return warning;
        }
        if ("[Gmail]/Sent".equalsIgnoreCase(sentFolder.trim())) {
            warning.setText("Gmail Sent folder is configured as " + sentFolder
                    + "; Gmail IMAP commonly uses [Gmail]/Sent Mail. Verify the folder exists.");
            return warning;
        }
        warning.setText("Gmail Sent folder: " + sentFolder);
        warning.addClassName("cw-muted");
        return warning;
    }

    private void syncGmailSenderAlias() {
        try {
            log.info("Gmail sender alias sync started: projectId={}", selectedProject.getId());
            gmailAliasService.syncDefaultAlias(selectedProject.getId());
            selectedProject = projectService.getProject(selectedProject.getId());
            loadProjectSettings(selectedProject);
            updateReadinessState();
            log.info("Gmail sender alias sync succeeded: projectId={}", selectedProject.getId());
            Notification.show("Sender alias synced", 2500, Position.BOTTOM_START);
        } catch (GmailAuthorizationRequiredException ex) {
            log.info("Gmail sender alias sync requires OAuth redirect: projectId={}", selectedProject.getId());
            UI.getCurrent().getPage().setLocation(ex.getAuthorizationUrl());
        } catch (IllegalStateException ex) {
            log.warn("Gmail sender alias sync failed: projectId={}", selectedProject == null ? null : selectedProject.getId(), ex);
            if ("Google OAuth client ID, client secret, and redirect URI must be configured".equals(ex.getMessage())) {
                Notification.show(ex.getMessage(), 5000, Position.BOTTOM_START);
            } else {
                Notification.show(rootCauseMessage("Sender alias sync failed", ex), 5000, Position.BOTTOM_START);
            }
        } catch (Exception ex) {
            log.warn("Gmail sender alias sync failed: projectId={}", selectedProject == null ? null : selectedProject.getId(), ex);
            Notification.show(rootCauseMessage("Sender alias sync failed", ex), 5000, Position.BOTTOM_START);
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

    private boolean hasProjectCredential(String value) {
        return blankToNull(value) != null;
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

    private String first(Map<String, List<String>> parameters, String key) {
        List<String> values = parameters.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
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
