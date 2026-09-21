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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.enums.ChatStreamEventType;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EventConverterTest {
    private final EventConverter converter = new EventConverter(List.of());

    @Test
    void preservesMarkdownWhitespaceAcrossReasoningAndSummaryChunks() {
        List<String> chunks =
                List.of(
                        "##",
                        " ",
                        "分析",
                        "\n",
                        "\n",
                        "| 地区 | 人数 |",
                        "\n",
                        "| --- | --- |",
                        "\n",
                        "| 尖山区 | 6 |",
                        "\n",
                        "\n",
                        ">",
                        " ",
                        "说明",
                        "\t",
                        "保留");
        for (EventType type : List.of(EventType.REASONING, EventType.SUMMARY)) {
            List<ChatStreamEvent> events =
                    chunks.stream()
                            .flatMap(
                                    chunk ->
                                            converter
                                                    .map(
                                                            new Event(
                                                                    type,
                                                                    Msg.builder()
                                                                            .textContent(chunk)
                                                                            .build(),
                                                                    false))
                                                    .stream())
                            .toList();
            assertEquals(chunks.size(), events.size());
            assertEquals(
                    String.join("", chunks),
                    events.stream().map(ChatStreamEvent::content).collect(Collectors.joining()));
        }
    }

    @Test
    void separatesSummaryThinkingFromAnswerWithoutInventingNewlines() {
        Msg msg =
                Msg.builder()
                        .content(
                                List.of(
                                        ThinkingBlock.builder()
                                                .thinking("Let me summarize.")
                                                .build(),
                                        TextBlock.builder().text("##").build(),
                                        TextBlock.builder().text(" ").build(),
                                        TextBlock.builder().text("统计结果").build()))
                        .build();
        List<ChatStreamEvent> events = converter.map(new Event(EventType.SUMMARY, msg, false));
        assertEquals(
                List.of(
                        ChatStreamEventType.THINKING,
                        ChatStreamEventType.SUMMARY,
                        ChatStreamEventType.SUMMARY,
                        ChatStreamEventType.SUMMARY),
                events.stream().map(ChatStreamEvent::type).toList());
        assertEquals(
                "## 统计结果",
                events.stream()
                        .filter(e -> e.type() == ChatStreamEventType.SUMMARY)
                        .map(ChatStreamEvent::content)
                        .collect(Collectors.joining()));
    }

    @Test
    void preservesWhitespaceInThinkingChunks() {
        for (EventType type : List.of(EventType.REASONING, EventType.SUMMARY)) {
            Msg msg =
                    Msg.builder()
                            .content(ThinkingBlock.builder().thinking(" \n\t").build())
                            .build();
            assertEquals(" \n\t", converter.map(new Event(type, msg, false)).get(0).content());
        }
    }

    @Test
    void doesNotRepeatAccumulatedSummaryAfterStreamingChunks() {
        Msg msg =
                Msg.builder()
                        .content(
                                List.of(
                                        ThinkingBlock.builder().thinking("过程").build(),
                                        TextBlock.builder().text("最终回复").build()))
                        .build();
        assertTrue(converter.map(new Event(EventType.SUMMARY, msg, true)).isEmpty());
    }

    @Test
    void ignoresEmptyContentBlocks() {
        Msg msg =
                Msg.builder()
                        .content(
                                List.of(
                                        TextBlock.builder().text("").build(),
                                        ThinkingBlock.builder().thinking("").build()))
                        .build();
        assertTrue(converter.map(new Event(EventType.REASONING, msg, false)).isEmpty());
        assertTrue(converter.map(new Event(EventType.SUMMARY, msg, false)).isEmpty());
    }
}
