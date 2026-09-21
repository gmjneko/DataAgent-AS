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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.github.malonetalk.enums.ChatStreamEventType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventConverterTest {
    private final EventConverter converter = new EventConverter(List.of());

    @Test
    void preservesWhitespaceAndDoesNotRepeatFinalMessage() {
        var map = converter.newStreamMapper();
        StringBuilder text = new StringBuilder();
        for (String chunk : List.of("##", " ", "分析", "\n\n", "| a |", "\t")) {
            text.append(
                    map.apply(new TextBlockDeltaEvent("reply", "text", chunk))
                            .getFirst()
                            .content());
        }
        assertEquals("## 分析\n\n| a |\t", text.toString());
        assertEquals(
                " \n\t",
                map.apply(new ThinkingBlockDeltaEvent("r", "thinking", " \n\t"))
                        .getFirst()
                        .content());
        assertTrue(map.apply(new AgentResultEvent(new AssistantMessage("完整回复"))).isEmpty());
        assertTrue(map.apply(new AgentEndEvent("reply")).getFirst().isLast());
    }

    @Test
    void toolArgumentsAccumulateAndSubscriptionsAreIsolated() {
        var first = converter.newStreamMapper();
        var second = converter.newStreamMapper();
        first.apply(new ToolCallDeltaEvent("r", "call", "query", "{\"sql\":"));
        second.apply(new ToolCallDeltaEvent("r", "call", "query", "{\"sql\":\"SELECT 2\"}"));
        first.apply(new ToolCallDeltaEvent("r", "call", "query", "\"SELECT 1\"}"));
        assertEquals(
                "SELECT 1",
                first.apply(new ToolCallEndEvent("r", "call", "query"))
                        .getFirst()
                        .toolCall()
                        .input()
                        .get("sql"));
        assertEquals(
                "SELECT 2",
                second.apply(new ToolCallEndEvent("r", "call", "query"))
                        .getFirst()
                        .toolCall()
                        .input()
                        .get("sql"));
    }

    @Test
    void exposesExternalQuestionsAndToolFailures() {
        var map = converter.newStreamMapper();
        var tool =
                ToolUseBlock.builder()
                        .id("q")
                        .name("ask_user")
                        .input(Map.of("question", "哪一年？"))
                        .build();
        var question = map.apply(new RequireExternalExecutionEvent("r", List.of(tool))).getFirst();
        assertEquals(ChatStreamEventType.QUESTION, question.type());
        assertEquals("哪一年？", question.content());
        map.apply(new ToolResultTextDeltaEvent("r", "q", "query", "查询失败"));
        var result =
                map.apply(new ToolResultEndEvent("r", "q", "query", ToolResultState.ERROR))
                        .getFirst();
        assertEquals(ChatStreamEventType.ERROR, result.type());
        assertEquals("查询失败", result.content());
    }

    @Test
    void preservesStructuredQuestionsWhenExternalToolSuspends() {
        var questions =
                List.of(
                        Map.of(
                                "question",
                                "活跃口径",
                                "options",
                                List.of("用户数", "登录次数"),
                                "multiple",
                                true));
        var input = Map.<String, Object>of("question", "请确认统计口径", "questions", questions);
        var map = converter.newStreamMapper();
        map.apply(
                new ToolCallDeltaEvent(
                        "r",
                        "q",
                        "ask_user",
                        "{\"question\":\"请确认统计口径\",\"questions\":[{\"question\":\"活跃口径\","
                                + "\"options\":[\"用户数\",\"登录次数\"],\"multiple\":true}]}"));
        map.apply(new ToolCallEndEvent("r", "q", "ask_user"));
        var running =
                map.apply(new ToolResultEndEvent("r", "q", "ask_user", ToolResultState.RUNNING))
                        .getFirst();
        assertEquals(ChatStreamEventType.QUESTION, running.type());
        assertEquals(input, running.toolCall().input());
        var tool = ToolUseBlock.builder().id("q").name("ask_user").input(input).build();
        var external = map.apply(new RequireExternalExecutionEvent("r", List.of(tool))).getFirst();
        assertEquals(input, external.toolCall().input());
        assertEquals("请确认统计口径", external.content());
    }

    @Test
    void blockIdsAreUniqueAcrossReasoningIterations() {
        var map = converter.newStreamMapper();
        assertNotEquals(
                map.apply(new TextBlockDeltaEvent("r1", "text", "a")).getFirst().messageId(),
                map.apply(new TextBlockDeltaEvent("r2", "text", "b")).getFirst().messageId());
    }
}
