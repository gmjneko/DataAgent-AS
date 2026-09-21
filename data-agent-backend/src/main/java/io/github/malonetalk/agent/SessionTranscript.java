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

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import io.agentscope.harness.agent.workspace.WorkspaceConstants;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Full-fidelity companion to Harness's rendered session log; preserves tool blocks for the UI.
 */
@Component
@RequiredArgsConstructor
public class SessionTranscript {
    private final AgentProperties properties;

    public static void validateSessionId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException(
                    "sessionId must contain 1-128 letters, digits, underscores or hyphens");
        }
    }

    public Path path(String userId, String sessionId) {
        validateSessionId(sessionId);
        if (userId == null || !userId.matches("[0-9]+")) {
            throw new IllegalArgumentException("A numeric authenticated userId is required");
        }
        return Path.of(properties.getWorkspace())
                .toAbsolutePath()
                .normalize()
                .resolve(userId)
                .resolve("agents/data-agent/sessions")
                .resolve(sessionId + ".messages.jsonl");
    }

    public synchronized List<Msg> read(String userId, String sessionId) {
        return List.copyOf(readEntries(path(userId, sessionId)).values());
    }

    private Map<String, Msg> readEntries(Path path) {
        Map<String, Msg> messages = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return messages;
        }
        try {
            String data = Files.readString(path);
            // A crash can leave the last append incomplete. Complete records remain readable.
            String committed = data.substring(0, data.lastIndexOf('\n') + 1);
            committed.lines()
                    .filter(line -> !line.isBlank())
                    .forEach(line -> {
                        Msg message = JsonUtils.getJsonCodec().fromJson(line, Msg.class);
                        messages.put(message.getId(), message);
                    });
            return messages;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read session history", e);
        }
    }

    public synchronized void append(String userId, String sessionId, List<Msg> messages) {
        Path path = path(userId, sessionId);
        Map<String, Msg> existing = readEntries(path);
        StringBuilder additions = new StringBuilder();
        for (Msg original : messages) {
            Msg message = SessionMessages.normalize(original);
            if (message.getRole() == MsgRole.SYSTEM
                    || ConversationCompactor.SUMMARY_MSG_NAME.equals(message.getName())) {
                continue;
            }
            String json = JsonUtils.getJsonCodec().toJson(message);
            Msg previous = existing.get(message.getId());
            // A terminal tool result is immutable in history, even when the model view is
            // offloaded.
            if (previous != null && previous.getContent().stream()
                    .anyMatch(block ->
                            block instanceof ToolResultBlock result
                                    && result.getState()
                                    != ToolResultState.RUNNING)) {
                continue;
            }
            existing.put(message.getId(), message);
            if (previous == null || !json.equals(JsonUtils.getJsonCodec().toJson(previous))) {
                additions.append(json).append('\n');
            }
        }
        if (additions.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                String data = Files.readString(path);
                if (!data.endsWith("\n")) {
                    int length = data.substring(0, data.lastIndexOf('\n') + 1)
                            .getBytes(StandardCharsets.UTF_8)
                            .length;
                    try (var file = FileChannel.open(path, StandardOpenOption.WRITE)) {
                        file.truncate(length);
                    }
                }
            }
            Files.writeString(
                    path, additions, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist session history", e);
        }
    }

    public synchronized void delete(String userId, String sessionId) {
        Path transcript = path(userId, sessionId);
        try {
            Files.deleteIfExists(transcript);
            Files.deleteIfExists(transcript.resolveSibling(sessionId + ".log.jsonl"));
            Files.deleteIfExists(
                    transcript.resolveSibling(sessionId + WorkspaceConstants.SESSION_CONTEXT_EXT));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete session history", e);
        }
    }
}
