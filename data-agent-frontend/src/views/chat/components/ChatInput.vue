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
  import { ref, computed, nextTick, watch } from 'vue';

  import type { PendingQuestion } from '@/utils/agentQuestions';
  import AgentQuestionForm from './AgentQuestionForm.vue';

  const props = defineProps<{
    isStreaming: boolean;
    pendingQuestion: PendingQuestion | null;
    // Previous user messages from the chat, used as input history for Up/Down navigation.
    // Ordered oldest → newest.
    userMessages: string[];
  }>();

  const emit = defineEmits<{
    send: [text: string];
    stop: [];
  }>();

  const inputText = ref('');
  const textareaRef = ref<{
    value: string;
    style: { height: string };
    scrollHeight: number;
    selectionStart: number;
    selectionEnd: number;
  }>();

  // -1 = not browsing; 0 = newest user message, increasing = older
  const historyIndex = ref(-1);

  // Full original message at the current history position (used by Tab confirmation)
  const historyFull = computed(() => {
    if (historyIndex.value < 0) return '';
    const msgs = props.userMessages;
    const idx = msgs.length - 1 - historyIndex.value;
    return idx >= 0 && idx < msgs.length ? msgs[idx] : '';
  });

  // Truncated version shown as placeholder: max 20 chars or cut at first newline,
  // whichever comes first. Appends "…" whenever anything was dropped.
  const historyPreview = computed(() => {
    const raw = historyFull.value;
    if (!raw) return null;
    const nl = raw.indexOf('\n');
    const cut = nl === -1 ? raw.length : nl;
    const limit = Math.min(50, cut);
    const truncated = raw.slice(0, limit);
    return truncated.length < raw.length ? truncated + '…' : truncated;
  });

  watch(inputText, () => {
    nextTick(autoResize);
  });

  function handleSend() {
    const text = inputText.value.trim();
    if (!text || props.isStreaming) return;
    historyIndex.value = -1;
    emit('send', text);
    inputText.value = '';
  }

  function handleKeydown(e: {
    key: string;
    shiftKey: boolean;
    ctrlKey: boolean;
    altKey: boolean;
    metaKey: boolean;
    preventDefault: () => void;
  }) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (props.isStreaming) {
        emit('stop');
      } else {
        handleSend();
      }
      return;
    }

    const hasModifier = e.shiftKey || e.ctrlKey || e.altKey || e.metaKey;
    if (hasModifier) return;

    const el = textareaRef.value;
    if (!el) return;

    // History preview via Up/Down:
    // - When input is empty → always works
    // - When input has text → only when cursor is at the boundary
    //   (Up at start of text, Down at end of text)
    // The history item shows as placeholder (not real value); Tab to confirm.

    if (e.key === 'ArrowUp') {
      if (props.userMessages.length === 0) return;
      const len = el.value.length;
      const cursorAtStart = el.selectionStart === 0 && el.selectionEnd === 0;
      if (len > 0 && !cursorAtStart) return;
      e.preventDefault();
      const next = historyIndex.value === -1 ? 0 : historyIndex.value + 1;
      historyIndex.value = Math.min(next, props.userMessages.length - 1);
      return;
    }

    if (e.key === 'ArrowDown') {
      if (historyIndex.value === -1) return;
      const len = el.value.length;
      const cursorAtEnd = el.selectionStart === len && el.selectionEnd === len;
      if (len > 0 && !cursorAtEnd) return;
      e.preventDefault();
      historyIndex.value = Math.max(historyIndex.value - 1, -1);
      return;
    }

    // Tab: confirm the current history preview, filling the full original text into the input
    if (e.key === 'Tab' && historyPreview.value !== null) {
      e.preventDefault();
      inputText.value = historyFull.value;
      historyIndex.value = -1;
      nextTick(autoResize);
      return;
    }

    // Escape: dismiss the history preview
    if (e.key === 'Escape' && historyPreview.value !== null) {
      e.preventDefault();
      historyIndex.value = -1;
    }
  }

  function autoResize() {
    const el = textareaRef.value;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px';
  }
</script>

