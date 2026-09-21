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

import request, { type ApiResponse } from './request';

export interface McpServer {
  id?: number;
  name: string;
  transportType: 'STDIO' | 'SSE' | 'HTTP';
  clientType: 'ASYNC' | 'SYNC';
  command: string;
  args: string[];
  env: Record<string, string>;
  url: string;
  headers: Record<string, string>;
  queryParams: Record<string, string>;
  timeout: number;
  initializationTimeout: number;
  connectTimeout: number;
  httpVersion: 'HTTP_1_1' | 'HTTP_2';
  redirectPolicy: 'NEVER' | 'FOLLOW';
  enableElicitation: boolean;
  enableTools: string[];
  disableTools: string[];
  description: string;
  status?: 'ACTIVE' | 'INACTIVE';
}
export interface McpStatus {
  serverId: number;
  connectionState: 'CONNECTED' | 'DISCONNECTED' | 'FAILED';
  lastConnectedAt?: string;
  error?: string;
  tools: { name: string; description: string; readOnly: boolean }[];
  discoveredTools: { name: string; description: string; readOnly: boolean }[];
}
const base = '/mcp-server';
const connectionTimeout = { timeout: 1_220_000 };
export const listMcp = () => request.get<ApiResponse<McpServer[]>>(base).then(r => r.data.data);
export const saveMcp = (value: McpServer) =>
  (value.id
    ? request.put<ApiResponse<McpServer>>(`${base}/${value.id}`, value, connectionTimeout)
    : request.post<ApiResponse<McpServer>>(base, value, connectionTimeout)
  ).then(r => r.data.data);
export const deleteMcp = (id: number) => request.delete(`${base}/${id}`, connectionTimeout);
export const setMcpEnabled = (id: number, enabled: boolean) =>
  request.put(`${base}/${id}/${enabled ? 'enable' : 'disable'}`, {}, connectionTimeout);
export const mcpStatus = (id: number) =>
  request.get<ApiResponse<McpStatus>>(`${base}/${id}/connection`).then(r => r.data.data);
export const mcpAction = (id: number, action: 'test' | 'refresh' | 'connect' | 'disconnect') =>
  request
    .post<ApiResponse<McpStatus>>(`${base}/${id}/${action}`, {}, connectionTimeout)
    .then(r => r.data.data);
