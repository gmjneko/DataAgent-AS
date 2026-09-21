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
package io.github.malonetalk.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.malonetalk.agent.AgentService;
import io.github.malonetalk.agent.SessionService;
import io.github.malonetalk.agent.mcp.McpToolRegistryService;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.convertor.McpServerConverter;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.McpRuntimeStatus;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.exception.GlobalExceptionHandler;
import io.github.malonetalk.interceptor.AuthInterceptor;
import io.github.malonetalk.service.McpServerService;
import io.github.malonetalk.service.SysUserService;
import io.github.malonetalk.utils.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

class AgentAndMcpApiTest {
    private final AgentService agent = mock(AgentService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final McpServerService servers = mock(McpServerService.class);
    private final McpToolRegistryService registry = mock(McpToolRegistryService.class);
    private final SysUserService users = mock(SysUserService.class);
    private final JwtUtil jwt = mock(JwtUtil.class);

    private MockMvc mvc(boolean admin) {
        when(jwt.parseUserId("token")).thenReturn(1);
        when(users.selectAuthProjection(1))
                .thenReturn(UserContext.builder().userId(1).superAdmin(admin).build());
        return MockMvcBuilders.standaloneSetup(
                        new AgentController(agent, sessions),
                        new McpServerController(
                                servers, Mappers.getMapper(McpServerConverter.class), registry))
                .addInterceptors(new AuthInterceptor(jwt, users))
                .setControllerAdvice(new GlobalExceptionHandler(new ExceptionResponseMapper()))
                .build();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void adminCanReadConnectionAndRefreshWhileRegularUsersCannot() throws Exception {
        var server = new McpServer();
        server.setId(7);
        server.setStatus("ACTIVE");
        when(servers.findById(7)).thenReturn(server);
        var state =
                McpRuntimeStatus.builder()
                        .serverId(7)
                        .connectionState("CONNECTED")
                        .tools(List.of())
                        .build();
        when(registry.status(7)).thenReturn(state);
        when(registry.refresh(server)).thenReturn(state);
        mvc(true)
                .perform(
                        get("/api/mcp-server/7/connection").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionState").value("CONNECTED"));
        mvc(true)
                .perform(post("/api/mcp-server/7/refresh").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(registry).refresh(server);
        mvc(false)
                .perform(
                        get("/api/mcp-server/7/connection").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyDeleteStopAndChatKeepExistingHttpContract() throws Exception {
        var mvc = mvc(false);
        when(sessions.getSessionHistory("s", 1)).thenReturn(List.of());
        mvc.perform(get("/api/agent/session/s/history").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(delete("/api/agent/session/s").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(sessions).clearSession("s", 1);
        mvc.perform(post("/api/agent/session/s/stop").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(agent).stop(1, "s");
        when(agent.chatStream(eq(1), eq("s"), eq("hello"), any(), any()))
                .thenReturn(
                        Flux.just(
                                ChatStreamEvent.builder()
                                        .type(ChatStreamEventType.TEXT)
                                        .content("hello")
                                        .isLast(true)
                                        .build()));
        var request =
                mvc.perform(
                                post("/api/agent/chat/stream")
                                        .header("Authorization", "Bearer token")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"sessionId\":\"s\",\"message\":\"hello\"}"))
                        .andReturn();
        var result = mvc.perform(asyncDispatch(request)).andExpect(status().isOk()).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("event:text"));
        assertTrue(result.getResponse().getContentAsString().contains("hello"));
    }
}
