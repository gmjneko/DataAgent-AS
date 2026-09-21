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
  entryPoints: ['src/utils/chatTimeline.ts'],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
});
const { appendTimeline, restoreTimeline, splitTimeline } = await import(
  `data:text/javascript;base64,${Buffer.from(outputFiles[0].text).toString('base64')}`
);
const event = (type, content = '', messageId = 'm1') => ({
  type,
  content,
  messageId,
  toolCall: null,
  toolResult: null,
  errorCode: null,
});

test('stream chunks remain ordered around tools and do not mutate earlier snapshots', () => {
  const initial = [event('thinking', '检查')];
  const events = [
    event('thinking', '数据'),
    event('text', '先查询'),
    event('tool_call'),
    event('tool_result'),
    event('text', '最终'),
    event('text', '结论'),
  ].reduce(appendTimeline, initial);
  assert.equal(initial[0].content, '检查');
  assert.deepEqual(
    events.map(e => e.type),
    ['thinking', 'text', 'tool_call', 'tool_result', 'text'],
  );
  assert.equal(events[0].content, '检查数据');
  assert.equal(events[4].content, '最终结论');
  const { process, answer } = splitTimeline(events);
  assert.equal(process[1].content, '先查询');
  assert.deepEqual(
    answer.map(e => e.content),
    ['最终结论'],
  );
});

test('separate model messages and explicit summary are kept distinct', () => {
  const events = [
    event('text', '过程', 'm1'),
    event('text', '回复', 'm2'),
    event('summary', '总结', 'm3'),
  ].reduce(appendTimeline, []);
  assert.equal(events.length, 3);
  assert.equal(splitTimeline(events).answer[0].content, '总结');
});

test('unfinished tools, questions, and errors do not become final replies', () => {
  for (const type of ['tool_call', 'tool_result', 'question', 'error', 'thinking']) {
    const events = [event('text', '正在检查'), event(type)];
    assert.equal(splitTimeline(events).answer.length, 0);
    assert.deepEqual(splitTimeline(events).process, events);
  }
});

test('history restores interleaving and final report; old responses remain readable', () => {
  const saved = [
    event('text', '过程'),
    event('tool_call'),
    event('tool_result'),
    event('text', '结论'),
    event('report'),
  ];
  const restored = restoreTimeline('过程结论', [], saved);
  assert.deepEqual(restored, saved);
  assert.deepEqual(
    splitTimeline(restored).answer.map(e => e.type),
    ['text', 'report'],
  );
  assert.equal(restoreTimeline('旧回复', [event('thinking')]).at(-1).content, '旧回复');
});
