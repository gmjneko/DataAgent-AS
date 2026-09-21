<!--
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
 -->

<script setup lang="ts">
  import { computed } from 'vue';
  import { renderMessageMarkdown } from '@/utils/messageMarkdown';
  import ChatFileDownload from './ChatFileDownload.vue';

  const props = withDefaults(defineProps<{ content: string; streaming?: boolean }>(), {
    streaming: false,
  });
  const parts = computed(() => {
    const fileIds = new Set<string>();
    const text = props.content
      .split('\n')
      .filter(line => {
        const match = line.match(/\/api\/table-exports\/([0-9a-f-]{36})\/download/i);
        if (!match) return true;
        fileIds.add(match[1]);
        return false;
      })
      .join('\n');
    const html = renderMessageMarkdown(text, props.streaming);
    return { html, fileIds: [...fileIds] };
  });
</script>

<template>
  <div class="message-content" :aria-busy="streaming">
    <div v-if="parts.html" v-html="parts.html"></div>
    <div v-if="parts.fileIds.length" class="message-content__downloads">
      <ChatFileDownload v-for="id in parts.fileIds" :key="id" :id="id" />
    </div>
  </div>
</template>

<style scoped>
  .message-content {
    word-break: break-word;

    :deep(p) {
      margin: 0 0 8px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    :deep(strong) {
      font-weight: 700;
    }

    :deep(em) {
      font-style: italic;
    }

    :deep(ul),
    :deep(ol) {
      padding-left: 20px;
      margin: 4px 0 8px;
    }

    :deep(li) {
      margin-bottom: 2px;
    }

    :deep(code) {
      background: var(--app-bg-hover);
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 15px;
      font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    }

    :deep(pre) {
      background: var(--app-bg-page);
      border: 1px solid var(--app-border);
      border-radius: 8px;
      padding: 12px 16px;
      overflow-x: auto;
      margin: 8px 0;

      code {
        background: none;
        padding: 0;
        font-size: 15px;
      }
    }

    :deep(blockquote) {
      border-left: 3px solid var(--app-accent);
      padding-left: 12px;
      margin: 8px 0;
      color: var(--app-text-secondary);
    }

    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4),
    :deep(h5),
    :deep(h6) {
      margin: 12px 0 6px;
      font-weight: 600;
      line-height: 1.4;
    }

    :deep(table) {
      border-collapse: collapse;
      width: 100%;
      margin: 8px 0;

      th,
      td {
        border: 1px solid var(--app-border);
        padding: 6px 12px;
        text-align: left;
      }

      th {
        background: var(--app-bg-page);
        font-weight: 600;
      }
    }

    :deep(hr) {
      border: none;
      border-top: 1px solid var(--app-border);
      margin: 12px 0;
    }
  }

  .message-content {
    min-width: 0;
  }
  .message-content :deep(a) {
    color: var(--app-link);
    text-decoration: underline;
  }
  .message-content :deep(img) {
    max-width: 100%;
  }
  .message-content :deep(table) {
    display: block;
    overflow-x: auto;
  }
  .message-content__downloads {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
  }
</style>
