package com.pdasilem.contactwork.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.auth.CurrentUserService;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.conversation.ContactConversationSummaryRepository;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.project.AiProvider;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectRepository;
import com.pdasilem.contactwork.project.ProjectService;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class LocalAiServiceTest {

    @Test
    void askProjectBuildsPromptWithRuntimeGoogleOptions() {
        CapturingChatModel chatModel = new CapturingChatModel();
        GoogleGenAiChatModel googleChatModel = googleChatModel(chatModel);
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");
        ProjectService projectService = new StubProjectService(project);
        ContactService contactService = new StubContactService();
        AiChatSessionRepository sessionRepository = proxy(AiChatSessionRepository.class, invocation -> {
            if ("findFirstByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc".equals(invocation.getMethod().getName())) {
                return Optional.empty();
            }
            if ("save".equals(invocation.getMethod().getName())) {
                return invocation.getArguments()[0];
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });
        AiChatMessageRepository messageRepository = proxy(AiChatMessageRepository.class, invocation -> {
            if ("findBySessionIdOrderByCreatedAtAsc".equals(invocation.getMethod().getName())) {
                return List.of();
            }
            if ("save".equals(invocation.getMethod().getName())) {
                return invocation.getArguments()[0];
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });

        LocalAiService service = new LocalAiService(
                provider(OllamaChatModel.class, null),
                provider(GoogleGenAiChatModel.class, googleChatModel),
                null,
                settingsService(AiProvider.GOOGLE_GENAI, "gemini-free", 0.7),
                projectService,
                contactService,
                proxy(MailboxMessageRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                proxy(ContactConversationSummaryRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                sessionRepository,
                messageRepository
        );

        AiChatMessage answer = service.askProject(project.getId(), "What changed?");

        assertThat(answer.getContent()).isEqualTo("answer");
        assertThat(chatModel.prompt.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) chatModel.prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-free");
        assertThat(options.getTemperature()).isEqualTo(0.7);
        assertThat(chatModel.prompt.getInstructions()).hasSize(2);
        assertThat(chatModel.prompt.getInstructions().get(0).getText()).contains("system");
        assertThat(chatModel.prompt.getInstructions().get(1).getText()).contains("What changed?");
    }

    @Test
    void googleProviderUnloadsDefaultLocalModelBeforeCall() {
        CapturingChatModel chatModel = new CapturingChatModel();
        GoogleGenAiChatModel googleChatModel = googleChatModel(chatModel);
        RecordingLifecycleService lifecycle = new RecordingLifecycleService();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");

        LocalAiService service = new LocalAiService(
                provider(OllamaChatModel.class, null),
                provider(GoogleGenAiChatModel.class, googleChatModel),
                lifecycle,
                settingsService(AiProvider.GOOGLE_GENAI, "gemini-free", 0.7),
                new StubProjectService(project),
                new StubContactService(),
                proxy(MailboxMessageRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                proxy(ContactConversationSummaryRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                sessionRepository(),
                messageRepository()
        );

        service.askProject(project.getId(), "Question?");

        assertThat(lifecycle.unloaded).containsExactly(AppAiSettingsService.FALLBACK_LOCAL_AI_MODEL);
    }

    @Test
    void askProjectBuildsPromptWithRuntimeOllamaOptions() {
        CapturingChatModel chatModel = new CapturingChatModel();
        OllamaChatModel ollamaChatModel = ollamaChatModel(chatModel);
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");
        ProjectService projectService = new StubProjectService(project);
        AiChatSessionRepository sessionRepository = proxy(AiChatSessionRepository.class, invocation -> {
            if ("findFirstByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc".equals(invocation.getMethod().getName())) {
                return Optional.empty();
            }
            if ("save".equals(invocation.getMethod().getName())) {
                return invocation.getArguments()[0];
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });
        AiChatMessageRepository messageRepository = proxy(AiChatMessageRepository.class, invocation -> {
            if ("findBySessionIdOrderByCreatedAtAsc".equals(invocation.getMethod().getName())) {
                return List.of();
            }
            if ("save".equals(invocation.getMethod().getName())) {
                return invocation.getArguments()[0];
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });

        LocalAiService service = new LocalAiService(
                provider(OllamaChatModel.class, ollamaChatModel),
                provider(GoogleGenAiChatModel.class, null),
                null,
                settingsService(AiProvider.LOCAL_OLLAMA, "gemma4:e2b", 0.7),
                projectService,
                new StubContactService(),
                proxy(MailboxMessageRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                proxy(ContactConversationSummaryRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                sessionRepository,
                messageRepository
        );

        AiChatMessage answer = service.askProject(project.getId(), "What changed?");

        assertThat(answer.getContent()).isEqualTo("answer");
        assertThat(answer.getProvider()).isEqualTo(AiProvider.LOCAL_OLLAMA);
        assertThat(chatModel.prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
        OllamaChatOptions options = (OllamaChatOptions) chatModel.prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("gemma4:e2b");
        assertThat(options.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void localProviderDoesNotUnloadOllama() {
        CapturingChatModel chatModel = new CapturingChatModel();
        OllamaChatModel ollamaChatModel = ollamaChatModel(chatModel);
        RecordingLifecycleService lifecycle = new RecordingLifecycleService();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");
        ProjectService projectService = new StubProjectService(project);

        LocalAiService service = new LocalAiService(
                provider(OllamaChatModel.class, ollamaChatModel),
                provider(GoogleGenAiChatModel.class, null),
                lifecycle,
                settingsService(AiProvider.LOCAL_OLLAMA, "gemma4:e2b", 0.7),
                projectService,
                new StubContactService(),
                proxy(MailboxMessageRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                proxy(ContactConversationSummaryRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                sessionRepository(),
                messageRepository()
        );

        service.askProject(project.getId(), "Question?");

        assertThat(lifecycle.unloaded).isEmpty();
    }

    @Test
    void chatSessionsCanBeCreatedListedArchivedDeletedAndCompacted() {
        CapturingChatModel chatModel = new CapturingChatModel();
        OllamaChatModel ollamaChatModel = ollamaChatModel(chatModel);
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");
        java.util.Map<UUID, AiChatSession> sessions = new java.util.LinkedHashMap<>();
        AiChatSessionRepository sessionRepository = proxy(AiChatSessionRepository.class, invocation -> {
            String method = invocation.getMethod().getName();
            if ("save".equals(method)) {
                AiChatSession session = (AiChatSession) invocation.getArguments()[0];
                if (session.getId() == null) {
                    session.setId(UUID.randomUUID());
                }
                sessions.put(session.getId(), session);
                return session;
            }
            if ("findByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc".equals(method)) {
                return sessions.values().stream()
                        .filter(session -> session.getArchivedAt() == null)
                        .toList();
            }
            if ("findById".equals(method)) {
                return Optional.ofNullable(sessions.get(invocation.getArguments()[0]));
            }
            if ("deleteById".equals(method)) {
                sessions.remove(invocation.getArguments()[0]);
                return null;
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });
        AiChatMessage message = new AiChatMessage();
        message.setRole(AiChatRole.USER);
        message.setContent("Important prior decision");
        AiChatMessageRepository messageRepository = proxy(AiChatMessageRepository.class, invocation -> {
            if ("findBySessionIdOrderByCreatedAtAsc".equals(invocation.getMethod().getName())) {
                return List.of(message);
            }
            if ("save".equals(invocation.getMethod().getName())) {
                return invocation.getArguments()[0];
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });
        LocalAiService service = new LocalAiService(
                provider(OllamaChatModel.class, ollamaChatModel),
                provider(GoogleGenAiChatModel.class, null),
                null,
                settingsService(AiProvider.LOCAL_OLLAMA, "gemma4:e2b", 0.7),
                new StubProjectService(project),
                new StubContactService(),
                proxy(MailboxMessageRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                proxy(ContactConversationSummaryRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                sessionRepository,
                messageRepository
        );

        AiChatSession session = service.newProjectChat(project.getId());

        assertThat(service.projectSessions(project.getId())).containsExactly(session);
        assertThat(service.compactSession(session.getId()).getSummary()).isEqualTo("answer");

        service.archiveSession(session.getId());

        assertThat(service.projectSessions(project.getId())).isEmpty();

        service.deleteSession(session.getId());

        assertThat(sessions).isEmpty();
    }

    private static AiChatSessionRepository sessionRepository() {
        return proxy(AiChatSessionRepository.class, invocation -> {
            if ("findFirstByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc".equals(invocation.getMethod().getName())) {
                return Optional.empty();
            }
            if ("save".equals(invocation.getMethod().getName())) {
                return invocation.getArguments()[0];
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });
    }

    private static AiChatMessageRepository messageRepository() {
        return proxy(AiChatMessageRepository.class, invocation -> {
            if ("findBySessionIdOrderByCreatedAtAsc".equals(invocation.getMethod().getName())) {
                return List.of();
            }
            if ("save".equals(invocation.getMethod().getName())) {
                return invocation.getArguments()[0];
            }
            return defaultValue(invocation.getMethod().getReturnType());
        });
    }

    private static GoogleGenAiChatModel googleChatModel(CapturingChatModel capturingChatModel) {
        GoogleGenAiChatModel chatModel = mock(GoogleGenAiChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> capturingChatModel.call(invocation.getArgument(0)));
        return chatModel;
    }

    private static OllamaChatModel ollamaChatModel(CapturingChatModel capturingChatModel) {
        OllamaChatModel chatModel = mock(OllamaChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> capturingChatModel.call(invocation.getArgument(0)));
        return chatModel;
    }

    private static AppAiSettingsService settingsService(AiProvider provider, String model, double temperature) {
        AppAiSettings settings = new AppAiSettings();
        settings.setProvider(provider);
        settings.setModel(model);
        settings.setTemperature(temperature);
        AppAiSettingsService service = mock(AppAiSettingsService.class);
        when(service.current()).thenReturn(settings);
        when(service.defaultLocalAiModel()).thenReturn(AppAiSettingsService.FALLBACK_LOCAL_AI_MODEL);
        return service;
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (bean != null) {
            beanFactory.addBean(type.getSimpleName(), bean);
        }
        return beanFactory.getBeanProvider(type);
    }

    private static class CapturingChatModel {
        private Prompt prompt;

        private ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))));
        }
    }

    private static class RecordingLifecycleService extends OllamaModelLifecycleService {
        private final List<String> unloaded = new java.util.ArrayList<>();

        RecordingLifecycleService() {
            super(null, new ObjectMapper(), "http://ollama");
        }

        @Override
        public void unload(String model) {
            unloaded.add(model);
        }
    }

    private static class StubProjectService extends ProjectService {
        private final Project project;

        StubProjectService(Project project) {
            super(proxy(ProjectRepository.class, invocation -> defaultValue(invocation.getMethod().getReturnType())),
                    new AppProperties(new AppProperties.Resources("/tmp/contactwork-test"),
                            new AppProperties.Mail(1000, "0 */5 * * * *", null, null), null),
                    org.mockito.Mockito.mock(CurrentUserService.class));
            this.project = project;
        }

        @Override
        public Project getProject(UUID projectId) {
            return project;
        }

        @Override
        public String aiSystemPrompt(Project project) {
            return "system";
        }

    }

    private static class StubContactService extends ContactService {
        StubContactService() {
            super(null, null, null, new StubProjectService(new Project()));
        }

        @Override
        public long countByStatus(UUID projectId, ContactStatus status) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ThrowingInvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("toString".equals(method.getName())) {
                return type.getSimpleName() + " proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return handler.invoke(new Invocation(method, args == null ? new Object[0] : args));
        });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == void.class) {
            return null;
        }
        return 0;
    }

    private interface ThrowingInvocationHandler {
        Object invoke(Invocation invocation) throws Throwable;
    }

    private record Invocation(java.lang.reflect.Method method, Object[] arguments) {
        java.lang.reflect.Method getMethod() {
            return method;
        }

        Object[] getArguments() {
            return arguments;
        }
    }
}
