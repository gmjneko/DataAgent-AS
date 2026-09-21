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
  entryPoints: ['src/utils/agentQuestions.ts'],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
});
const { parsePendingQuestion, questionDetail, formatQuestionAnswers } = await import(
  `data:text/javascript;base64,${Buffer.from(outputFiles[0].text).toString('base64')}`
);
const questions = [
  { question: '用户范围', options: ['工作人员【推荐】', '全体用户'], multiple: false },
  { question: '活跃口径', options: ['活跃用户数', '登录次数', '操作次数'], multiple: true },
  { question: '时间范围', options: [], multiple: false },
];
const event = {
  type: 'question',
  content: '请确认统计口径',
  toolCall: { id: 'q1', name: 'ask_user', input: { question: '请确认统计口径', questions } },
};

test('stream and persisted events preserve grouped choices', () => {
  const parsed = parsePendingQuestion(event);
  assert.deepEqual(parsed.questions, questions);
  assert.equal(parsed.toolCallId, 'q1');
  assert.deepEqual(parsePendingQuestion(JSON.parse(JSON.stringify(event))), parsed);
  assert.match(questionDetail(event), /活跃口径（多选）\n• 活跃用户数/);
  assert.ok(!questionDetail(event).includes('"question"'));
});

test('legacy text and content fallback preserve line breaks without guessing options', () => {
  const legacy = {
    ...event,
    toolCall: { ...event.toolCall, input: { question: '范围？\nA. 工作人员\nB. 全体用户' } },
  };
  assert.deepEqual(parsePendingQuestion(legacy).questions, []);
  assert.equal(questionDetail(legacy), legacy.toolCall.input.question);
  assert.equal(
    parsePendingQuestion({ ...event, toolCall: { ...event.toolCall, input: {} } }).question,
    event.content,
  );
});

test('malformed options are ignored and arbitrary HTML stays plain text', () => {
  const malformed = {
    ...event,
    toolCall: {
      ...event.toolCall,
      input: {
        questions: [
          null,
          {},
          { question: '范围', options: ['A', 1, 'A', '', '<script>'], multiple: 'false' },
        ],
      },
    },
  };
  assert.deepEqual(parsePendingQuestion(malformed).questions, [
    { question: '范围', options: ['A', '<script>'], multiple: false },
  ]);
  assert.equal(parsePendingQuestion({ ...event, toolCall: null }), null);
});

test('submission requires each answer and keeps multi-select labels, custom answers and notes', () => {
  const answers = [
    { selected: ['工作人员【推荐】'], custom: '' },
    { selected: ['登录次数', '活跃用户数'], custom: '排除测试账号' },
    { selected: [], custom: '昨天' },
  ];
  assert.equal(formatQuestionAnswers(questions, [], '仅备注'), null);
  assert.equal(formatQuestionAnswers(questions, answers.slice(0, 2), ''), null);
  assert.equal(
    formatQuestionAnswers(questions, answers, '按地区汇总'),
    '1. 用户范围\n工作人员【推荐】\n\n2. 活跃口径\n活跃用户数；登录次数；排除测试账号\n\n3. 时间范围\n昨天\n\n补充说明：按地区汇总',
  );
  assert.equal(
    formatQuestionAnswers([questions[0]], [{ selected: questions[0].options, custom: '' }], ''),
    null,
  );
  assert.equal(
    formatQuestionAnswers([questions[0]], [{ selected: [], custom: '仅管理员' }], ''),
    '1. 用户范围\n仅管理员',
  );
});
