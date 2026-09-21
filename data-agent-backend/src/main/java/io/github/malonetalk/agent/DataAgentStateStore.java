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

import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.State;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import java.util.Optional;

/** Refreshes tool rules at load time, before the framework constructs its permission engine. */
public class DataAgentStateStore extends MysqlAgentStateStore {
    private final ToolPermissions permissions;
    private final SessionTranscript transcript;

    public DataAgentStateStore(
            javax.sql.DataSource dataSource,
            String database,
            String table,
            ToolPermissions permissions,
            SessionTranscript transcript) {
        super(dataSource, database, table, false);
        this.permissions = permissions;
        this.transcript = transcript;
    }

    @Override
    public <T extends State> Optional<T> get(
            String user, String session, String key, Class<T> type) {
        Optional<T> result = super.get(user, session, key, type);
        if (type == AgentState.class && "agent_state".equals(key)) {
            AgentState state =
                    result.map(AgentState.class::cast)
                            .orElseGet(
                                    () ->
                                            AgentState.builder()
                                                    .userId(user)
                                                    .sessionId(session)
                                                    .build());
            state.contextMutable().replaceAll(SessionMessages::normalize);
            state.setPermissionContext(permissions.snapshot());
            return Optional.of(type.cast(state));
        }
        return result;
    }

    @Override
    public void save(String user, String session, String key, State state) {
        if (state instanceof AgentState agentState) {
            agentState.contextMutable().replaceAll(SessionMessages::normalize);
            transcript.append(user, session, agentState.getContext());
        }
        super.save(user, session, key, state);
    }
}
