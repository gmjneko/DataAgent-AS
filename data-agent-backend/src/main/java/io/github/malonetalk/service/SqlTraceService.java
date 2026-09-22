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

import io.github.malonetalk.dto.SqlTraceRecord;
import io.github.malonetalk.dto.SqlTraceResponse;
import io.github.malonetalk.dto.pagination.PageResponse;

public interface SqlTraceService {

    /** 记录一条成功的 SQL 执行；尽力而为，任何失败只记日志，不影响 Agent 主流程。 */
    void record(SqlTraceRecord record);

    PageResponse<SqlTraceResponse> getSessionTracePage(
            String sessionId, Integer page, Integer pageSize);

    SqlTraceResponse getSessionTraceDetail(String sessionId, long id);
}
