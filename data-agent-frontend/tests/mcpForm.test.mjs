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
  entryPoints: ['src/utils/mcpForm.ts'],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
});
const { emptyMcp, validateMcp, splitLines } = await import(
  `data:text/javascript;base64,${Buffer.from(outputFiles[0].text).toString('base64')}`
);
test('validates HTTP and STDIO configuration and timeouts', () => {
  assert.ok(validateMcp(emptyMcp()));
  const form = { ...emptyMcp(), name: 'service', url: 'https://example.com/mcp' };
  assert.equal(validateMcp(form), undefined);
  assert.ok(validateMcp({ ...form, url: 'file:///tmp/socket' }));
  assert.ok(validateMcp({ ...form, url: 'https://name:secret@example.com/mcp' }));
  assert.ok(validateMcp({ ...form, timeout: 0 }));
  assert.ok(validateMcp({ ...form, initializationTimeout: 600001 }));
  assert.ok(validateMcp({ ...form, transportType: 'STDIO' }));
  assert.equal(validateMcp({ ...form, transportType: 'STDIO', command: 'node' }), undefined);
});
test('tool filters preserve exact names and discard blank lines', () => {
  assert.deepEqual(splitLines(' search \n\nquery_data\n'), ['search', 'query_data']);
  assert.deepEqual(splitLines(''), []);
});
