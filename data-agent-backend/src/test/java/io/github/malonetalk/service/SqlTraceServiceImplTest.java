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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.dto.SqlTraceRecord;
import io.github.malonetalk.dto.SqlTraceResponse;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.SqlTrace;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.SqlTraceMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SqlTraceServiceImplTest {

    @Mock private SqlTraceMapper sqlTraceMapper;

    private SqlTraceServiceImpl sqlTraceService;

    @BeforeEach
    void setUp() {
        sqlTraceService = new SqlTraceServiceImpl(sqlTraceMapper, new ObjectMapper());
    }

    @Test
    void recordInsertsFullyPopulatedTrace() {
        QueryResult result = QueryResult.builder().addColumn("id").totalRows(3).build();

        sqlTraceService.record(
                SqlTraceRecord.builder()
                        .sessionId("session-1")
                        .userId("42")
                        .traceId("trace-1")
                        .datasource(datasource())
                        .sql(
                                "SELECT u.id FROM users u JOIN orders o ON o.user_id = u.id WHERE"
                                        + " o.amount > 100 AND u.id IN (SELECT user_id FROM vip)")
                        .result(result)
                        .durationMs(55)
                        .build());

        SqlTrace trace = captureInsertedTrace();
        assertEquals("session-1", trace.getSessionId());
        assertEquals(42, trace.getUserId());
        assertEquals("trace-1", trace.getTraceId());
        assertEquals(1, trace.getDatasourceId());
        assertEquals("主库", trace.getDatasourceName());
        assertEquals("shop", trace.getDatabaseName());
        assertEquals("orders,users,vip", trace.getTableNames());
        assertEquals(3, trace.getRowCount());
        assertFalse(trace.getTruncated());
        assertEquals(55L, trace.getDurationMs());
        assertNotNull(trace.getCreateTime());
        assertTrue(trace.getResultJson().contains("\"columns\":[\"id\"]"));
    }

    @Test
    void recordToleratesNonNumericUserId() {
        sqlTraceService.record(
                SqlTraceRecord.builder()
                        .sessionId("session-1")
                        .userId("not-a-number")
                        .datasource(datasource())
                        .sql("SELECT id FROM users")
                        .result(emptyResult())
                        .durationMs(1)
                        .build());

        assertNull(captureInsertedTrace().getUserId());
    }

    @Test
    void recordLeavesTableNamesNullWhenSqlUnparseable() {
        sqlTraceService.record(
                SqlTraceRecord.builder()
                        .sessionId("session-1")
                        .datasource(datasource())
                        .sql("SELECT FROM WHERE")
                        .result(emptyResult())
                        .durationMs(1)
                        .build());

        assertNull(captureInsertedTrace().getTableNames());
    }

    @Test
    void recordFallsBackToNullResultJsonWhenSerializationFails() {
        QueryResult.Builder builder = QueryResult.builder().addColumn("v");
        // Jackson 对无任何可序列化属性的 Object 抛 InvalidDefinitionException。
        builder.addRow(Map.of("v", new Object()));
        QueryResult result = builder.build();

        sqlTraceService.record(
                SqlTraceRecord.builder()
                        .sessionId("session-1")
                        .datasource(datasource())
                        .sql("SELECT v FROM users")
                        .result(result)
                        .durationMs(1)
                        .build());

        SqlTrace trace = captureInsertedTrace();
        assertNull(trace.getResultJson());
        assertEquals("users", trace.getTableNames());
    }

    @Test
    void recordSwallowsInsertFailure() {
        when(sqlTraceMapper.insert(any(SqlTrace.class)))
                .thenThrow(new RuntimeException("metadata db down"));

        assertDoesNotThrow(
                () ->
                        sqlTraceService.record(
                                SqlTraceRecord.builder()
                                        .sessionId("session-1")
                                        .datasource(datasource())
                                        .sql("SELECT id FROM users")
                                        .result(emptyResult())
                                        .durationMs(1)
                                        .build()));
    }

    @Test
    void detailThrowsWhenTraceMissing() {
        when(sqlTraceMapper.selectByIdAndSessionId(9L, "session-1")).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> sqlTraceService.getSessionTraceDetail("session-1", 9L));
    }

    @Test
    void detailSplitsAndTrimsTableNames() {
        SqlTrace trace = new SqlTrace();
        trace.setId(9L);
        trace.setSessionId("session-1");
        trace.setTableNames(" users , orders,,");
        trace.setSqlText("SELECT id FROM users");
        when(sqlTraceMapper.selectByIdAndSessionId(9L, "session-1")).thenReturn(trace);

        SqlTraceResponse response = sqlTraceService.getSessionTraceDetail("session-1", 9L);

        assertEquals(List.of("users", "orders"), response.tableNames());
        assertNull(response.resultJson());
    }

    private static Datasource datasource() {
        return Datasource.builder().id(1).name("主库").databaseName("shop").build();
    }

    private static QueryResult emptyResult() {
        return QueryResult.builder().totalRows(0).build();
    }

    private SqlTrace captureInsertedTrace() {
        ArgumentCaptor<SqlTrace> captor = ArgumentCaptor.forClass(SqlTrace.class);
        verify(sqlTraceMapper).insert(captor.capture());
        return captor.getValue();
    }
}
