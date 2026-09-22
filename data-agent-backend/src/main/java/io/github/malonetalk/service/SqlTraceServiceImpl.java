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
package io.github.malonetalk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.dto.SqlTraceRecord;
import io.github.malonetalk.dto.SqlTraceResponse;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.SqlTrace;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.SqlTraceMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTraceServiceImpl implements SqlTraceService {

    private final SqlTraceMapper sqlTraceMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void record(SqlTraceRecord record) {
        try {
            sqlTraceMapper.insert(toEntity(record));
        } catch (Exception e) {
            // 尽力而为：trace 落库失败（含结果序列化降级之外的所有异常）不能影响 Agent 主流程。
            log.error("Failed to record SQL trace: sessionId={}", record.sessionId(), e);
        }
    }

    @Override
    public PageResponse<SqlTraceResponse> getSessionTracePage(
            String sessionId, Integer page, Integer pageSize) {
        int resolvedPage = PageResponse.resolvePage(page);
        int resolvedPageSize = PageResponse.resolvePageSize(pageSize);
        Page<Object> startedPage = PageHelper.startPage(resolvedPage, resolvedPageSize);
        Page<SqlTrace> tracePage = (Page<SqlTrace>) sqlTraceMapper.selectBySessionId(sessionId);
        List<SqlTraceResponse> items =
                tracePage.getResult().stream().map(this::toResponse).toList();
        startedPage.close();
        return PageResponse.of(items, tracePage.getTotal(), resolvedPage, resolvedPageSize);
    }

    @Override
    public SqlTraceResponse getSessionTraceDetail(String sessionId, long id) {
        SqlTrace trace = sqlTraceMapper.selectByIdAndSessionId(id, sessionId);
        if (trace == null) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_NOT_FOUND, "SQL trace does not exist: id=" + id);
        }
        return toResponse(trace);
    }

    private SqlTrace toEntity(SqlTraceRecord record) {
        Datasource datasource = record.datasource();
        QueryResult result = record.result();
        List<String> tables = extractTableNames(record.sql());

        SqlTrace trace = new SqlTrace();
        trace.setSessionId(record.sessionId());
        trace.setUserId(parseUserId(record.userId()));
        trace.setTraceId(record.traceId());
        trace.setDatasourceId(datasource.getId());
        trace.setDatasourceName(datasource.getName());
        trace.setDatabaseName(datasource.getDatabaseName());
        trace.setTableNames(tables.isEmpty() ? null : String.join(",", tables));
        trace.setSqlText(record.sql());
        trace.setResultJson(serializeResult(result));
        trace.setRowCount(result.totalRows());
        trace.setTruncated(result.truncated());
        trace.setDurationMs(record.durationMs());
        trace.setCreateTime(LocalDateTime.now());
        return trace;
    }

    /** 表名提取失败不阻塞 trace 落库（此时 SQL 已通过 SqlExecutor 校验，失败概率极低）。 */
    private List<String> extractTableNames(String sql) {
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select select) {
                Set<String> tables = new TablesNamesFinder().getTables((Statement) select);
                if (tables == null) {
                    return List.of();
                }
                return tables.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
            }
        } catch (JSQLParserException e) {
            log.warn("Failed to extract table names from SQL: {}", e.getMessage());
        }
        return List.of();
    }

    /** 序列化失败时降级存 null，保证 SQL 本身的执行记录不丢。 */
    private String serializeResult(QueryResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize query result for SQL trace: {}", e.getMessage());
            return null;
        }
    }

    private Integer parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SqlTraceResponse toResponse(SqlTrace trace) {
        return new SqlTraceResponse(
                trace.getId(),
                trace.getSessionId(),
                trace.getTraceId(),
                trace.getDatasourceName(),
                trace.getDatabaseName(),
                splitTableNames(trace.getTableNames()),
                trace.getSqlText(),
                trace.getRowCount(),
                trace.getTruncated(),
                trace.getDurationMs(),
                trace.getCreateTime(),
                trace.getResultJson());
    }

    private List<String> splitTableNames(String tableNames) {
        if (tableNames == null || tableNames.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tableNames.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }
}
