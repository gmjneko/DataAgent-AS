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
package io.github.malonetalk.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.github.malonetalk.agent.AgentProperties;
import io.github.malonetalk.agent.ToolPermissions;
import io.github.malonetalk.agent.tools.AskUserTool;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.mapper.McpServerMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class McpToolRegistryServiceTest {
    private final HarnessAgent agent = mock(HarnessAgent.class);
    private final Toolkit toolkit = new Toolkit();
    private final McpClientFactory factory = mock(McpClientFactory.class);
    private final ToolPermissions permissions = new ToolPermissions();
    private final McpToolRegistryService registry =
            new McpToolRegistryService(
                    agent,
                    new AgentProperties(),
                    mock(McpServerMapper.class),
                    factory,
                    permissions);
    private final McpServer server = new McpServer();

    McpToolRegistryServiceTest() {
        when(agent.getToolkit()).thenReturn(toolkit);
        server.setId(1);
        server.setStatus("ACTIVE");
    }

    private McpClientWrapper client(String... names) {
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(client.getName()).thenReturn("mcp-1");
        when(client.isInitialized()).thenReturn(true);
        when(client.initialize()).thenReturn(Mono.empty());
        var tools =
                List.of(names).stream()
                        .map(
                                name ->
                                        JsonUtils.getJsonCodec()
                                                .fromJson(
                                                        "{\"name\":\""
                                                                + name
                                                                + "\",\"description\":\"test\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}",
                                                        McpSchema.Tool.class))
                        .toList();
        when(client.listTools()).thenReturn(Mono.just(tools));
        tools.forEach(tool -> when(client.getCachedTool(tool.name())).thenReturn(tool));
        when(factory.connect(server)).thenReturn(client);
        return client;
    }

    @Test
    void filtersRegistersAndRemovesToolsAndPermissions() {
        var client = client("alpha", "beta", "gamma");
        server.setEnableTools("[\"alpha\",\"beta\"]");
        server.setDisableTools("[\"beta\"]");
        var status = registry.refresh(server);
        assertEquals("CONNECTED", status.connectionState());
        assertEquals(List.of("alpha"), status.tools().stream().map(t -> t.name()).toList());
        assertTrue(toolkit.getToolNames().contains("alpha"));
        assertTrue(permissions.snapshot().getAllowRules().containsKey("alpha"));
        assertNotNull(status.lastConnectedAt());
        registry.disconnect(1);
        assertFalse(toolkit.getToolNames().contains("alpha"));
        assertFalse(permissions.snapshot().getAllowRules().containsKey("alpha"));
        verify(client, atLeastOnce()).close();
    }

    @Test
    void conflictNeverRemovesExistingTools() {
        toolkit.registerTool(new AskUserTool());
        var client = client("ask_user");
        assertEquals("FAILED", registry.refresh(server).connectionState());
        assertTrue(toolkit.getToolNames().contains("ask_user"));
        verify(client).close();
    }

    @Test
    void failedDiscoveryClosesClientAndDoesNotExposeCredentials() {
        var client = client("alpha");
        when(client.listTools())
                .thenReturn(Mono.error(new IllegalStateException("https://secret@example.com")));
        var status = registry.refresh(server);
        assertEquals("FAILED", status.connectionState());
        assertFalse(status.error().contains("secret"));
        verify(client).close();
        registry.close();
    }

    @Test
    void inactiveServersAreNotConnectedAndTestDoesNotRegisterTools() {
        var client = client("alpha");
        server.setStatus("INACTIVE");
        assertEquals("DISCONNECTED", registry.refresh(server).connectionState());
        verify(factory, never()).connect(server);
        assertEquals("CONNECTED", registry.test(server).connectionState());
        assertTrue(toolkit.getToolNames().isEmpty());
        verify(client).close();
    }
}
