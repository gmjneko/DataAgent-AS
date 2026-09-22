/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.github.malonetalk.agent.tools.AskUserTool;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class HarnessMigrationTest {
    @TempDir Path workspace;

    private Model model() {
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("local-test-model");
        return model;
    }

    private Flux<ChatResponse> reply(ContentBlock block) {
        return Flux.just(ChatResponse.builder().id("reply").content(List.of(block)).build());
    }

    private HarnessAgent agent(Model model, AgentStateStore store) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new AskUserTool());
        ToolPermissions permissions = new ToolPermissions();
        permissions.allow("ask_user");
        return HarnessAgent.builder()
                .name("test")
                .agentId("data-agent")
                .model(model)
                .toolkit(toolkit)
                .permissionContext(permissions.snapshot())
                .stateStore(store)
                .workspace(workspace)
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableSubagents()
                .disableFilesystemTools()
                .disableShellTool()
                .disableToolsConfig()
                .disableDefaultWorkspaceSkills()
                .compaction(
                        CompactionConfig.builder()
                                .triggerMessages(6)
                                .keepMessages(2)
                                .triggerTokens(100000)
                                .keepTokens(0)
                                .flushBeforeCompact(false)
                                .build())
                .build();
    }

    private RuntimeContext context(String user) {
        return RuntimeContext.builder().userId(user).sessionId("session").build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resumesExternalQuestionAfterRecreatingAgentAndIsolatesUsers(boolean structured) {
        var input =
                structured
                        ? Map.<String, Object>of(
                                "question",
                                "哪一年？",
                                "questions",
                                List.of(
                                        Map.of(
                                                "question",
                                                "年份",
                                                "options",
                                                List.of("2025", "2026"),
                                                "multiple",
                                                false)))
                        : Map.<String, Object>of("question", "哪一年？");
        Model model = model();
        AtomicInteger calls = new AtomicInteger();
        when(model.stream(anyList(), any(), any()))
                .thenAnswer(
                        inv -> {
                            String schema = JsonUtils.getJsonCodec().toJson(inv.getArgument(1));
                            assertTrue(schema.contains("\"questions\":"), schema);
                            assertTrue(schema.contains("\"options\":"), schema);
                            assertTrue(schema.contains("\"multiple\":"), schema);
                            return calls.getAndIncrement() == 0
                                    ? reply(
                                            ToolUseBlock.builder()
                                                    .id("q")
                                                    .name("ask_user")
                                                    .input(input)
                                                    .build())
                                    : reply(TextBlock.builder().text("已按 2026 年查询").build());
                        });
        var store = new JsonFileAgentStateStore(workspace.resolve("state"));
        try (var first = agent(model, store)) {
            var events =
                    first.streamEvents(new UserMessage("查询"), context("1"))
                            .collectList()
                            .block(Duration.ofSeconds(10));
            var mapping =
                    new io.github.malonetalk.convertor.EventConverter(List.of()).newStreamMapper();
            assertTrue(
                    events.stream()
                            .flatMap(e -> mapping.apply(e).stream())
                            .anyMatch(
                                    e ->
                                            e.type()
                                                    == io.github.malonetalk.enums
                                                            .ChatStreamEventType.QUESTION));
        }
        try (var restarted = agent(model, store)) {
            var service =
                    new AgentService(
                            restarted,
                            new SessionOperations(),
                            mock(SessionOwnership.class),
                            mock(io.github.malonetalk.service.DatasourceService.class),
                            new io.github.malonetalk.convertor.EventConverter(List.of()),
                            new io.github.malonetalk.exception.ExceptionResponseMapper());
            var answer =
                    List.of(
                            new io.github.malonetalk.dto.ChatRequest.ToolResultInput(
                                    "q", "ask_user", "2026"));
            var events =
                    service.chatStream(1, "session", null, answer, null)
                            .collectList()
                            .block(Duration.ofSeconds(10));
            assertTrue(
                    events.stream()
                            .anyMatch(e -> e.content() != null && e.content().contains("2026")));
            var replay =
                    service.chatStream(1, "session", null, answer, null)
                            .collectList()
                            .block(Duration.ofSeconds(10));
            assertTrue(
                    replay.stream()
                            .anyMatch(
                                    e ->
                                            e.type()
                                                    == io.github.malonetalk.enums
                                                            .ChatStreamEventType.ERROR));
            assertFalse(store.exists("2", "session"));
            restarted.clearContext("1", "session");
            assertTrue(
                    restarted.getDelegate().getAgentState("1", "session").getContext().isEmpty());
        }
    }

    @Test
    void disconnectFinishesPersistenceBeforeReleasingTheSession() throws Exception {
        Model model = model();
        Sinks.One<ChatResponse> response = Sinks.one();
        CountDownLatch entered = new CountDownLatch(1);
        when(model.stream(anyList(), any(), any()))
                .thenAnswer(
                        inv -> {
                            entered.countDown();
                            return response.asMono().flux();
                        });
        var store = new JsonFileAgentStateStore(workspace.resolve("state"));
        try (var agent = agent(model, store)) {
            var operations = new SessionOperations();
            var service =
                    new AgentService(
                            agent,
                            operations,
                            mock(SessionOwnership.class),
                            mock(io.github.malonetalk.service.DatasourceService.class),
                            new io.github.malonetalk.convertor.EventConverter(List.of()),
                            new io.github.malonetalk.exception.ExceptionResponseMapper());
            var subscription =
                    service.chatStream(1, "session", "retain me", null, null).subscribe();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            subscription.dispose();
            response.tryEmitValue(
                    ChatResponse.builder()
                            .id("last")
                            .content(List.of(TextBlock.builder().text("partial").build()))
                            .build());
            operations.stream("session", () -> Flux.just(true)).blockLast(Duration.ofSeconds(10));
            assertTrue(store.exists("1", "session"));
            assertTrue(
                    store
                            .get(
                                    "1",
                                    "session",
                                    "agent_state",
                                    io.agentscope.core.state.AgentState.class)
                            .orElseThrow()
                            .getContext()
                            .stream()
                            .anyMatch(msg -> msg.getTextContent().contains("retain me")));
        }
    }

    @Test
    void compactsAndContinuesConversation() {
        Model model = model();
        when(model.stream(anyList(), any(), any()))
                .thenAnswer(inv -> reply(TextBlock.builder().text("会话摘要和回复").build()));
        var store = new JsonFileAgentStateStore(workspace.resolve("state"));
        try (var agent = agent(model, store)) {
            for (int i = 0; i < 5; i++) {
                agent.streamEvents(new UserMessage("问题" + i), context("1"))
                        .blockLast(Duration.ofSeconds(10));
            }
            var context = agent.getDelegate().getAgentState("1", "session").getContext();
            assertTrue(context.size() < 10, "model context should be compacted");
            assertTrue(context.stream().anyMatch(msg -> msg.getTextContent().contains("问题4")));
        }
    }
}