<template>
  <div class="chat-input">
    <AgentQuestionForm
      v-if="props.pendingQuestion?.questions.length && !isStreaming"
      :key="props.pendingQuestion.toolCallId"
      :pending="props.pendingQuestion"
      @send="emit('send', $event)"
    />
    <div v-else-if="props.pendingQuestion && !isStreaming" class="chat-input__question-banner">
      <span class="chat-input__question-label">Agent 提问：</span>
      <span class="chat-input__question-text">{{ props.pendingQuestion.question }}</span>
    </div>
    <div
      v-if="!props.pendingQuestion?.questions.length || isStreaming"
      class="chat-input__composer"
    >
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="chat-input__textarea"
        :placeholder="
          historyPreview !== null
            ? historyPreview + '  (Tab 确认)'
            : props.pendingQuestion && !isStreaming
              ? '输入你的回答，Enter 发送...'
              : isStreaming
                ? '正在回复中...'
                : '向 Data Agent 提问...'
        "
        :disabled="isStreaming"
        rows="1"
        @keydown="handleKeydown"
      />
      <div class="chat-input__toolbar">
        <span class="chat-input__hint">Enter 发送 · Shift + Enter 换行</span>
        <button
          v-if="!isStreaming"
          type="button"
          class="chat-input__send-btn"
          :disabled="!inputText.trim()"
          title="发送消息"
          @click="handleSend"
        >
          <el-icon :size="17"><Top /></el-icon>
        </button>
        <button
          v-else
          type="button"
          class="chat-input__send-btn chat-input__send-btn--stop"
          title="停止生成"
          @click="emit('stop')"
        >
          <el-icon :size="15"><VideoPause /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
  .chat-input {
    padding: 14px 32px 22px;
    background: var(--app-bg-card);
    border-top: 1px solid var(--app-border-light);
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .chat-input__question-banner {
    width: 100%;
    margin-bottom: 10px;
    padding: 8px 14px;
    background: var(--app-bg-page);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    font-size: 15px;
    color: var(--app-text-secondary);
  }

  .chat-input__question-label {
    font-weight: 600;
  }

  .chat-input__question-text {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    color: var(--app-text-primary);
  }

  .chat-input__textarea {
    width: 100%;
    resize: none;
    border: none;
    border-radius: 8px;
    padding: 5px 2px;
    font-size: 15px;
    line-height: 1.5;
    font-family: inherit;
    outline: none;
    background: transparent;
    color: var(--app-text-primary);
    transition:
      background-color 0.2s,
      color 0.2s;
    max-height: 150px;
  }

  .chat-input__textarea:focus {
    outline: none;
  }

  .chat-input__textarea:disabled {
    color: var(--app-text-muted);
  }

  .chat-input__composer {
    width: min(900px, 100%);
    margin: 0 auto;
    padding: 13px 15px 10px;
    border: 1px solid var(--app-border);
    border-radius: 13px;
    background: var(--app-bg-input);
    box-shadow: 0 2px 8px rgba(24, 24, 27, 0.04);
    transition:
      border-color 0.15s,
      box-shadow 0.15s;
  }

  .chat-input__composer:focus-within {
    border-color: color-mix(in srgb, var(--app-accent) 45%, var(--app-border));
    box-shadow: 0 3px 14px rgba(24, 24, 27, 0.08);
  }

  .chat-input__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 7px;
  }

  .chat-input__hint {
    color: var(--app-text-muted);
    font-size: 15px;
  }

  .chat-input__send-btn {
    display: grid;
    width: 30px;
    height: 30px;
    place-items: center;
    padding: 0;
    border: none;
    border-radius: 8px;
    color: var(--app-accent-text);
    background: var(--app-accent);
    cursor: pointer;
    transition:
      opacity 0.15s,
      transform 0.15s;
  }

  .chat-input__send-btn:disabled {
    cursor: default;
    opacity: 0.3;
  }

  .chat-input__send-btn:not(:disabled):hover {
    transform: translateY(-1px);
  }

  .chat-input__send-btn--stop {
    color: #fff;
    background: #dc2626;
  }
</style>
