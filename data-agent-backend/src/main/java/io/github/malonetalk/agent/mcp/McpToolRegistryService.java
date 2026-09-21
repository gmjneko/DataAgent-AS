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

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;
import io.github.malonetalk.agent.AgentProperties;
import io.github.malonetalk.agent.ToolPermissions;
import io.github.malonetalk.convertor.McpJson;
import io.github.malonetalk.dto.McpRuntimeStatus;
import io.github.malonetalk.dto.McpRuntimeStatus.ToolInfo;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.mapper.McpServerMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolRegistryService {
    private final HarnessAgent agent;
    private final AgentProperties properties;
    private final McpServerMapper servers;
    private final McpClientFactory factory;
    private final ToolPermissions permissions;
    private final Map<Integer, McpClientWrapper> clients = new HashMap<>();
    private final Map<Integer, McpRuntimeStatus> states = new HashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isMcpEnabled()) {
            return;
        }
        servers.selectByStatus(Status.ACTIVE.getCode()).forEach(this::refresh);
    }

    public synchronized McpRuntimeStatus status(Integer id) {
        return states.getOrDefault(
                id,
                McpRuntimeStatus.builder()
                        .serverId(id)
                        .connectionState("DISCONNECTED")
                        .tools(List.of())
                        .build());
    }

    public synchronized McpRuntimeStatus refresh(McpServer server) {
        disconnect(server.getId());
        if (!properties.isMcpEnabled() || !Status.ACTIVE.getCode().equals(server.getStatus())) {
            return status(server.getId());
        }
        McpClientWrapper client = null;
        List<ToolInfo> selected = List.of();
        boolean registering = false;
        try {
            client = factory.connect(server);
            client.initialize().block(initializationDeadline(server));
            var discovered = client.listTools().block(requestDeadline(server));
            List<ToolInfo> allTools = discovered.stream().map(this::toolInfo).toList();
            selected = filterTools(server, allTools);
            Set<String> names = agent.getToolkit().getToolNames();
            if (selected.stream().anyMatch(tool -> names.contains(tool.name()))) {
                throw new IllegalArgumentException("工具名称与已有工具重复");
            }
            if (!selected.isEmpty()) {
                registering = true;
                agent.getToolkit()
                        .registration()
                        .mcpClient(client)
                        .enableTools(selected.stream().map(ToolInfo::name).toList())
                        .apply();
                selected.forEach(tool -> permissions.allow(tool.name()));
            }
            clients.put(server.getId(), client);
            McpRuntimeStatus state =
                    McpRuntimeStatus.builder()
                            .serverId(server.getId())
                            .connectionState("CONNECTED")
                            .lastConnectedAt(Instant.now())
                            .tools(selected)
                            .discoveredTools(allTools)
                            .build();
            states.put(server.getId(), state);
            return state;
        } catch (Exception e) {
            cleanupFailedRegistration(client, selected, registering);
            // Never expose connection exceptions: SDK errors can contain credential-bearing URLs.
            String reason =
                    e instanceof IllegalArgumentException ? "工具名冲突或配置无效" : "连接或工具发现失败，请检查服务及超时配置";
            log.warn("MCP server {} failed: {}", server.getId(), e.getClass().getSimpleName());
            McpRuntimeStatus state =
                    McpRuntimeStatus.builder()
                            .serverId(server.getId())
                            .connectionState("FAILED")
                            .lastConnectedAt(status(server.getId()).lastConnectedAt())
                            .error(reason)
                            .tools(List.of())
                            .build();
            states.put(server.getId(), state);
            return state;
        }
    }

    private void cleanupFailedRegistration(
            McpClientWrapper client, List<ToolInfo> selected, boolean registering) {
        if (client != null) {
            if (registering) {
                selected.forEach(
                        tool -> {
                            permissions.remove(tool.name());
                            agent.getToolkit().removeTool(tool.name());
                        });
                try {
                    agent.getToolkit()
                            .removeMcpClient(client.getName())
                            .block(Duration.ofSeconds(10));
                } catch (Exception ignored) {
                    /* Close below even when unregister fails. */
                }
            }
            try {
                client.close();
            } catch (Exception ignored) {
                /* Already failed. */
            }
        }
    }

    private List<ToolInfo> filterTools(McpServer server, List<ToolInfo> discovered) {
        Set<String> allow = new HashSet<>(McpJson.list(server.getEnableTools()));
        Set<String> deny = new HashSet<>(McpJson.list(server.getDisableTools()));
        return discovered.stream()
                .filter(
                        tool ->
                                !deny.contains(tool.name())
                                        && (allow.isEmpty() || allow.contains(tool.name())))
                .toList();
    }

    private ToolInfo toolInfo(McpSchema.Tool tool) {
        return new ToolInfo(
                tool.name(),
                tool.description(),
                tool.annotations() != null
                        && Boolean.TRUE.equals(tool.annotations().readOnlyHint()));
    }

    public synchronized McpRuntimeStatus disconnect(Integer id) {
        McpRuntimeStatus previous = status(id);
        McpClientWrapper client = clients.remove(id);
        previous.tools().forEach(tool -> permissions.remove(tool.name()));
        if (client != null) {
            try {
                agent.getToolkit().removeMcpClient(client.getName()).block(Duration.ofSeconds(10));
            } catch (Exception e) {
                log.warn("MCP server {} disconnect failed: {}", id, e.getClass().getSimpleName());
            } finally {
                previous.tools().forEach(tool -> agent.getToolkit().removeTool(tool.name()));
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("MCP server {} close failed", id);
                }
            }
        }
        var state =
                McpRuntimeStatus.builder()
                        .serverId(id)
                        .connectionState("DISCONNECTED")
                        .lastConnectedAt(previous.lastConnectedAt())
                        .tools(List.of())
                        .build();
        states.put(id, state);
        return state;
    }

    public McpRuntimeStatus test(McpServer server) {
        try (McpClientWrapper client = factory.connect(server)) {
            client.initialize().block(initializationDeadline(server));
            var tools =
                    client.listTools().block(requestDeadline(server)).stream()
                            .map(this::toolInfo)
                            .toList();
            return McpRuntimeStatus.builder()
                    .serverId(server.getId())
                    .connectionState("CONNECTED")
                    .tools(tools)
                    .discoveredTools(tools)
                    .build();
        } catch (Exception e) {
            return McpRuntimeStatus.builder()
                    .serverId(server.getId())
                    .connectionState("FAILED")
                    .error("连接测试失败，请检查服务及配置")
                    .tools(List.of())
                    .build();
        }
    }

    private Duration initializationDeadline(McpServer server) {
        return Duration.ofMillis(
                (server.getInitializationTimeout() == null
                                ? 30000
                                : server.getInitializationTimeout())
                        + 5000);
    }

    private Duration requestDeadline(McpServer server) {
        return Duration.ofMillis(
                (server.getTimeout() == null ? 120000 : server.getTimeout()) + 5000);
    }

    @PreDestroy
    public synchronized void close() {
        List.copyOf(clients.keySet()).forEach(this::disconnect);
    }
}
