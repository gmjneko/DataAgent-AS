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

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.convertor.EventConverter;
import io.github.malonetalk.dto.ChatRequest;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.enums.ChatStreamEventType;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorResponse;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.web.TraceIdFilter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {
    private final HarnessAgent agent;
    private final SessionOperations operations;
    private final SessionService sessions;
    private final DatasourceService datasources;
    private final EventConverter converter;
    private final ExceptionResponseMapper exceptions;

    public Flux<ChatStreamEvent> chatStream(
            int userId,
            String sessionId,
            String input,
            List<ChatRequest.ToolResultInput> toolResults,
            Integer datasourceId) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return operations.stream(
                        sessionId,
                        () -> {
                            sessions.bindUserSession(userId, sessionId);
                            if (datasourceId != null) {
                                datasources.bindSessionDatasource(sessionId, datasourceId);
                            }
                            RuntimeContext context =
                                    RuntimeContext.builder()
                                            .userId(String.valueOf(userId))
                                            .sessionId(sessionId)
                                            .put("traceId", traceId == null ? "" : traceId)
                                            .put(
                                                    "datasourceId",
                                                    datasourceId == null ? "" : datasourceId)
                                            .build();
                            List<Msg> messages = buildInput(userId, sessionId, input, toolResults);
                            var mapping = converter.newStreamMapper();
                            return agent.streamEvents(messages, context)
                                    .flatMapIterable(mapping)
                                    .doFinally(
                                            signal -> {
                                                try (var ignored =
                                                        MDC.putCloseable(
                                                                TraceIdFilter.TRACE_ID_MDC_KEY,
                                                                traceId)) {
                                                    log.info(
                                                            "SSE chat finished: userId={},"
                                                                    + " sessionId={}, signal={}",
                                                            userId,
                                                            sessionId,
                                                            signal);
                                                }
                                            });
                        })
                .onErrorResume(this::toErrorEvent)
                // Keep the upstream alive after the SSE consumer disconnects so the framework can
                // process the interrupt and persist state before releasing the session operation.
                .replay(0)
                .autoConnect(1)
                .doOnCancel(() -> agent.getDelegate().interrupt(String.valueOf(userId), sessionId));
    }

    private List<Msg> buildInput(
            int userId, String sessionId, String input, List<ChatRequest.ToolResultInput> results) {
        if (results == null || results.isEmpty()) {
            if (input == null || input.isBlank()) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "message 不能为空");
            }
            return List.of(new UserMessage(input));
        }
        var state = agent.getDelegate().getAgentState(String.valueOf(userId), sessionId);
        var completed =
                state.getContext().stream()
                        .flatMap(msg -> msg.getContent().stream())
                        .filter(ToolResultBlock.class::isInstance)
                        .map(ToolResultBlock.class::cast)
                        .filter(result -> result.getState() != ToolResultState.RUNNING)
                        .map(ToolResultBlock::getId)
                        .collect(Collectors.toSet());
        if (results.stream().map(ChatRequest.ToolResultInput::toolCallId).distinct().count()
                != results.size()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "不能重复回答同一个问题");
        }
        var pending =
                state.getContext().stream()
                        .flatMap(msg -> msg.getContent().stream())
                        .filter(ToolUseBlock.class::isInstance)
                        .map(ToolUseBlock.class::cast)
                        .filter(
                                tool ->
                                        tool.getState() != ToolCallState.FINISHED
                                                && !completed.contains(tool.getId()))
                        .toList();
        for (var result : results) {
            if (pending.stream()
                    .noneMatch(
                            tool ->
                                    tool.getId().equals(result.toolCallId())
                                            && tool.getName().equals(result.toolName())
                                            && "ask_user".equals(tool.getName()))) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "不存在对应的待回答问题");
            }
        }
        return results.stream()
                .<Msg>map(
                        result ->
                                new ToolResultMessage(
                                        ToolResultBlock.text(result.output())
                                                .withIdAndName(
                                                        result.toolCallId(), result.toolName())
                                                .withState(ToolResultState.SUCCESS)))
                .toList();
    }

    public void stop(int userId, String sessionId) {
        sessions.requireOwnership(userId, sessionId);
        agent.getDelegate().interrupt(String.valueOf(userId), sessionId);
    }

    private Flux<ChatStreamEvent> toErrorEvent(Throwable exception) {
        ErrorResponse error = exceptions.resolve(exception);
        exceptions.logMapped(log, exception, error);
        return Flux.just(
                ChatStreamEvent.builder()
                        .type(ChatStreamEventType.ERROR)
                        .isLast(true)
                        .content(error.message())
                        .errorCode(error.errorCode().getCode())
                        .build());
    }
}
