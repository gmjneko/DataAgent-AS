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
package io.github.malonetalk.dto;

import io.github.malonetalk.agent.datasource.QueryResult;
import io.github.malonetalk.entity.Datasource;
import lombok.Builder;

/** 待落库的一条 SQL 执行记录（execute_sql 工具成功执行后由采集点组装）。 */
@Builder
public record SqlTraceRecord(
        String sessionId,
        String userId,
        String traceId,
        Datasource datasource,
        String sql,
        QueryResult result,
        long durationMs) {}
