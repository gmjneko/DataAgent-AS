/*
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
 */
import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { test } from 'node:test';
import { build } from 'esbuild';

const { outputFiles } = await build({
  entryPoints: ['src/utils/messageMarkdown.ts'],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
});
const { renderMessageMarkdown } = await import(
  `data:text/javascript;base64,${Buffer.from(outputFiles[0].text).toString('base64')}`
);

test('streamed prose renders headings and incomplete emphasis immediately', () => {
  assert.match(
    renderMessageMarkdown('## 分析结果\n\n**活跃人数', true),
    /<h2>分析结果<\/h2>[\s\S]*<strong>活跃人数<\/strong>/,
  );
  assert.match(renderMessageMarkdown('字段 `user_id', true), /<code>user_id<\/code>/);
  assert.match(renderMessageMarkdown('**人数**增加', true), /<strong>人数<\/strong>增加/);
});

test('completed responses use original Markdown and do not invent closing delimiters', () => {
  assert.equal(renderMessageMarkdown('**未完成', false), '<p>**未完成</p>\n');
  const markdown = '## 结果\n\n**人数**\n\n| 地区 | 人数 |\n| --- | --- |\n| 华东 | 12 |';
  assert.equal(renderMessageMarkdown(markdown, true), renderMessageMarkdown(markdown, false));
  assert.match(renderMessageMarkdown(markdown, true), /<table>/);
});

test('streamed code and escaped markers remain literal, and HTML stays escaped', () => {
  assert.match(
    renderMessageMarkdown('```text\n**literal', true),
    /<code class="language-text">\*\*literal/,
  );
  assert.ok(!renderMessageMarkdown('\\*\\*literal', true).includes('<strong>'));
  assert.ok(!renderMessageMarkdown('<img src=x onerror=alert(1)>', true).includes('<img'));
});
