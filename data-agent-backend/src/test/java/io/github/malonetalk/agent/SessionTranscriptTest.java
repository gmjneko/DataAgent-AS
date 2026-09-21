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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionTranscriptTest {
    @TempDir Path workspace;

    private SessionTranscript transcript() {
        AgentProperties properties = new AgentProperties();
        properties.setWorkspace(workspace.toString());
        return new SessionTranscript(properties);
    }

    @Test
    void survivesRestartDeduplicatesAndPreservesOriginalToolOutputs() {
        var history = transcript();
        var user = new UserMessage("分析数据");
        var tool =
                new ToolResultMessage(
                        ToolResultBlock.text("完整查询结果")
                                .withIdAndName("call", "execute_sql")
                                .withState(ToolResultState.SUCCESS));
        history.append("1", "session", List.of(user, tool));
        history.append(
                "1",
                "session",
                List.of(
                        user,
                        tool.withContent(
                                List.of(
                                        ToolResultBlock.text("已卸载")
                                                .withIdAndName("call", "execute_sql")))));
        var restored = transcript().read("1", "session");
        assertEquals(2, restored.size());
        assertEquals(
                "完整查询结果",
                ((TextBlock)
                                ((ToolResultBlock) restored.get(1).getContent().getFirst())
                                        .getOutput()
                                        .getFirst())
                        .getText());
        assertTrue(history.read("2", "session").isEmpty());
        history.delete("1", "session");
        assertTrue(history.read("1", "session").isEmpty());
    }

    @Test
    void recoversCompleteRecordsAfterAnInterruptedAppend() throws Exception {
        var history = transcript();
        history.append("1", "s", List.of(new UserMessage("first")));
        Files.writeString(history.path("1", "s"), "{\"id\":", StandardOpenOption.APPEND);
        assertEquals(1, history.read("1", "s").size());
        history.append("1", "s", List.of(new UserMessage("second")));
        assertEquals(2, history.read("1", "s").size());
    }

    @Test
    void modelSummariesDoNotBecomeUserHistory() {
        var history = transcript();
        history.append(
                "1",
                "s",
                List.of(
                        Msg.builder()
                                .role(MsgRole.USER)
                                .name(ConversationCompactor.SUMMARY_MSG_NAME)
                                .textContent("model summary")
                                .build()));
        assertTrue(history.read("1", "s").isEmpty());
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> transcript().read("1", "../other"));
        assertThrows(IllegalArgumentException.class, () -> transcript().read("../2", "session"));
    }
}
