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
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
  import * as echarts from 'echarts';
  import { useThemeStore } from '@/stores/theme';
  import { mergeChartOption } from '@/utils/chartOption';

  const props = withDefaults(defineProps<{ optionJson: string; height?: number }>(), {
    height: 340,
  });

  const theme = useThemeStore();
  const container = ref<globalThis.HTMLDivElement | null>(null);
  const errorMessage = ref<string | null>(null);

  let chart: echarts.ECharts | null = null;
  let observer: globalThis.ResizeObserver | null = null;

  const boxStyle = computed(() => ({ height: `${props.height}px` }));

  /** 严格 JSON.parse：LLM 输出不可信，应用内渲染不允许任何脚本执行面。 */
  function renderChart() {
    if (!container.value) return;
    disposeChart();

    let option: echarts.EChartsOption | null = null;
    try {
      const parsed: unknown = JSON.parse(props.optionJson);
      if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
        option = parsed as echarts.EChartsOption;
      } else {
        errorMessage.value = 'ECharts Option 必须是 JSON 对象';
      }
    } catch (e) {
      errorMessage.value = `图表配置解析失败：${(e as Error).message}`;
    }
    if (!option) return;

    try {
      chart = echarts.init(container.value, theme.mode === 'dark' ? 'dark' : undefined);
      chart.setOption(mergeChartOption(option, props.height));
      observer = new globalThis.ResizeObserver(() => chart?.resize());
      observer.observe(container.value);
    } catch (e) {
      errorMessage.value = `图表渲染失败：${(e as Error).message}`;
      disposeChart();
    }
  }

  function disposeChart() {
    observer?.disconnect();
    observer = null;
    chart?.dispose();
    chart = null;
  }

  onMounted(renderChart);
  onBeforeUnmount(disposeChart);

  // flush: 'post' 确保出错时被 v-if 移除的容器先回到 DOM 再重建图表。
  watch(
    () => props.optionJson,
    () => {
      errorMessage.value = null;
      renderChart();
    },
    { flush: 'post' },
  );

  watch(
    () => theme.mode,
    () => {
      if (!errorMessage.value) renderChart();
    },
    { flush: 'post' },
  );
</script>

<template>
  <div class="echart-block">
    <div v-if="errorMessage" class="echart-block__error">
      <div class="echart-block__error-tip">{{ errorMessage }}</div>
      <pre class="echart-block__raw">{{ optionJson }}</pre>
    </div>
    <div v-else ref="container" class="echart-block__canvas" :style="boxStyle"></div>
  </div>
</template>

<style scoped>
  .echart-block {
    width: 100%;
  }

  .echart-block__canvas {
    width: 100%;
  }

  .echart-block__error-tip {
    margin-bottom: 8px;
    color: var(--el-color-danger);
    font-size: 13px;
  }

  .echart-block__raw {
    margin: 0;
    padding: 12px;
    border: 1px dashed var(--el-color-danger);
    border-radius: 8px;
    background: var(--app-bg-page);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    max-height: 240px;
    overflow: auto;
    font:
      13px/1.5 ui-monospace,
      monospace;
  }
</style>
