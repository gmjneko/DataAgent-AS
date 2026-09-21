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

import { marked } from 'marked';

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
