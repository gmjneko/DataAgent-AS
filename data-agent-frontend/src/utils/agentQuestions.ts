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
 * limitations under the License.
 */

import type { TimelineEvent } from './chatTimeline';
import { INTERACTIVE_TOOLS, isInteractiveTool } from './interactiveTools';

export interface AgentQuestion {
  question: string;
  options: string[];
  multiple: boolean;
}

export interface PendingQuestion {
  toolCallId: string;
  toolName: string;
  question: string;
  questions: AgentQuestion[];
}

export interface QuestionAnswer {
  selected: string[];
  custom: string;
}

export function parsePendingQuestion(event: TimelineEvent): PendingQuestion | null {
  const tool = event.toolCall;
  if (!tool || !isInteractiveTool(tool.name)) return null;
  const input = tool.input;
  const prompt = input[INTERACTIVE_TOOLS[tool.name].questionField];
  const questions: AgentQuestion[] = [];
  if (Array.isArray(input.questions)) {
    for (const item of input.questions) {
      if (!item || typeof item !== 'object' || typeof item.question !== 'string') continue;
      if (!item.question.trim()) continue;
      const options = Array.isArray(item.options)
        ? item.options.filter(
            (option: unknown): option is string => typeof option === 'string' && !!option.trim(),
          )
        : [];
      questions.push({
        question: item.question,
        options: [...new Set<string>(options)],
        multiple: item.multiple === true,
      });
    }
  }
  return {
    toolCallId: tool.id,
    toolName: tool.name,
    question: typeof prompt === 'string' ? prompt : (event.content ?? ''),
    questions,
  };
}

/** Use readable answers as the external tool result and the user's chat message. */
export function formatQuestionAnswers(
  questions: AgentQuestion[],
  answers: QuestionAnswer[],
  note: string,
): string | null {
  const lines: string[] = [];
  for (const [index, question] of questions.entries()) {
    const answer = answers[index];
    const selected = question.options.filter(option => answer?.selected.includes(option));
    const custom = answer?.custom.trim() ?? '';
    if (!selected.length && !custom) return null;
    if (!question.multiple && selected.length > 1) return null;
    lines.push(
      `${index + 1}. ${question.question}\n${[...selected, custom].filter(Boolean).join('；')}`,
    );
  }
  if (note.trim()) lines.push(`补充说明：${note.trim()}`);
  return lines.join('\n\n');
}

export function questionDetail(event: TimelineEvent): string {
  const pending = parsePendingQuestion(event);
  if (!pending) return event.content ?? '';
  return [
    pending.question,
    ...pending.questions.map((question, index) =>
      [
        `${index + 1}. ${question.question}${question.options.length ? (question.multiple ? '（多选）' : '（单选）') : ''}`,
        ...question.options.map(option => `• ${option}`),
      ].join('\n'),
    ),
  ]
    .filter(Boolean)
    .join('\n\n');
}
