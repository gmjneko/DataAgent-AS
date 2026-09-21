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
  const values = defineModel<Record<string, string>>({ required: true });
  function rename(old: string, name: string) {
    if (name === old || Object.prototype.hasOwnProperty.call(values.value, name)) return;
    values.value = Object.fromEntries(
      Object.entries(values.value).map(([k, v]) => [k === old ? name : k, v]),
    );
  }
  function add() {
    let key = 'KEY';
    while (Object.prototype.hasOwnProperty.call(values.value, key)) key += '_';
    values.value = { ...values.value, [key]: '' };
  }
  function remove(key: string) {
    const next = { ...values.value };
    delete next[key];
    values.value = next;
  }
</script>
<template>
  <div class="fields">
    <div v-for="(value, key) in values" :key="key" class="field">
      <el-input
        :model-value="key"
        aria-label="配置名称"
        placeholder="名称"
        @change="rename(String(key), $event)"
      />
      <el-input
        :model-value="value"
        type="password"
        show-password
        aria-label="配置值"
        placeholder="值"
        @update:model-value="values = { ...values, [key]: $event }"
      />
      <el-button aria-label="删除配置" @click="remove(String(key))">删除</el-button>
    </div>
    <el-button size="small" @click="add">添加配置</el-button>
  </div>
</template>
<style scoped>
  .fields {
    width: 100%;
  }
  .field {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }
</style>
