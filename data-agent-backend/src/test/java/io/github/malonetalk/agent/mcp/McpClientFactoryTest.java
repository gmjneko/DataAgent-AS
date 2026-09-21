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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.ClientType;
import io.github.malonetalk.enums.TransportType;
import io.github.malonetalk.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class McpClientFactoryTest {
    @Test
    void mapsAllTransportsAndTimeouts() {
        var factory = new McpClientFactory();
        for (TransportType transport : TransportType.values()) {
            var builder = mock(McpClientBuilder.class, RETURNS_SELF);
            var wrapper = mock(McpClientWrapper.class);
            when(builder.buildAsync()).thenReturn(Mono.just(wrapper));
            try (var construction = mockStatic(McpClientBuilder.class)) {
                construction.when(() -> McpClientBuilder.create("mcp-1")).thenReturn(builder);
                var server = new McpServer();
                server.setId(1);
                server.setClientType(ClientType.ASYNC);
                server.setTransportType(transport);
                server.setCommand("node");
                server.setArgs("[\"a b\",\"c,d\"]");
                server.setEnv("{\"TOKEN\":\"value\"}");
                server.setUrl("https://example.com/mcp");
                server.setHeaders("{\"Authorization\":\"Bearer value\"}");
                server.setQueryParams("{\"tenant\":\"one\"}");
                server.setTimeout(1000L);
                server.setInitializationTimeout(2000L);
                assertSame(wrapper, factory.connect(server));
                switch (transport) {
                    case STDIO ->
                            verify(builder)
                                    .stdioTransport(
                                            "node",
                                            List.of("a b", "c,d"),
                                            Map.of("TOKEN", "value"));
                    case SSE -> verify(builder).sseTransport("https://example.com/mcp");
                    case HTTP -> verify(builder).streamableHttpTransport("https://example.com/mcp");
                }
                if (transport != TransportType.STDIO) {
                    verify(builder).headers(Map.of("Authorization", "Bearer value"));
                    verify(builder).queryParams(Map.of("tenant", "one"));
                }
                verify(builder).timeout(Duration.ofMillis(1000));
                verify(builder).initializationTimeout(Duration.ofMillis(2000));
            }
        }
    }

    @Test
    void rejectsInvalidTransportConfiguration() {
        var server = new McpServer();
        server.setTransportType(TransportType.HTTP);
        server.setClientType(ClientType.ASYNC);
        server.setUrl("file:///etc/passwd");
        assertThrows(BusinessException.class, () -> new McpClientFactory().validate(server));
        server.setUrl("https://user:secret@example.com/mcp");
        assertThrows(BusinessException.class, () -> new McpClientFactory().validate(server));
    }
}
