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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.tool.Tool;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxContext;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.github.malonetalk.agent.models.ModelFactory;
import io.github.malonetalk.agent.models.ModelProperties;
import io.github.malonetalk.agent.tools.MarkAgentTool;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

class AgentConfigurationTest {
    @TempDir Path workspace;

    static class ContextProbe implements MarkAgentTool {
        volatile RuntimeContext received;

        @Tool(name = "probe", description = "Probe", readOnly = true, concurrencySafe = true)
        public String probe(RuntimeContext context) {
            received = context;
            return "result".repeat(500);
        }
    }

    private InMemoryAgentStateStore transcriptStore(
            ToolPermissions permissions, SessionTranscript transcript) {
        return new InMemoryAgentStateStore() {
            @Override
            public <T extends State> Optional<T> get(String u, String s, String k, Class<T> type) {
                var found = super.get(u, s, k, type);
                if (type != AgentState.class) return found;
                var state =
                        found.map(AgentState.class::cast)
                                .orElseGet(
                                        () -> AgentState.builder().userId(u).sessionId(s).build());
                state.setPermissionContext(permissions.snapshot());
                return Optional.of(type.cast(state));
            }

            @Override
            public void save(String u, String s, String k, State state) {
                transcript.append(u, s, ((AgentState) state).getContext());
                super.save(u, s, k, state);
            }
        };
    }

    private Model probeModel() {
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("test");
        AtomicInteger calls = new AtomicInteger();
        when(model.stream(anyList(), any(), any()))
                .thenAnswer(
                        inv ->
                                Flux.just(
                                        ChatResponse.builder()
                                                .id("r-" + calls.get())
                                                .content(
                                                        List.of(
                                                                calls.getAndIncrement() == 0
                                                                        ? ToolUseBlock.builder()
                                                                                .id("tool")
                                                                                .name("probe")
                                                                                .input(Map.of())
                                                                                .build()
                                                                        : TextBlock.builder()
                                                                                .text("done")
                                                                                .build()))
                                                .build()));
        return model;
    }

    @Test
    void productionConfigurationInjectsContextOffloadsAndRetainsHistory() throws Exception {
        var properties = new AgentProperties();
        properties.setWorkspace(workspace.toString());
        properties.setMaxToolResultChars(1000);
        var permissions = new ToolPermissions();
        var transcript = new SessionTranscript(properties);
        var store = transcriptStore(permissions, transcript);
        Model model = probeModel();
        ModelFactory factory = mock(ModelFactory.class);
        when(factory.getInstance(any())).thenReturn(model);
        ContextProbe probe = new ContextProbe();
        try (var agent =
                new AgentConfiguration()
                        .dataAgent(
                                factory,
                                new ModelProperties(),
                                properties,
                                List.of(probe),
                                store,
                                List.of(),
                                permissions,
                                transcript,
                                configuredE2b(),
                                dataSource())) {
            var context =
                    RuntimeContext.builder()
                            .userId("42")
                            .sessionId("isolated")
                            .put("traceId", "trace")
                            .put("datasourceId", 7)
                            .put(
                                    SandboxContext.class,
                                    SandboxContext.builder()
                                            .externalSandbox(new IdleSandbox())
                                            .build())
                            .build();
            agent.streamEvents(new UserMessage("query"), context).blockLast(Duration.ofSeconds(10));
            assertNotNull(probe.received);
            assertEquals("42", probe.received.getUserId());
            assertEquals("isolated", probe.received.getSessionId());
            assertEquals(7, (Integer) probe.received.get("datasourceId"));
            var history = transcript.read("42", "isolated");
            assertTrue(
                    history.stream()
                            .flatMap(msg -> msg.getContent().stream())
                            .filter(ToolResultBlock.class::isInstance)
                            .map(ToolResultBlock.class::cast)
                            .flatMap(block -> block.getOutput().stream())
                            .filter(TextBlock.class::isInstance)
                            .map(TextBlock.class::cast)
                            .anyMatch(text -> text.getText().contains("result".repeat(500))));
            var state = agent.getDelegate().getAgentState("42", "isolated");
            assertFalse(state.getContext().toString().contains("result".repeat(500)));
            assertTrue(transcript.read("43", "isolated").isEmpty());
            assertTrue(agent.getToolkit().getToolNames().contains("write_file"));
            assertTrue(agent.getToolkit().getToolNames().contains("edit_file"));
            assertFalse(agent.getToolkit().getToolNames().contains("execute"));
            assertTrue(permissions.snapshot().toString().contains("write_file"));
            assertTrue(permissions.snapshot().toString().contains("edit_file"));
        }
    }

    @Test
    void unconfiguredE2bRejectsAgentConstruction() {
        var properties = new AgentProperties();
        properties.setWorkspace(workspace.toString());
        assertThrows(
                IllegalStateException.class,
                () ->
                        new AgentConfiguration()
                                .dataAgent(
                                        mock(ModelFactory.class),
                                        new ModelProperties(),
                                        properties,
                                        List.of(),
                                        transcriptStore(
                                                new ToolPermissions(),
                                                new SessionTranscript(properties)),
                                        List.of(),
                                        new ToolPermissions(),
                                        new SessionTranscript(properties),
                                        new E2bSandboxProperties(),
                                        mock(DataSource.class)));
    }

    private static E2bSandboxProperties configuredE2b() {
        E2bSandboxProperties e2b = new E2bSandboxProperties();
        e2b.setApiKey("test-key");
        e2b.setTemplateId("python-data");
        e2b.setApiBaseUrl("http://e2b.example.local");
        e2b.setDomain("e2b.example.local");
        return e2b;
    }

    private static DataSource dataSource() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("no database in unit test"));
        return dataSource;
    }

    /** Accepts filesystem commands without contacting E2B. */
    private static final class IdleSandbox implements Sandbox {
        @Override
        public ExecResult exec(
                RuntimeContext runtimeContext, String command, Integer timeoutSeconds) {
            return new ExecResult(0, "", "", false);
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void shutdown() {}

        @Override
        public void close() {}

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public SandboxState getState() {
            return null;
        }

        @Override
        public InputStream persistWorkspace() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void hydrateWorkspace(InputStream archive) {}
    }
}
