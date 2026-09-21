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

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.convertor.McpJson;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.ClientType;
import io.github.malonetalk.enums.TransportType;
import io.github.malonetalk.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class McpClientFactory {
    public void validate(McpServer server) {
        if (server.getTransportType() == null || server.getClientType() == null) {
            invalid("传输类型和客户端类型不能为空");
        }
        if (server.getTransportType() == TransportType.STDIO) {
            if (!StringUtils.hasText(server.getCommand())) {
                invalid("STDIO 必须配置 command");
            }
        } else {
            try {
                URI uri = URI.create(server.getUrl());
                if (!HTTP_SCHEMES.contains(uri.getScheme())
                        || uri.getHost() == null
                        || uri.getUserInfo() != null) {
                    invalid("请输入有效的 HTTP(S) URL，凭据请使用 headers");
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                invalid("请输入有效的 HTTP(S) URL");
            }
        }
        for (Long timeout :
                new Long[] {
                    server.getTimeout(),
                    server.getInitializationTimeout(),
                    server.getConnectTimeout()
                }) {
            if (timeout != null && (timeout <= 0 || timeout > 600000)) {
                invalid("超时必须为 1 到 600000 毫秒");
            }
        }
        if (server.getHttpVersion() != null
                && !Set.of("HTTP_1_1", "HTTP_2").contains(server.getHttpVersion())) {
            invalid("不支持的 HTTP 版本");
        }
        if ("ERROR".equals(server.getRedirectPolicy())) {
            invalid("redirectPolicy 请使用 NEVER 或 FOLLOW");
        }
        if (Boolean.TRUE.equals(server.getEnableElicitation())) {
            invalid("本次接入不支持 MCP elicitation 表单，请关闭 enableElicitation");
        }
    }

    private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");

    public McpClientWrapper connect(McpServer server) {
        validate(server);
        McpClientBuilder builder = McpClientBuilder.create("mcp-" + server.getId());
        switch (server.getTransportType()) {
            case STDIO ->
                    builder.stdioTransport(
                            server.getCommand(),
                            McpJson.list(server.getArgs()),
                            McpJson.map(server.getEnv()));
            case SSE ->
                    builder.sseTransport(server.getUrl())
                            .customizeSseClient(client -> customize(client, server));
            case HTTP ->
                    builder.streamableHttpTransport(server.getUrl())
                            .customizeStreamableHttpClient(client -> customize(client, server));
        }
        if (server.getTransportType() != TransportType.STDIO) {
            builder.headers(McpJson.map(server.getHeaders()))
                    .queryParams(McpJson.map(server.getQueryParams()));
        }
        builder.timeout(
                Duration.ofMillis(server.getTimeout() == null ? 120000 : server.getTimeout()));
        builder.initializationTimeout(
                Duration.ofMillis(
                        server.getInitializationTimeout() == null
                                ? 30000
                                : server.getInitializationTimeout()));
        return server.getClientType() == ClientType.SYNC
                ? builder.buildSync()
                : builder.buildAsync().block();
    }

    private void customize(HttpClient.Builder client, McpServer server) {
        if (server.getConnectTimeout() != null) {
            client.connectTimeout(Duration.ofMillis(server.getConnectTimeout()));
        }
        if (server.getHttpVersion() != null) {
            client.version(HttpClient.Version.valueOf(server.getHttpVersion()));
        }
        client.followRedirects(
                "FOLLOW".equals(server.getRedirectPolicy())
                        ? HttpClient.Redirect.NORMAL
                        : HttpClient.Redirect.NEVER);
    }

    private void invalid(String message) {
        throw BusinessException.of(ErrorCode.BAD_REQUEST, message);
    }
}
