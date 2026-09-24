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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxAcquireResult;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.github.malonetalk.agent.E2bSandboxProperties;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.ToolExceptionMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.SqlTraceService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecuteSqlToolTest {

    @Test
    void keepsUpTo300RowsInline() throws Exception {
        RecordingSandbox sandbox = new RecordingSandbox();
        ExecuteSqlTool tool = tool(rows(ExecuteSqlTool.INLINE_ROW_LIMIT, false));

        String text = text(tool.executeSql("select id from t", context(sandbox)));

        assertTrue(text.startsWith("Query result (total 300 rows)"));
        assertEquals(0, sandbox.commands.size());
    }

    @Test
    void savesMoreThan300RowsIntoSandbox() throws Exception {
        RecordingSandbox sandbox = new RecordingSandbox();
        QueryResult result = rows(ExecuteSqlTool.INLINE_ROW_LIMIT + 1, false);
        ExecuteSqlTool tool = tool(result);

        String text = text(tool.executeSql("select id from t", context(sandbox)));

        assertTrue(text.startsWith("数据过多，已保存成文件的形式放在沙箱中，路径为sql-results/"));
        assertTrue(
                text.contains(
                        "read_file、write_file、edit_file、grep_files、glob_files、list_files、"
                                + "execute"));
        assertTrue(
                String.join("\n", sandbox.commands).contains(base64(ExecuteSqlTool.toCsv(result))));
        assertFalse(text.contains("文件包含前"));
    }

    private static ExecuteSqlTool tool(QueryResult result) throws Exception {
        DatasourceService datasources = mock(DatasourceService.class);
        when(datasources.getDatasourceForSession(any())).thenReturn(mock(Datasource.class));
        SqlExecutor sqlExecutor = mock(SqlExecutor.class);
        when(sqlExecutor.execute(any(), any())).thenReturn(result);
        ToolExceptionMapper mapper = mock(ToolExceptionMapper.class);
        when(mapper.run(any()))
                .thenAnswer(
                        invocation ->
                                ((ToolExceptionMapper.ToolAction) invocation.getArgument(0)).run());
        return new ExecuteSqlTool(
                datasources,
                sqlExecutor,
                mock(SqlTraceService.class),
                mapper,
                new E2bSandboxProperties());
    }

    private static QueryResult rows(int count, boolean truncated) {
        QueryResult.Builder builder = QueryResult.builder().addColumn("id");
        for (int i = 0; i < count; i++) {
            builder.newRow().put("id", i + 1);
        }
        return builder.totalRows(count).truncated(truncated).build();
    }

    private static RuntimeContext context(Sandbox sandbox) {
        return RuntimeContext.builder()
                .sessionId("conv-1")
                .userId("42")
                .put(SandboxAcquireResult.class, SandboxAcquireResult.selfManaged(sandbox))
                .build();
    }

    private static String text(ToolResultBlock block) {
        return ((TextBlock) block.getOutput().getFirst()).getText();
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingSandbox implements Sandbox {
        private final List<String> commands = new ArrayList<>();

        @Override
        public ExecResult exec(
                RuntimeContext runtimeContext, String command, Integer timeoutSeconds) {
            commands.add(command);
            return new ExecResult(0, "", "", false);
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void shutdown() {}

        @Override
        public void close() {}

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public SandboxState getState() {
            return null;
        }

        @Override
        public InputStream persistWorkspace() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void hydrateWorkspace(InputStream archive) {}
    }
}
