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
import io.github.malonetalk.agent.models.ModelFactory;
import io.github.malonetalk.agent.models.ModelProperties;
import io.github.malonetalk.agent.tools.MarkAgentTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
                                transcript)) {
            var context =
                    RuntimeContext.builder()
                            .userId("42")
                            .sessionId("isolated")
                            .put("traceId", "trace")
                            .put("datasourceId", 7)
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
            var filesystem = agent.getWorkspaceManager().getFilesystem();
            filesystem.write(context, "private.txt", "private data");
            var otherUser = RuntimeContext.builder().userId("43").sessionId("other").build();
            assertFalse(filesystem.read(otherUser, "42/private.txt", 0, 100).isSuccess());
            assertFalse(
                    filesystem
                            .read(otherUser, workspace.resolve("42/private.txt").toString(), 0, 100)
                            .isSuccess());
            assertThrows(
                    SecurityException.class,
                    () -> filesystem.read(otherUser, "../42/private.txt", 0, 100));
            try (var files = Files.walk(workspace.resolve("42"))) {
                assertTrue(
                        files.anyMatch(p -> p.getFileName().toString().contains("tool")),
                        "offloaded tool output exists");
            }
        }
    }
}
