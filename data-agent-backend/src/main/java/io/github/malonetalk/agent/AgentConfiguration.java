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

import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.github.malonetalk.agent.models.ModelFactory;
import io.github.malonetalk.agent.models.ModelProperties;
import io.github.malonetalk.agent.tools.MarkAgentTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {
    @Bean
    public AgentStateStore agentStateStore(
            javax.sql.DataSource dataSource,
            AgentProperties properties,
            ToolPermissions permissions,
            SessionTranscript transcript)
            throws SQLException {
        try (var connection = dataSource.getConnection()) {
            return new DataAgentStateStore(
                    dataSource,
                    connection.getCatalog(),
                    properties.getStateTable(),
                    permissions,
                    transcript);
        }
    }

    @Bean(destroyMethod = "close")
    public HarnessAgent dataAgent(
            ModelFactory factory,
            ModelProperties model,
            AgentProperties properties,
            List<MarkAgentTool> tools,
            AgentStateStore store,
            @Qualifier("skillRepositories") List<AgentSkillRepository> skillRepositories,
            ToolPermissions permissions,
            SessionTranscript transcript)
            throws IOException {

        Toolkit toolkit = new Toolkit();
        tools.forEach(toolkit::registerTool);
        Path workspace = Path.of(properties.getWorkspace()).toAbsolutePath().normalize();
        Path shared = workspace.resolve("shared");
        Files.createDirectories(shared);

        HarnessAgent agent = HarnessAgent.builder()
                .name("DataAgent")
                .agentId("data-agent")
                .sysPrompt("你是数据分析助手。遵循用户数据权限，查询使用语义层和只读 SQL 工具，口径不明确时调用 ask_user。")
                .model(factory.getInstance(model))
                .toolkit(toolkit)
                .stateStore(store)
                .workspace(workspace)
                .filesystem(
                        new LocalFilesystemSpec()
                                .project(shared)
                                .mode(LocalFsMode.SANDBOXED)
                                .isolationScope(IsolationScope.USER)
                )
                .skillRepositories(skillRepositories)
                .middleware(new DataAgentMiddleware(transcript, store))
                .compaction(
                        CompactionConfig.builder()
                                .triggerMessages(properties.getTriggerMessages())
                                .keepMessages(properties.getKeepMessages())
                                .triggerTokens(properties.getTriggerTokens())
                                .keepTokens(0)
                                .flushBeforeCompact(false)
                                .offloadBeforeCompact(true)
                                .build()
                )
                .toolResultEviction(
                        ToolResultEvictionConfig.builder()
                                .maxResultChars(properties.getMaxToolResultChars())
                                .build()
                )
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableSubagents()
                .disableShellTool()
                .disableToolsConfig()
                .disableDefaultWorkspaceSkills()
                .disableAtPathExpansion()
                .maxIters(properties.getMaxIters())
                .enablePendingToolRecovery(true)
                .build();
        // Filesystem reads are needed for unloaded results; writes/shell are not application tools.
        agent.getToolkit().getToolNames().stream()
                .filter(name -> !List.of("write_file", "edit_file", "execute", "wait_async_results")
                        .contains(name)
                ).forEach(permissions::allow);

        return agent;
    }
}
