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

import { marked, type Token, type Tokens } from 'marked';

export type MessageSegment =
  | { kind: 'text'; html: string }
  | { kind: 'echart'; optionJson: string };

/**
 * Split message markdown into segments: prose keeps flowing through markdown rendering,
 * while closed ```echarts fenced blocks become chart segments rendered by EChartBlock.
 * An echarts fence that is still open (streaming) stays in the trailing text segment,
 * so it displays as a growing code block and flips into a chart once the fence closes.
 */
export function parseMessageSegments(content: string, streaming = false): MessageSegment[] {
  const segments: MessageSegment[] = [];
  let pending: Token[] = [];

  const flush = () => {
    if (pending.length === 0) return;
    const raw = pending.map(token => token.raw).join('');
    pending = [];
    const html = renderMessageMarkdown(raw, streaming);
    if (html) segments.push({ kind: 'text', html });
  };

  for (const token of marked.lexer(content)) {
    if (isClosedEchartsBlock(token)) {
      flush();
      segments.push({ kind: 'echart', optionJson: (token as Tokens.Code).text });
    } else {
      pending.push(token);
    }
  }
  flush();
  return segments;
}

function isClosedEchartsBlock(token: Token): boolean {
  if (token.type !== 'code') return false;
  const code = token as Tokens.Code;
  return code.lang === 'echarts' && code.raw.trimEnd().endsWith('```');
}

/** Complete only the open inline suffix for display; never change the saved response. */
export function renderMessageMarkdown(content: string, streaming = false): string {
  let text = content.replace(/</g, '&lt;');
  if (streaming) {
    // Fenced code already renders while open. Inspect only a trailing prose block so code
    // and complete Markdown tokens are never rewritten as partial emphasis.
    const blocks = marked.lexer(text).filter(token => token.type !== 'space');
    const block = blocks[blocks.length - 1];
    if (block?.type === 'paragraph' || block?.type === 'heading') {
      const tail = block.tokens?.[block.tokens.length - 1];
      if (tail?.type === 'text') {
        const open = tail.raw.match(/(?<![\\*_`])(\*\*|__|`+)(?=\S)([^\n]*)$/);
        if (open && !open[2].includes(open[1])) text += open[1];
      }
    }
  }
  return marked.parse(text, { gfm: true, breaks: true, async: false });
}
