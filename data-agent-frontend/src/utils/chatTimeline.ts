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

import type { ChatStreamEvent } from '@/api/agent';

export type TimelineEvent = Pick<
  ChatStreamEvent,
  'type' | 'content' | 'toolCall' | 'toolResult' | 'errorCode'
> & { messageId?: string | null };

/** Merge only adjacent chunks from the same model message; never cross a tool event. */
export function appendTimeline(events: TimelineEvent[], event: TimelineEvent): TimelineEvent[] {
  if (['text', 'summary', 'thinking'].includes(event.type) && !event.content) return events;
  const last = events[events.length - 1];
  if (
    last &&
    ['text', 'summary', 'thinking'].includes(event.type) &&
    last.type === event.type &&
    last.messageId === event.messageId
  ) {
    return [...events.slice(0, -1), { ...last, content: (last.content ?? '') + event.content }];
  }
  return [...events, { ...event }];
}

export function restoreTimeline(
  content: string,
  traces: TimelineEvent[],
  timeline?: TimelineEvent[],
): TimelineEvent[] {
  if (timeline?.length) return timeline.reduce(appendTimeline, []);
  // Older servers cannot provide interleaving. Keep their content readable without guessing order.
  const parts = content.split(/\n\nSummary[:：]\n/);
  return [
    ...traces,
    ...parts.filter(Boolean).map(
      (text, index): TimelineEvent => ({
        type: index === 0 ? 'text' : 'summary',
        content: text,
        toolCall: null,
        toolResult: null,
        errorCode: null,
      }),
    ),
  ];
}

/** The last model text is the answer. Everything preceding it belongs to the work log. */
export function splitTimeline(events: TimelineEvent[]) {
  let answerIndex = -1;
  for (let index = events.length - 1; index >= 0; index--) {
    if (events[index].type === 'text' || events[index].type === 'summary') {
      answerIndex = index;
      break;
    }
    // A tool/question/error after the last text means this turn has no final answer yet.
    if (events[index].type !== 'report') break;
  }
  return {
    process: answerIndex < 0 ? events : events.slice(0, answerIndex),
    answer: answerIndex < 0 ? [] : events.slice(answerIndex),
  };
}
