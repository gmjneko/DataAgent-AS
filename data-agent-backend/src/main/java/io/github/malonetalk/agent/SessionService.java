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
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.dto.SessionDatasourceBinding;
import io.github.malonetalk.dto.SessionInfo;
import io.github.malonetalk.dto.TurnItem;
import io.github.malonetalk.entity.UserSession;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.SessionDatasourceMapper;
import io.github.malonetalk.mapper.UserSessionMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final HarnessAgent agent;
    private final SessionOperations operations;
    private final AgentStateStore stateStore;
    private final SessionTranscript transcript;
    private final SessionHistory history;
    private final SessionDatasourceMapper sessionDatasourceMapper;
    private final UserSessionMapper userSessionMapper;

    public synchronized void bindUserSession(int userId, String sessionId) {
        SessionTranscript.validateSessionId(sessionId);
        Integer owner = userSessionMapper.selectOwner(sessionId);
        if (owner != null && owner != userId) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        userSessionMapper.insertIgnore(userId, sessionId);
        requireOwnership(userId, sessionId);
    }

    public int owner(String sessionId, Integer userId) {
        SessionTranscript.validateSessionId(sessionId);
        Integer owner = userSessionMapper.selectOwner(sessionId);
        if (owner == null || (userId != null && !Objects.equals(owner, userId))) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return owner;
    }

    public void requireOwnership(Integer userId, String sessionId) {
        owner(sessionId, userId);
    }

    public List<Msg> getSessionDebug(String sessionId, Integer userId) {
        int owner = owner(sessionId, userId);
        return stateStore
                .get(String.valueOf(owner), sessionId, "agent_state", AgentState.class)
                .map(AgentState::getContext)
                .orElse(List.of());
    }

    public List<TurnItem> getSessionHistory(String sessionId, Integer userId) {
        return history.buildTurnItems(
                transcript.read(String.valueOf(owner(sessionId, userId)), sessionId));
    }

    public void clearSession(String sessionId, Integer userId) {
        operations.run(sessionId, () -> clearSessionState(sessionId, userId));
    }

    private void clearSessionState(String sessionId, Integer userId) {
        String owner = String.valueOf(owner(sessionId, userId));
        agent.clearContext(owner, sessionId);
        stateStore.delete(owner, sessionId);
        agent.clearStateCache(owner, sessionId);
        transcript.delete(owner, sessionId);
        sessionDatasourceMapper.deleteBySessionId(sessionId);
        userSessionMapper.deleteBySessionId(sessionId);
    }

    public void clearAllSessions(Integer userId) {
        for (UserSession session : userSessionMapper.selectAll()) {
            if (userId == null || Objects.equals(userId, session.getUserId())) {
                clearSession(session.getSessionId(), userId);
            }
        }
    }

    public List<SessionInfo> listSessions(Integer userId) {
        var bindings = sessionDatasourceMapper.listBindingsWithDatasourceName();
        return userSessionMapper.selectAll().stream()
                .filter(session -> userId == null || Objects.equals(userId, session.getUserId()))
                .filter(session ->
                        stateStore.exists(String.valueOf(session.getUserId()), session.getSessionId()))
                .map(session -> {
                    String id = session.getSessionId();
                    String uid = String.valueOf(session.getUserId());
                    List<Msg> messages = transcript.read(uid, id);
                    String title = messages.stream()
                            .filter(msg -> msg.getRole() == MsgRole.USER)
                            .flatMap(msg -> msg.getContent().stream())
                            .filter(TextBlock.class::isInstance)
                            .map(TextBlock.class::cast)
                            .map(TextBlock::getText)
                            .findFirst()
                            .orElse(id);
                    String created = session.getCreateTime().toString();
                    String updated = created;
                    try {
                        updated = Files.getLastModifiedTime(transcript.path(uid, id))
                                .toInstant()
                                .toString();
                    } catch (IOException ignored) {
                        /* No transcript yet. */
                    }
                    var binding = bindings.stream()
                            .filter(row -> id.equals(row.getSessionId()))
                            .findFirst();
                    return new SessionInfo(
                            id,
                            title.substring(0, Math.min(title.length(), 30)),
                            created,
                            updated,
                            binding.map(SessionDatasourceBinding::getDatasourceId).orElse(null),
                            binding.map(SessionDatasourceBinding::getDatasourceName).orElse(null));
                })
                .sorted(Comparator.comparing(SessionInfo::lastActiveAt).reversed())
                .toList();
    }
}
