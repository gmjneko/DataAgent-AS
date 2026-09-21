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
package io.github.malonetalk.agent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.ToolSuspendException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AskUserTool implements MarkAgentTool {

    @Tool(
            name = ToolCallConstants.ASK_USER,
            description =
                    "Ask the user a question when an operation is unclear or requires"
                            + " confirmation. Execution resumes after the user responds."
                            + " When offering choices, ALWAYS use questions with structured options;"
                            + " never embed A/B/C choices in the question text. Group related"
                            + " questions in one call. Users can also write their own answers.")
    public String askUser(
            @ToolParam(name = "question", description = "The question or short introduction to show.")
                    String question,
            @ToolParam(
                            name = "questions",
                            description =
                                    "Structured questions. Each item has question (prompt), options"
                                            + " (array of distinct option labels; empty for free text),"
                                            + " and multiple (true for multiple choice, false for single"
                                            + " choice). Put recommendations in option labels. Omit"
                                            + " only for a simple free-text question.",
                            required = false)
                    List<Question> questions) {
        log.info("Agent asks user: {}", question);
        throw new ToolSuspendException(question);
    }

    public record Question(String question, List<String> options, boolean multiple) {}
}
