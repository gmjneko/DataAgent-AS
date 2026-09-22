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

import request from './request';
import type { ApiResponse } from './request';
import type { PageResponse } from './types';

export interface SqlTraceResponse {
  id: number;
  sessionId: string;
  traceId: string | null;
  datasourceName: string | null;
  databaseName: string | null;
  tableNames: string[];
  sql: string;
  rowCount: number | null;
  truncated: boolean;
  durationMs: number | null;
  createTime: string;
  /** 列表查询不返回；详情查询返回完整结果 JSON（columns/rows/totalRows/truncated）。 */
  resultJson: string | null;
}

export interface SqlTracePageQuery {
  page?: number;
  pageSize?: number;
}

export function getSqlTraces(sessionId: string, query: SqlTracePageQuery = {}) {
  return request.get<ApiResponse<PageResponse<SqlTraceResponse>>>(
    `/agent/session/${sessionId}/sql-trace`,
    { params: query },
  );
}

export function getSqlTraceDetail(sessionId: string, id: number) {
  return request.get<ApiResponse<SqlTraceResponse>>(`/agent/session/${sessionId}/sql-trace/${id}`);
}
