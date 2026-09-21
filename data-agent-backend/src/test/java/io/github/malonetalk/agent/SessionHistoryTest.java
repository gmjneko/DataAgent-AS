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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.enums.ChatStreamEventType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SessionHistoryTest {
    @Test
    void historyPreservesStructuredQuestionOptions() {
        var questions =
                List.of(
                        Map.of(
                                "question",
                                "时间范围",
                                "options",
                                List.of("昨天", "近七天"),
                                "multiple",
                                false));
        var input = Map.<String, Object>of("question", "请确认", "questions", questions);
        TurnItem turn =
                ReflectionTestUtils.invokeMethod(
                        new SessionHistory(List.of()),
                        "buildAgentTurn",
                        List.of(
                                ToolUseBlock.builder()
                                        .id("q")
                                        .name("ask_user")
                                        .input(input)
                                        .build()));
        assertNotNull(turn);
        var question =
                turn.timeline().stream()
                        .filter(event -> event.type() == ChatStreamEventType.QUESTION)
                        .findFirst()
                        .orElseThrow();
        assertEquals(input, question.toolCall().input());
        assertEquals("请确认", question.content());
    }

    @Test
    void historyKeepsTextAndToolsInOrderWithoutMergingThinkingAcrossText() {
        SessionHistory service = new SessionHistory(List.of());
        TurnItem turn =
                ReflectionTestUtils.invokeMethod(
                        service,
                        "buildAgentTurn",
                        List.of(
                                ThinkingBlock.builder().thinking("思考一").build(),
                                TextBlock.builder().text("先查询数据").build(),
                                ThinkingBlock.builder().thinking("思考二").build(),
                                ToolUseBlock.builder()
                                        .id("call-1")
                                        .name("query")
                                        .input(Map.of("sql", "SELECT 1"))
                                        .build(),
                                TextBlock.builder().text("最终结论").build()));
        assertNotNull(turn);
        assertEquals(
                List.of(
                        ChatStreamEventType.THINKING,
                        ChatStreamEventType.TEXT,
                        ChatStreamEventType.THINKING,
                        ChatStreamEventType.TOOL_CALL,
                        ChatStreamEventType.TEXT),
                turn.timeline().stream().map(e -> e.type()).toList());
        assertEquals("思考一", turn.timeline().get(0).content());
        assertEquals("思考二", turn.timeline().get(2).content());
        assertEquals("最终结论", turn.timeline().get(4).content());
        assertEquals("先查询数据最终结论", turn.content());
        assertEquals(3, turn.traceSteps().size());
    }
}
