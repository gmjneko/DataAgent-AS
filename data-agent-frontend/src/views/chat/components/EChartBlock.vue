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

  const props = withDefaults(defineProps<{ optionJson: string; height?: number }>(), {
    height: 340,
  });

  const theme = useThemeStore();
  const container = ref<globalThis.HTMLDivElement | null>(null);
  const errorMessage = ref<string | null>(null);

  let chart: echarts.ECharts | null = null;
  let observer: globalThis.ResizeObserver | null = null;

  const boxStyle = computed(() => ({ height: `${props.height}px` }));

  /**
   * 前端补全默认样式：agent 只输出 title/xAxis/yAxis/series 等必要字段。
   * 显式固定各组件位置：ECharts v6 的默认布局是标题居中 + 图例在底部，
   * 图例会压在 x 轴标签上，因此标题/图例/grid 间距必须全部显式声明。
   */
  function mergeDefaults(option: echarts.EChartsOption): echarts.EChartsOption {
    const series = Array.isArray(option.series) ? option.series : [];
    const cartesian =
      series.length === 0 || series.every(item => item.type === 'bar' || item.type === 'line');
    const named = series.some(item => typeof item.name === 'string' && item.name);
    // title 允许写成数组（多标题），此时原样保留不做 merge
    const mergedTitle =
      option.title == null
        ? undefined
        : Array.isArray(option.title)
          ? option.title
          : { left: 0, top: 4, ...option.title };
    return {
      backgroundColor: 'transparent',
      toolbox: { top: 4, right: 8, feature: { saveAsImage: {} } },
      ...option,
      title: mergedTitle,
      tooltip: option.tooltip ?? { trigger: cartesian ? 'axis' : 'item' },
      legend: option.legend ?? { top: 34, type: 'scroll' },
      grid:
        option.grid ??
        (cartesian
          ? { left: 8, right: 16, top: named ? 64 : 40, bottom: 8, containLabel: true }
          : undefined),
    };
  }

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
      chart.setOption(mergeDefaults(option));
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
