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
  import { computed } from 'vue';
  import type { TimelineEvent } from '@/utils/chatTimeline';
  import { INTERACTIVE_TOOLS, isInteractiveTool } from '@/utils/interactiveTools';

  const props = defineProps<{ step: TimelineEvent; running: boolean }>();
  const emit = defineEmits<{ previewReport: [content: string] }>();
  const title = computed(() => {
    const { step, running } = props;
    if (step.type === 'error') return `执行出错${step.errorCode ? ` · ${step.errorCode}` : ''}`;
    if (step.type === 'question') return '等待你的回答';
    if (step.type === 'report') return '分析报告已生成';
    if (step.toolCall) {
      if (isInteractiveTool(step.toolCall.name)) return INTERACTIVE_TOOLS[step.toolCall.name].label;
      return `${running ? '正在运行' : '已调用'} ${step.toolCall.name}`;
    }
    return step.toolResult ? `已返回 ${step.toolResult.name}` : step.type;
  });
  const detail = computed(() => {
    const { step } = props;
    if (step.toolCall) return JSON.stringify(step.toolCall.input, null, 2);
    return step.toolResult?.output ?? step.content ?? '';
  });
</script>

<template>
  <details
    class="trace-step"
    :class="{ 'trace-step--error': step.type === 'error' }"
    :open="step.type === 'error' || step.type === 'question'"
  >
    <summary>
      <el-icon v-if="running" class="trace-step__spinner"><Loading /></el-icon>
      <el-icon v-else-if="step.type === 'error'"><Warning /></el-icon>
      <el-icon v-else-if="step.type === 'tool_result'"><CircleCheck /></el-icon>
      <el-icon v-else><Operation /></el-icon>
      <span>{{ title }}</span>
      <el-icon class="trace-step__chevron"><ArrowRight /></el-icon>
    </summary>
    <div class="trace-step__detail">
      <button
        v-if="step.type === 'report' && step.toolResult"
        class="trace-step__report"
        @click="emit('previewReport', step.toolResult.output)"
      >
        预览报告
      </button>
      <pre v-else>{{ detail }}</pre>
    </div>
  </details>
</template>

<style scoped>
  .trace-step {
    min-width: 0;
    color: var(--app-text-secondary);
    font-size: 13px;
  }
  .trace-step summary {
    display: flex;
    align-items: center;
    gap: 9px;
    cursor: pointer;
    list-style: none;
  }
  .trace-step summary::-webkit-details-marker {
    display: none;
  }
  .trace-step summary span {
    overflow-wrap: anywhere;
  }
  .trace-step__chevron {
    color: var(--app-text-muted);
    font-size: 11px;
    transition: transform 0.15s;
  }
  .trace-step[open] .trace-step__chevron {
    transform: rotate(90deg);
  }
  .trace-step__detail {
    margin: 10px 0 0 23px;
  }
  .trace-step pre {
    padding: 12px;
    border: 1px solid var(--app-border);
    border-radius: 8px;
    background: var(--app-bg-page);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    max-height: 320px;
    overflow: auto;
    font:
      12px/1.6 ui-monospace,
      monospace;
  }
  .trace-step--error {
    color: var(--el-color-danger);
  }
  .trace-step__report {
    border: 0;
    background: transparent;
    color: var(--app-link);
  }
  .trace-step__spinner {
    animation: spin 1s linear infinite;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
