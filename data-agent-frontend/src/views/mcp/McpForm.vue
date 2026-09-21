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
  import { ref, watch } from 'vue';
  import { ElMessage } from 'element-plus';
  import type { McpServer } from '@/api/mcp';
  import { emptyMcp, splitLines, validateMcp } from '@/utils/mcpForm';
  import SecretFields from './SecretFields.vue';
  const props = defineProps<{ value: McpServer | null; saving: boolean }>();
  const emit = defineEmits<{ save: [value: McpServer]; cancel: [] }>();
  const form = ref(emptyMcp());
  const args = ref('');
  const allow = ref('');
  const deny = ref('');
  watch(
    () => props.value,
    value => {
      form.value = { ...emptyMcp(), ...JSON.parse(JSON.stringify(value ?? {})) };
      form.value.env ??= {};
      form.value.headers ??= {};
      form.value.queryParams ??= {};
      args.value = (form.value.args ?? []).join('\n');
      allow.value = (form.value.enableTools ?? []).join('\n');
      deny.value = (form.value.disableTools ?? []).join('\n');
    },
    { immediate: true },
  );
  function submit() {
    const value = {
      ...form.value,
      args: args.value.split('\n').filter(v => v !== ''),
      enableTools: splitLines(allow.value),
      disableTools: splitLines(deny.value),
    };
    const error = validateMcp(value);
    if (error) {
      ElMessage.warning(error);
      return;
    }
    emit('save', value);
  }
</script>
<template>
  <el-form label-width="135px" :disabled="saving" @submit.prevent="submit">
    <el-form-item label="服务名称" required>
      <el-input v-model="form.name" maxlength="255" />
    </el-form-item>
    <el-form-item label="传输方式">
      <el-select v-model="form.transportType">
        <el-option label="Streamable HTTP" value="HTTP" />
        <el-option label="SSE" value="SSE" />
        <el-option label="STDIO" value="STDIO" />
      </el-select>
    </el-form-item>
    <el-form-item label="客户端">
      <el-radio-group v-model="form.clientType">
        <el-radio value="ASYNC">异步</el-radio>
        <el-radio value="SYNC">同步</el-radio>
      </el-radio-group>
    </el-form-item>
    <template v-if="form.transportType === 'STDIO'">
      <el-form-item label="启动命令" required>
        <el-input v-model="form.command" placeholder="例如 npx 或可执行文件路径" />
      </el-form-item>
      <el-form-item label="启动参数">
        <el-input
          v-model="args"
          type="textarea"
          :rows="3"
          placeholder="每行一个参数，含空格的参数无需引号"
        />
      </el-form-item>
      <el-form-item label="环境变量"><SecretFields v-model="form.env" /></el-form-item>
    </template>
    <template v-else>
      <el-form-item label="服务地址" required>
        <el-input v-model="form.url" placeholder="https://example.com/mcp" />
      </el-form-item>
      <el-form-item label="请求头"><SecretFields v-model="form.headers" /></el-form-item>
      <el-form-item label="查询参数"><SecretFields v-model="form.queryParams" /></el-form-item>
      <el-form-item label="HTTP 版本">
        <el-select v-model="form.httpVersion">
          <el-option label="HTTP 1.1" value="HTTP_1_1" />
          <el-option label="HTTP 2" value="HTTP_2" />
        </el-select>
      </el-form-item>
      <el-form-item label="重定向">
        <el-radio-group v-model="form.redirectPolicy">
          <el-radio value="NEVER">不跟随</el-radio>
          <el-radio value="FOLLOW">跟随</el-radio>
        </el-radio-group>
      </el-form-item>
    </template>
    <el-form-item label="请求超时 (ms)">
      <el-input-number v-model="form.timeout" :min="1" :max="600000" />
    </el-form-item>
    <el-form-item label="初始化超时 (ms)">
      <el-input-number v-model="form.initializationTimeout" :min="1" :max="600000" />
    </el-form-item>
    <el-form-item label="连接超时 (ms)">
      <el-input-number v-model="form.connectTimeout" :min="1" :max="600000" />
    </el-form-item>
    <el-form-item label="允许工具">
      <el-input v-model="allow" type="textarea" placeholder="每行一个名称；留空允许全部" />
    </el-form-item>
    <el-form-item label="禁用工具">
      <el-input v-model="deny" type="textarea" placeholder="每行一个名称；禁用优先" />
    </el-form-item>
    <el-form-item label="说明">
      <el-input v-model="form.description" type="textarea" />
    </el-form-item>
    <p class="hint">
      已保存的敏感值显示为 ********，保持原值即可保留。启用的工具对所有登录用户可用。
    </p>
    <el-form-item>
      <el-button type="primary" native-type="submit" :loading="saving">保存</el-button>
      <el-button @click="emit('cancel')">取消</el-button>
    </el-form-item>
  </el-form>
</template>
<style scoped>
  .hint {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
</style>
