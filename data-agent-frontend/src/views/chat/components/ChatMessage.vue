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
  import { computed, ref, watch, onUnmounted } from 'vue';
  import { ElCollapseTransition } from 'element-plus';
  import type { ChatMessage as ChatMessageType } from '@/composables/useAgentChat';
  import { splitTimeline } from '@/utils/chatTimeline';
  import MessageContent from './MessageContent.vue';
  import ChatTimeline from './ChatTimeline.vue';

  const props = defineProps<{ message: ChatMessageType }>();
  const emit = defineEmits<{ previewReport: [content: string] }>();
  const parts = computed(() => splitTimeline(props.message.timeline));
  const hasAnswer = computed(
    () =>
      props.message.outcome !== 'stopped' &&
      props.message.outcome !== 'error' &&
      parts.value.answer.length > 0,
  );
  const expanded = ref(!hasAnswer.value);
  const processEvents = computed(() =>
    hasAnswer.value ? parts.value.process : props.message.timeline,
  );
  const workLabel = computed(() => {
    if (props.message.isStreaming) return hasAnswer.value ? '正在回答' : '正在处理';
    if (props.message.outcome === 'stopped') return '已停止';
    if (props.message.outcome === 'error' || props.message.timeline.some(e => e.type === 'error'))
      return '执行中出现错误';
    if (!hasAnswer.value) return '处理记录';
    const calls = parts.value.process.filter(e => e.type === 'tool_call').length;
    return calls ? `已完成 · ${calls} 次工具调用` : '已完成';
  });
  watch(hasAnswer, done => {
    expanded.value = !done;
  });

  const copied = ref(false);
  let resetTimer: ReturnType<typeof setTimeout> | null = null;
  async function copyMessage() {
    try {
      await navigator.clipboard.writeText(props.message.content);
      copied.value = true;
      if (resetTimer) clearTimeout(resetTimer);
      resetTimer = setTimeout(() => {
        copied.value = false;
      }, 2000);
    } catch {
      /* Clipboard can be unavailable in insecure contexts. */
    }
  }
  onUnmounted(() => {
    if (resetTimer) clearTimeout(resetTimer);
  });
</script>

<template>
  <div class="chat-message" :class="`chat-message--${message.role}`">
    <div v-if="message.role === 'user'" class="chat-message__actions">
      <button type="button" class="chat-message__copy-btn" title="复制" @click.stop="copyMessage">
        <el-icon :size="16">
          <Check v-if="copied" />
          <CopyDocument v-else />
        </el-icon>
      </button>
    </div>
    <div class="chat-message__bubble">
      <MessageContent v-if="message.role === 'user'" :content="message.content" />
      <template v-else>
        <div v-if="processEvents.length || !hasAnswer" class="chat-message__work">
          <button
            type="button"
            class="chat-message__work-toggle"
            :aria-expanded="expanded"
            @click="expanded = !expanded"
          >
            <el-icon :class="{ 'is-expanded': expanded }"><ArrowRight /></el-icon>
            {{ workLabel }}
            <span v-if="message.isStreaming" class="chat-message__cursor">…</span>
          </button>
          <ElCollapseTransition>
            <div v-show="expanded">
              <ChatTimeline
                :events="processEvents"
                :streaming="message.isStreaming && !hasAnswer"
                @preview-report="emit('previewReport', $event)"
              />
            </div>
          </ElCollapseTransition>
        </div>
        <ChatTimeline
          v-if="hasAnswer"
          :events="parts.answer"
          :streaming="message.isStreaming"
          @preview-report="emit('previewReport', $event)"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
  .chat-message {
    display: flex;
    width: min(900px, 100%);
    margin: 0 auto 28px;
  }

  .chat-message--user {
    justify-content: flex-end;
    align-items: center;
  }

  .chat-message--agent {
    justify-content: flex-start;
  }

  .chat-message__bubble {
    max-width: min(78%, 650px);
    padding: 12px 16px;
    border-radius: 14px;
    font-size: 15px;
    line-height: 1.75;
  }

  .chat-message--user .chat-message__bubble {
    background: var(--app-accent);
    color: var(--app-accent-text);
    border-bottom-right-radius: 5px;
    box-shadow: 0 2px 8px rgba(24, 24, 27, 0.1);
  }

  .chat-message--agent .chat-message__bubble {
    background: transparent;
    color: var(--app-text-primary);
    width: 100%;
    max-width: 100%;
    padding: 0;
  }

  .chat-message__thinking {
    color: var(--app-text-muted);
    font-style: italic;
  }

  .chat-message__cursor {
    display: inline-block;
    animation: blink 1s step-end infinite;
    color: var(--app-accent);
    font-weight: bold;
  }

  .chat-message--user .chat-message__cursor {
    color: var(--app-accent-text);
    opacity: 0.7;
  }

  .chat-message__actions {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    padding-right: 8px;
  }

  .chat-message__copy-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    padding: 0;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: var(--app-text-secondary);
    opacity: 0;
    cursor: pointer;
    transition:
      opacity 0.15s,
      background-color 0.15s,
      color 0.15s;
  }

  .chat-message--user:hover .chat-message__copy-btn {
    opacity: 0.6;
  }

  .chat-message__copy-btn:hover {
    opacity: 1 !important;
    color: var(--app-text-primary);
    background: var(--app-bg-hover);
  }

  @keyframes blink {
    0%,
    100% {
      opacity: 1;
    }
    50% {
      opacity: 0;
    }
  }
  .chat-message__work {
    margin-bottom: 22px;
  }
  .chat-message__work-toggle {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 0 0 12px;
    margin-bottom: 16px;
    color: var(--app-text-secondary);
    border: none;
    border-bottom: 1px solid var(--app-border);
    background: transparent;
    text-align: left;
    font: inherit;
    font-size: 15px;
  }
  .chat-message__work-toggle .el-icon {
    transition: transform 0.15s;
  }
  .chat-message__work-toggle .is-expanded {
    transform: rotate(90deg);
  }
</style>
