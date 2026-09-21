<!--
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
  import { onMounted, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    listMcp,
    saveMcp,
    deleteMcp,
    setMcpEnabled,
    mcpStatus,
    mcpAction,
    type McpServer,
    type McpStatus,
  } from '@/api/mcp';
  import McpForm from './McpForm.vue';
  const servers = ref<McpServer[]>([]);
  const states = ref<Record<number, McpStatus>>({});
  const busy = ref(false);
  const dialog = ref(false);
  const editing = ref<McpServer | null>(null);
  const tools = ref<McpStatus | null>(null);
  async function load() {
    servers.value = await listMcp();
    await Promise.all(
      servers.value.map(async s => {
        states.value[s.id!] = await mcpStatus(s.id!);
      }),
    );
  }
  async function perform(action: () => Promise<unknown>) {
    busy.value = true;
    try {
      await action();
      await load();
    } finally {
      busy.value = false;
    }
  }
  function edit(server: McpServer | null) {
    editing.value = server;
    dialog.value = true;
  }
  async function save(value: McpServer) {
    await perform(async () => {
      await saveMcp(value);
      dialog.value = false;
      ElMessage.success('配置已保存');
    });
  }
  async function remove(server: McpServer) {
    try {
      await ElMessageBox.confirm(`删除 ${server.name} 并移除其工具？`, '删除 MCP Server', {
        type: 'warning',
      });
    } catch {
      return;
    }
    await perform(() => deleteMcp(server.id!));
  }
  async function action(
    server: McpServer,
    operation: 'test' | 'refresh' | 'connect' | 'disconnect',
  ) {
    await perform(async () => {
      const result = await mcpAction(server.id!, operation);
      if (result.error) ElMessage.error(result.error);
      else
        ElMessage.success(
          operation === 'test' ? `连接成功，发现 ${result.tools.length} 个工具` : '操作完成',
        );
      if (operation === 'test') tools.value = result;
    });
  }
  const labels = { CONNECTED: '已连接', DISCONNECTED: '未连接', FAILED: '连接失败' };
  onMounted(async () => {
    busy.value = true;
    try {
      await load();
    } finally {
      busy.value = false;
    }
  });
</script>
<template>
  <section class="mcp-page">
    <header>
      <div>
        <h2>MCP 管理</h2>
        <p>连接外部工具服务，启用后供所有登录用户使用。</p>
      </div>
      <el-button type="primary" :disabled="busy" @click="edit(null)">新增服务</el-button>
    </header>
    <el-table
      v-loading="busy"
      :data="servers"
      row-key="id"
      empty-text="暂无 MCP Server，点击新增服务进行配置"
    >
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="transportType" label="传输" width="95" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 'ACTIVE'"
            :disabled="busy"
            @change="perform(() => setMcpEnabled(row.id, row.status !== 'ACTIVE'))"
          />
        </template>
      </el-table-column>
      <el-table-column label="连接状态" min-width="125">
        <template #default="{ row }">
          <el-tag
            :type="
              states[row.id]?.connectionState === 'CONNECTED'
                ? 'success'
                : states[row.id]?.connectionState === 'FAILED'
                  ? 'danger'
                  : 'info'
            "
          >
            {{ labels[states[row.id]?.connectionState ?? 'DISCONNECTED'] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="工具" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="tools = states[row.id] ?? null">
            {{ states[row.id]?.tools.length ?? 0 }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="最近连接" min-width="170">
        <template #default="{ row }">
          {{
            states[row.id]?.lastConnectedAt
              ? new Date(states[row.id]!.lastConnectedAt!).toLocaleString()
              : '—'
          }}
        </template>
      </el-table-column>
      <el-table-column label="最近错误" min-width="180">
        <template #default="{ row }">{{ states[row.id]?.error ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="310" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="busy" @click="edit(row)">编辑</el-button>
          <el-button link :disabled="busy" @click="action(row, 'test')">测试</el-button>
          <el-button
            link
            :disabled="busy || row.status !== 'ACTIVE'"
            @click="action(row, 'refresh')"
          >
            连接 / 刷新
          </el-button>
          <el-button link :disabled="busy" @click="action(row, 'disconnect')">断开</el-button>
          <el-button link type="danger" :disabled="busy" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog
      v-model="dialog"
      :title="editing ? '编辑 MCP Server' : '新增 MCP Server'"
      width="min(720px, 95vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      destroy-on-close
    >
      <McpForm :value="editing" :saving="busy" @save="save" @cancel="dialog = false" />
    </el-dialog>
    <el-dialog
      :model-value="tools !== null"
      title="已发现工具"
      width="min(760px, 95vw)"
      @close="tools = null"
    >
      <el-table :data="tools?.discoveredTools ?? []" empty-text="暂无工具">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="说明" min-width="280" />
        <el-table-column label="只读" width="70">
          <template #default="{ row }">{{ row.readOnly ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </section>
</template>
<style scoped>
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;
    gap: 16px;
  }
  h2 {
    margin: 0 0 8px;
  }
  p {
    color: var(--el-text-color-secondary);
    margin: 0;
  }
</style>
