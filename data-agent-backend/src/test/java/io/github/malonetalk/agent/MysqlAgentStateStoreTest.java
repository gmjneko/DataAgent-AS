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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "AGENTSCOPE_TEST_DB_URL", matches = ".+")
class MysqlAgentStateStoreTest {
    @TempDir Path workspace;

    @Test
    void savesRestoresIsolatesAndClearsMysqlState() throws Exception {
        var source =
                new DriverManagerDataSource(
                        System.getenv("AGENTSCOPE_TEST_DB_URL"),
                        System.getenv("AGENTSCOPE_TEST_DB_USERNAME"),
                        System.getenv("AGENTSCOPE_TEST_DB_PASSWORD"));
        String table = "agentscope_test_" + UUID.randomUUID().toString().replace("-", "");
        var properties = new AgentProperties();
        properties.setWorkspace(workspace.toString());
        var permissions = new ToolPermissions();
        permissions.allow("ask_user");
        try (var connection = source.getConnection();
                var statement = connection.createStatement()) {
            assertTrue(
                    connection.getCatalog().endsWith("_test"),
                    "Use a dedicated database ending in _test");
            statement.execute(
                    "CREATE TABLE "
                            + table
                            + " (session_id VARCHAR(255) NOT NULL, state_key VARCHAR(255) NOT NULL,"
                            + " item_index INT NOT NULL DEFAULT 0, state_data LONGTEXT NOT NULL,"
                            + " created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME"
                            + " DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY"
                            + " KEY(session_id,state_key,item_index)) ENGINE=InnoDB DEFAULT"
                            + " CHARSET=utf8mb4");
            try {
                var store =
                        new DataAgentStateStore(
                                source,
                                connection.getCatalog(),
                                table,
                                permissions,
                                new SessionTranscript(properties));
                var state = AgentState.builder().userId("1").sessionId("same").build();
                state.contextMutable().add(new UserMessage("retained"));
                store.save("1", "same", "agent_state", state);
                var restored =
                        new DataAgentStateStore(
                                source,
                                connection.getCatalog(),
                                table,
                                permissions,
                                new SessionTranscript(properties));
                assertEquals(
                        "retained",
                        restored.get("1", "same", "agent_state", AgentState.class)
                                .orElseThrow()
                                .getContext()
                                .getFirst()
                                .getTextContent());
                assertFalse(restored.exists("2", "same"));
                restored.delete("1", "same");
                assertFalse(restored.exists("1", "same"));
            } finally {
                statement.execute("DROP TABLE " + table);
            }
        }
    }
}
