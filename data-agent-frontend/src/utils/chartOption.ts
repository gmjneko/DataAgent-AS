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
 */

import type { EChartsOption, GridComponentOption, LegendComponentOption } from 'echarts';

/** 标题下方留给图例的起点。再往上会和标题挤在一起。 */
const LEGEND_TOP = 34;
/** 单行图例高度加与绘图区的间隙。top 34 时绘图区从 64 开始。 */
const LEGEND_BAND = 30;
const TITLE_TOP = 40;
const GRID_BOTTOM = 8;

interface SeriesLike {
  type?: string;
  name?: unknown;
}

/**
 * 补全模型没写的布局。ECharts 6 的图例默认贴在画布底部，
 * 模型只要输出了 legend（哪怕只有 data）或很窄的 grid.bottom，图例就会压住绘图区和类目轴。
 * 横向图例统一放到标题下方，并保证 grid 顶部留出这一行的高度。
 */
export function mergeChartOption(option: EChartsOption, height = 340): EChartsOption {
  const series = seriesItems(option.series);
  const cartesian = series.length === 0 || series.every(isCartesianSeries);
  const legend = normalizeLegend(option.legend);
  const mergedTitle =
    option.title == null || Array.isArray(option.title)
      ? option.title
      : { left: 0, top: 4, ...option.title };
  return {
    backgroundColor: 'transparent',
    toolbox: { top: 4, right: 8, feature: { saveAsImage: {} } },
    ...option,
    title: mergedTitle,
    tooltip: option.tooltip ?? { trigger: cartesian ? 'axis' : 'item' },
    legend,
    grid: cartesian ? mergeGrid(option.grid, legendTop(legend, series), height) : option.grid,
  };
}

function seriesItems(series: EChartsOption['series']): SeriesLike[] {
  if (series == null) return [];
  const list = Array.isArray(series) ? series : [series];
  return list.flatMap(item => {
    if (item == null || typeof item !== 'object') return [];
    const record = item as { type?: unknown; name?: unknown };
    return [{ type: typeof record.type === 'string' ? record.type : undefined, name: record.name }];
  });
}

function isCartesianSeries(series: SeriesLike): boolean {
  return series.type == null || series.type === 'bar' || series.type === 'line';
}

function legendItems(legend: EChartsOption['legend']): LegendComponentOption[] {
  if (legend == null) return [];
  return Array.isArray(legend) ? legend : [legend];
}

function normalizeLegend(legend: EChartsOption['legend']): EChartsOption['legend'] {
  if (legend == null) return { type: 'scroll', left: 'center', top: LEGEND_TOP };
  if (Array.isArray(legend)) return legend.map(pinLegend);
  return pinLegend(legend);
}

function pinLegend(item: LegendComponentOption): LegendComponentOption {
  if (item.show === false) return item;
  const top = typeof item.top === 'number' && item.top >= LEGEND_TOP ? item.top : LEGEND_TOP;
  const placed: LegendComponentOption = { ...item, top };
  delete placed.bottom;
  if (item.orient === 'vertical') return placed;
  placed.type = 'scroll';
  if (placed.left == null && placed.right == null) placed.left = 'center';
  return placed;
}

function legendTop(legend: EChartsOption['legend'], series: SeriesLike[]): number {
  const items = legendItems(legend);
  const hidden = items.length > 0 && items.every(item => item.show === false);
  const named = series.some(item => typeof item.name === 'string' && item.name);
  const hasData = items.some(item => Array.isArray(item.data) && item.data.length > 0);
  if (hidden || (!named && !hasData)) return TITLE_TOP;
  const tops = items
    .filter(item => item.show !== false && typeof item.top === 'number')
    .map(item => item.top as number);
  return (tops.length ? Math.max(...tops) : LEGEND_TOP) + LEGEND_BAND;
}

function mergeGrid(
  grid: EChartsOption['grid'],
  top: number,
  height: number,
): EChartsOption['grid'] {
  const base: GridComponentOption = {
    left: 8,
    right: 16,
    top,
    bottom: GRID_BOTTOM,
    containLabel: true,
  };
  if (grid == null) return base;
  const merged = (Array.isArray(grid) ? grid : [grid]).map(item => ({
    ...base,
    ...item,
    top: atLeast(item.top, top, height),
    bottom: atLeast(item.bottom, GRID_BOTTOM, height),
    containLabel: true,
  }));
  return Array.isArray(grid) ? merged : merged[0];
}

/** 模型常用百分比间距，比图例高度更小时改成像素，避免图例压进绘图区。 */
function atLeast(value: unknown, min: number, height: number): number | string {
  if (typeof value === 'number' && Number.isFinite(value)) return Math.max(value, min);
  if (typeof value === 'string') {
    const percent = /^(\d+(?:\.\d+)?)%$/.exec(value.trim());
    if (percent) return (Number(percent[1]) / 100) * height >= min ? value : min;
    const numeric = Number(value);
    if (value.trim() !== '' && Number.isFinite(numeric)) return Math.max(numeric, min);
  }
  return min;
}
