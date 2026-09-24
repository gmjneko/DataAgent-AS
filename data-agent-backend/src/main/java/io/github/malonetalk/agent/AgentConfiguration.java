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
import io.agentscope.extensions.mysql.snapshot.JdbcSnapshotSpec;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.extensions.sandbox.e2b.E2bPersistenceMode;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxClient;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.github.malonetalk.agent.models.ModelFactory;
import io.github.malonetalk.agent.models.ModelProperties;
import io.github.malonetalk.agent.sandbox.LazySandboxClient;
import io.github.malonetalk.agent.sandbox.SandboxDemandMiddleware;
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
            SessionTranscript transcript,
            E2bSandboxProperties e2b,
            javax.sql.DataSource dataSource)
            throws IOException {
        if (!e2b.isConfigured()) {
            throw new IllegalStateException(
                    "E2B sandbox is not configured (agentscope.e2b.api-key and template-id are"
                            + " required).");
        }

        Toolkit toolkit = new Toolkit();
        tools.forEach(toolkit::registerTool);
        Path workspace = Path.of(properties.getWorkspace()).toAbsolutePath().normalize();
        Files.createDirectories(workspace);

        HarnessAgent agent =
                HarnessAgent.builder()
                        .name("DataAgent")
                        .agentId("data-agent")
                        .sysPrompt(
                                """
                                你是数据分析助手。遵循用户数据权限，查询使用语义层和只读 SQL 工具，口径不明确时调用 ask_user。
                                回答中适合可视化时（如趋势对比、分类分布、占比构成），直接在正文里嵌入 ```echarts 代码块：
                                内容为纯 JSON 的 ECharts Option，只写 title、xAxis、yAxis、series 等必要字段（tooltip/legend 等样式由前端补全）。
                                少量明细数据用 Markdown 表格，单一数值直接用文字。\
                                """)
                        .model(factory.getInstance(model))
                        .toolkit(toolkit)
                        .stateStore(store)
                        .workspace(workspace)
                        .filesystem(e2bFilesystem(e2b, dataSource))
                        .skillRepositories(skillRepositories)
                        .middleware(new DataAgentMiddleware(transcript, store))
                        .middleware(new SandboxDemandMiddleware())
                        .compaction(
                                CompactionConfig.builder()
                                        .triggerMessages(properties.getTriggerMessages())
                                        .keepMessages(properties.getKeepMessages())
                                        .triggerTokens(properties.getTriggerTokens())
                                        .keepTokens(0)
                                        .flushBeforeCompact(false)
                                        .offloadBeforeCompact(true)
                                        .build())
                        .toolResultEviction(
                                ToolResultEvictionConfig.builder()
                                        .maxResultChars(properties.getMaxToolResultChars())
                                        .build())
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
        // Shell stays disabled. File tools run inside the E2B sandbox.
        agent.getToolkit().getToolNames().stream()
                .filter(name -> !List.of("execute", "wait_async_results").contains(name))
                .forEach(permissions::allow);

        return agent;
    }

    private static SandboxFilesystemSpec e2bFilesystem(
            E2bSandboxProperties e2b, javax.sql.DataSource dataSource) {
        return new E2bFilesystemSpec()
                .client(new LazySandboxClient(new E2bSandboxClient()))
                .apiKey(e2b.getApiKey())
                .templateId(e2b.getTemplateId())
                .apiBaseUrl(e2b.getApiBaseUrl())
                .domain(e2b.getDomain())
                .workspaceRoot(e2b.workspaceRoot())
                .sandboxTimeoutSeconds(e2b.getSandboxTimeoutSeconds())
                .readTimeoutSeconds(Math.max(120, e2b.getSandboxTimeoutSeconds()))
                .persistenceMode(E2bPersistenceMode.TAR)
                .snapshotSpec(new JdbcSnapshotSpec(dataSource))
                .isolationScope(IsolationScope.SESSION);
    }
}
