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

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.UserSessionMapper;

import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * User-session binding and ownership checks backed by the user_session table. Deliberately free
 * of HarnessAgent dependencies: agent tools and their backing services live inside the agent's
 * dependency closure and may rely on this class without cycling back to the agent.
 */
@Component
@RequiredArgsConstructor
public class SessionOwnership {

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
}
