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

import type { McpServer } from '@/api/mcp';

export const emptyMcp = (): McpServer => ({
  name: '',
  transportType: 'HTTP',
  clientType: 'ASYNC',
  command: '',
  args: [],
  env: {},
  url: '',
  headers: {},
  queryParams: {},
  timeout: 120000,
  initializationTimeout: 30000,
  connectTimeout: 10000,
  httpVersion: 'HTTP_1_1',
  redirectPolicy: 'NEVER',
  enableElicitation: false,
  enableTools: [],
  disableTools: [],
  description: '',
});
export function validateMcp(value: McpServer): string | undefined {
  if (!value.name.trim()) return '请输入服务名称';
  if (value.transportType === 'STDIO') {
    if (!value.command.trim()) return '请输入启动命令';
  } else {
    try {
      const url = new URL(value.url);
      if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password)
        return '请输入 HTTP(S) 地址，凭据使用请求头';
    } catch {
      return '请输入有效的服务地址';
    }
  }
  if (
    [value.timeout, value.initializationTimeout, value.connectTimeout].some(
      t => !Number.isInteger(t) || t < 1 || t > 600000,
    )
  )
    return '超时必须为 1 到 600000 毫秒';
}
export const splitLines = (value: string) =>
  value
    .split('\n')
    .map(v => v.trim())
    .filter(Boolean);
