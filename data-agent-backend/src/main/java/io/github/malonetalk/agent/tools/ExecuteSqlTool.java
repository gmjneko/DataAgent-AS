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

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxAcquireResult;
import io.github.malonetalk.agent.E2bSandboxProperties;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.agent.datasource.SqlExecutor;
import io.github.malonetalk.agent.sandbox.SandboxDemand;
import io.github.malonetalk.agent.sandbox.SandboxFiles;
import io.github.malonetalk.dto.SqlTraceRecord;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.ToolExceptionMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.SqlTraceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ExecuteSqlTool implements MarkAgentTool {

    static final int INLINE_ROW_LIMIT = 300;

    private final DatasourceService dataSourceService;
    private final SqlExecutor sqlExecutor;
    private final SqlTraceService sqlTraceService;
    private final ToolExceptionMapper toolExceptionMapper;
    private final E2bSandboxProperties e2b;

    @Tool(
            concurrencySafe = true,
            readOnly = true,
            name = "execute_sql",
            description =
                    "Execute SELECT SQL query on the target datasource and return the query result."
                        + " Only supports SELECT queries, does not support INSERT/UPDATE/DELETE or"
                        + " other modification operations. Results with more than 300 rows are"
                        + " saved as a CSV file in the sandbox; use the file tools to read that"
                        + " path.")
    public ToolResultBlock executeSql(
            @ToolParam(name = "sql", description = "The SELECT SQL query statement to execute")
                    String sql,
            RuntimeContext ctx) {
        return toolExceptionMapper.run(
                () -> {
                    Datasource datasource =
                            dataSourceService.getDatasourceForSession(ctx.getSessionId());
                    long startNanos = System.nanoTime();
                    QueryResult result = sqlExecutor.execute(datasource, sql);
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    sqlTraceService.record(
                            SqlTraceRecord.builder()
                                    .sessionId(ctx.getSessionId())
                                    .userId(ctx.getUserId())
                                    .traceId(resolveTraceId(ctx))
                                    .datasource(datasource)
                                    .sql(sql)
                                    .result(result)
                                    .durationMs(durationMs)
                                    .build());
                    return ToolResultBlock.text(presentResult(result, ctx));
                });
    }

    private String resolveTraceId(RuntimeContext ctx) {
        Object value = ctx.get("traceId");
        return value instanceof String traceId && !traceId.isBlank() ? traceId : null;
    }

    private String presentResult(QueryResult result, RuntimeContext ctx) throws Exception {
        if (result.rows().size() > INLINE_ROW_LIMIT) {
            return saveResultFile(result, ctx);
        }
        return formatResult(result);
    }

    private String saveResultFile(QueryResult result, RuntimeContext ctx) throws Exception {
        Sandbox sandbox = boundSandbox(ctx);
        if (sandbox == null) {
            return "Error: E2B sandbox is not active for this call.";
        }
        ctx.put(SandboxDemand.class, SandboxDemand.ACTIVE);
        String relativePath = "sql-results/" + UUID.randomUUID() + ".csv";
        String absolutePath = e2b.workspaceRoot() + "/" + relativePath;
        SandboxFiles.writeText(sandbox, ctx, absolutePath, toCsv(result));
        String message =
                "数据过多，已保存成文件的形式放在沙箱中，路径为"
                        + relativePath
                        + "，你可以使用read_file、write_file、edit_file、grep_files、glob_files、"
                        + "list_files、execute对工作区进行操作";
        if (result.truncated()) {
            message += "。文件包含前" + result.rows().size() + "行";
        }
        return message;
    }

    private static Sandbox boundSandbox(RuntimeContext ctx) {
        if (ctx == null) {
            return null;
        }
        SandboxAcquireResult acquired = ctx.get(SandboxAcquireResult.class);
        return acquired != null ? acquired.getSandbox() : null;
    }

    static String toCsv(QueryResult result) {
        StringBuilder csv = new StringBuilder();
        csv.append(csvLine(result.columns())).append('\n');
        for (Map<String, Object> row : result.rows()) {
            List<String> cells = new ArrayList<>();
            for (String column : result.columns()) {
                Object value = row.get(column);
                cells.add(value == null ? "" : String.valueOf(value));
            }
            csv.append(csvLine(cells)).append('\n');
        }
        return csv.toString();
    }

    private static String csvLine(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(csvCell(cells.get(i)));
        }
        return line.toString();
    }

    private static String csvCell(String value) {
        if (value.indexOf(',') < 0
                && value.indexOf('"') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String formatResult(QueryResult result) {
        if (result.rows().isEmpty()) {
            return "Query result is empty.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Query result (total ").append(result.totalRows()).append(" rows)");
        if (result.truncated()) {
            sb.append(", truncated to show first ").append(result.rows().size()).append(" rows");
        }
        sb.append(":\n");

        sb.append("Columns: ").append(result.columns()).append("\n");

        for (int i = 0; i < result.rows().size(); i++) {
            sb.append("Row ").append(i + 1).append(": ").append(result.rows().get(i)).append("\n");
        }

        return sb.toString();
    }
}
