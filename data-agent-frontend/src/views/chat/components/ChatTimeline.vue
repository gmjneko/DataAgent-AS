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
  import type { TimelineEvent } from '@/utils/chatTimeline';
  import MessageContent from './MessageContent.vue';
  import TracePanel from './TracePanel.vue';

  defineProps<{ events: TimelineEvent[]; streaming: boolean }>();
  const emit = defineEmits<{ previewReport: [content: string] }>();
</script>

<template>
  <div class="chat-timeline">
    <template v-for="(event, index) in events" :key="index">
      <MessageContent
        v-if="['text', 'summary', 'thinking'].includes(event.type)"
        :content="event.content ?? ''"
        :streaming="streaming && index === events.length - 1"
        :class="{ 'chat-timeline__thinking': event.type === 'thinking' }"
      />
      <TracePanel
        v-else
        :step="event"
        :running="
          streaming &&
          event.type === 'tool_call' &&
          !events.slice(index + 1).some(next => next.toolResult?.id === event.toolCall?.id)
        "
        @preview-report="emit('previewReport', $event)"
      />
    </template>
  </div>
</template>

<style scoped>
  .chat-timeline {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  .chat-timeline__thinking {
    color: var(--app-text-secondary);
  }
</style>
