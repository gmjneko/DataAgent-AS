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
package io.github.malonetalk.convertor;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonUtils;
import io.github.malonetalk.convertor.handler.ToolResultHandler;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolCallInfo;
import io.github.malonetalk.enums.ChatStreamEventType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventConverter {
    private final List<ToolResultHandler> handlers;

    /**
     * Each subscription owns its buffers; no state leaks between simultaneous chats.
     */
    public Function<AgentEvent, List<ChatStreamEvent>> newStreamMapper() {
        return new StreamMapper();
    }

    private class StreamMapper implements Function<AgentEvent, List<ChatStreamEvent>> {
        private final Map<String, StringBuilder> arguments = new HashMap<>();
        private final Map<String, StringBuilder> outputs = new HashMap<>();
        private final Map<String, ToolUseBlock> calls = new HashMap<>();

        @Override
        public List<ChatStreamEvent> apply(AgentEvent event) {
            if (event instanceof TextBlockDeltaEvent text) {
                return List.of(
                        text(
                                ChatStreamEventType.TEXT,
                                text.getReplyId() + ":" + text.getBlockId(),
                                text.getDelta()));
            }
            if (event instanceof ThinkingBlockDeltaEvent thinking) {
                return List.of(
                        text(
                                ChatStreamEventType.THINKING,
                                thinking.getReplyId() + ":" + thinking.getBlockId(),
                                thinking.getDelta()));
            }
            if (event instanceof ToolCallDeltaEvent call) {
                arguments
                        .computeIfAbsent(call.getToolCallId(), id -> new StringBuilder())
                        .append(call.getDelta());
            } else if (event instanceof ToolCallEndEvent call) {
                StringBuilder json = arguments.remove(call.getToolCallId());
                Map<String, Object> input = parseArguments(json == null ? "{}" : json.toString());
                calls.put(
                        call.getToolCallId(),
                        ToolUseBlock.builder()
                                .id(call.getToolCallId())
                                .name(call.getToolCallName())
                                .input(input)
                                .build());
                return List.of(
                        ChatStreamEvent.builder()
                                .type(ChatStreamEventType.TOOL_CALL)
                                .messageId(call.getReplyId())
                                .toolCall(
                                        new ToolCallInfo(
                                                call.getToolCallId(),
                                                call.getToolCallName(),
                                                input))
                                .build());
            } else if (event instanceof ToolResultTextDeltaEvent result) {
                outputs.computeIfAbsent(result.getToolCallId(), id -> new StringBuilder())
                        .append(result.getDelta());
            } else if (event instanceof ToolResultEndEvent result) {
                return toolResult(result);
            } else if (event instanceof RequireExternalExecutionEvent external) {
                return external.getToolCalls().stream().map(this::question).toList();
            } else if (event instanceof RequireUserConfirmEvent) {
                return List.of(
                        text(ChatStreamEventType.ERROR, event.getId(), "工具需要授权，请联系管理员检查工具配置。"));
            } else if (event instanceof AgentEndEvent end) {
                return List.of(
                        ChatStreamEvent.builder()
                                .type(ChatStreamEventType.TEXT)
                                .messageId(end.getReplyId())
                                .isLast(true)
                                .build());
            }
            // AGENT_RESULT repeats the full answer. Deltas have already been sent.
            return List.of();
        }

        private List<ChatStreamEvent> toolResult(ToolResultEndEvent result) {
            ToolUseBlock call = calls.remove(result.getToolCallId());
            if (result.getState() == ToolResultState.RUNNING
                    && "ask_user".equals(result.getToolCallName())
                    && call != null) {
                outputs.remove(result.getToolCallId());
                // 2.0.2 suspends external tools with a RUNNING result; newer 2.x also emits
                // RequireExternalExecutionEvent. Both map to the same application contract.
                return List.of(question(call));
            }
            StringBuilder output = outputs.remove(result.getToolCallId());
            String value = output == null ? "" : output.toString();
            ToolResultBlock block =
                    ToolResultBlock.builder()
                            .id(result.getToolCallId())
                            .name(result.getToolCallName())
                            .state(result.getState())
                            .metadata(result.getMetadata())
                            .output(TextBlock.builder().text(value).build())
                            .build();
            return List.of(
                    handlers.stream()
                            .filter(handler -> handler.supports(block, value))
                            .findFirst()
                            .map(
                                    handler ->
                                            handler.handle(
                                                    block, value, result.getReplyId(), false))
                            .orElseGet(
                                    () ->
                                            ToolResultHandler.defaultHandle(
                                                    block, value, result.getReplyId(), false)));
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> parseArguments(String value) {
            if (value.isBlank()) {
                return Map.of();
            }
            return JsonUtils.getJsonCodec().fromJson(value, Map.class);
        }

        private ChatStreamEvent question(ToolUseBlock tool) {
            Object question = tool.getInput().get("question");
            return ChatStreamEvent.builder()
                    .type(ChatStreamEventType.QUESTION)
                    .content(question == null ? "" : question.toString())
                    .toolCall(new ToolCallInfo(tool.getId(), tool.getName(), tool.getInput()))
                    .build();
        }
    }

    private ChatStreamEvent text(ChatStreamEventType type, String id, String value) {
        return ChatStreamEvent.builder().type(type).messageId(id).content(value).build();
    }
}
