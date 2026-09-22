<!--
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
 -->

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { getSqlTraces, getSqlTraceDetail, type SqlTraceResponse } from '@/api/sqlTrace';
  import { formatDateTime } from '@/utils/dateTime';

  interface QueryResultData {
    columns: string[];
    rows: Record<string, unknown>[];
    totalRows: number;
    truncated: boolean;
  }

  const props = defineProps<{
    visible: boolean;
    sessionId: string;
    /** 会话是否已发过消息：未发过消息的会话没有 trace，也无 ownership 绑定，直接展示空态。 */
    ready: boolean;
  }>();

  const emit = defineEmits<{ 'update:visible': [value: boolean] }>();

  const traces = ref<SqlTraceResponse[]>([]);
  const loading = ref(false);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);

  // resultJson 不在列表返回，展开行时按需拉取详情并缓存
  const detailCache = ref<Map<number, SqlTraceResponse>>(new Map());
  const detailLoading = ref<Set<number>>(new Set());

  async function fetchTraces() {
    if (!props.ready) return;
    loading.value = true;
    try {
      const res = await getSqlTraces(props.sessionId, {
        page: page.value,
        pageSize: pageSize.value,
      });
      traces.value = res.data.data.items;
      total.value = res.data.data.total;
    } catch {
      traces.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  async function handleExpandChange(row: SqlTraceResponse, expandedRows: SqlTraceResponse[]) {
    if (!expandedRows.includes(row)) return;
    if (detailCache.value.has(row.id) || detailLoading.value.has(row.id)) return;
    detailLoading.value.add(row.id);
    try {
      const res = await getSqlTraceDetail(props.sessionId, row.id);
      detailCache.value.set(row.id, res.data.data);
    } catch {
      // 失败已由拦截器提示；置空缓存避免展开时反复请求
      detailCache.value.set(row.id, { ...row, resultJson: null });
    } finally {
      detailLoading.value.delete(row.id);
    }
  }

  function resultOf(row: SqlTraceResponse): QueryResultData | null {
    const source = detailCache.value.get(row.id) ?? row;
    if (!source.resultJson) return null;
    try {
      return JSON.parse(source.resultJson) as QueryResultData;
    } catch {
      return null;
    }
  }

  function formatCellValue(value: unknown) {
    if (value === null || value === undefined) return 'NULL';
    if (value instanceof Object) return JSON.stringify(value);
    return String(value);
  }

  function reset() {
    traces.value = [];
    total.value = 0;
    page.value = 1;
    detailCache.value.clear();
    detailLoading.value.clear();
  }

  function handlePageChange(newPage: number) {
    page.value = newPage;
    fetchTraces();
  }

  function handleSizeChange(newSize: number) {
    pageSize.value = newSize;
    page.value = 1;
    fetchTraces();
  }

  watch(
    () => props.visible,
    visible => {
      if (visible) fetchTraces();
    },
  );

  watch(
    () => props.sessionId,
    () => {
      reset();
      if (props.visible) fetchTraces();
    },
  );

  watch(
    () => props.ready,
    (ready, wasReady) => {
      if (ready && !wasReady && props.visible) fetchTraces();
    },
  );
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="SQL 执行记录"
    size="860px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="sql-trace-drawer">
      <template v-if="ready">
        <div class="sql-trace-drawer__toolbar">
          <span v-if="total > 0" class="sql-trace-drawer__meta">共 {{ total }} 条</span>
          <el-button
            text
            size="small"
            :loading="loading"
            class="sql-trace-drawer__refresh"
            @click="fetchTraces"
          >
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="traces"
          row-key="id"
          size="small"
          @expand-change="handleExpandChange"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-loading="detailLoading.has(row.id)" class="sql-trace-drawer__detail">
                <div class="sql-trace-drawer__label">SQL</div>
                <pre class="sql-trace-drawer__sql">{{ row.sql }}</pre>
                <div class="sql-trace-drawer__label">
                  返回结果
                  <span v-if="resultOf(row)?.truncated" class="sql-trace-drawer__note">
                    （已截断，仅返回前 {{ resultOf(row)?.rows.length }} 行）
                  </span>
                </div>
                <el-table
                  v-if="resultOf(row)?.rows.length"
                  :data="resultOf(row)!.rows"
                  border
                  size="small"
                  class="sql-trace-drawer__result"
                >
                  <el-table-column
                    v-for="column in resultOf(row)!.columns"
                    :key="column"
                    :label="column"
                    min-width="130"
                    show-overflow-tooltip
                  >
                    <template #default="scope">{{ formatCellValue(scope.row[column]) }}</template>
                  </el-table-column>
                </el-table>
                <div v-else class="sql-trace-drawer__empty-result">结果为空</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="SQL" min-width="240">
            <template #default="{ row }">
              <span class="sql-trace-drawer__sql-cell">{{ row.sql }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.databaseName || '-' }}</template>
          </el-table-column>
          <el-table-column label="表" min-width="160">
            <template #default="{ row }">
              <el-tag
                v-for="table in row.tableNames"
                :key="table"
                size="small"
                class="sql-trace-drawer__tag"
              >
                {{ table }}
              </el-tag>
              <span v-if="!row.tableNames.length">-</span>
            </template>
          </el-table-column>
          <el-table-column label="行数" width="70" align="right">
            <template #default="{ row }">{{ row.rowCount ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="80" align="right">
            <template #default="{ row }">
              {{ row.durationMs != null ? `${row.durationMs}ms` : '-' }}
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!loading && traces.length === 0" class="sql-trace-drawer__empty">
          本会话还没有执行过 SQL
        </div>

        <div v-if="total > 0" class="sql-trace-drawer__pagination">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </template>

      <div v-else class="sql-trace-drawer__empty">本会话还没有执行过 SQL</div>
    </div>
  </el-drawer>
</template>

<style scoped>
  .sql-trace-drawer {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .sql-trace-drawer__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .sql-trace-drawer__meta {
    color: var(--app-text-muted, #909399);
    font-size: 13px;
  }

  .sql-trace-drawer__refresh {
    margin-left: auto;
  }

  .sql-trace-drawer__sql-cell {
    font-family: ui-monospace, monospace;
    font-size: 12px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    overflow-wrap: anywhere;
  }

  .sql-trace-drawer__tag {
    margin: 1px 4px 1px 0;
  }

  .sql-trace-drawer__detail {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 4px 8px;
  }

  .sql-trace-drawer__label {
    font-size: 13px;
    font-weight: 600;
    color: var(--app-text-secondary, #606266);
  }

  .sql-trace-drawer__note {
    font-weight: 400;
    color: var(--app-text-muted, #909399);
  }

  .sql-trace-drawer__sql {
    margin: 0;
    padding: 12px;
    border: 1px solid var(--app-border, #e4e7ed);
    border-radius: 8px;
    background: var(--app-bg-page, #f5f7fa);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    max-height: 240px;
    overflow: auto;
    font:
      13px/1.5 ui-monospace,
      monospace;
  }

  .sql-trace-drawer__result {
    max-width: 100%;
  }

  .sql-trace-drawer__empty-result {
    color: var(--app-text-muted, #909399);
    font-size: 13px;
    padding: 8px 0;
  }

  .sql-trace-drawer__empty {
    padding: 48px 0;
    text-align: center;
    color: var(--app-text-muted, #909399);
    font-size: 14px;
  }

  .sql-trace-drawer__pagination {
    display: flex;
    justify-content: flex-end;
  }
</style>
