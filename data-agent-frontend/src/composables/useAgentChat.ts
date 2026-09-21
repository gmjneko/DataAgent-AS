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

import { ref, shallowRef } from 'vue';
import { streamChat, fetchSessionHistory } from '@/api/agent';
import { appendTimeline, restoreTimeline, type TimelineEvent } from '@/utils/chatTimeline';
import { INTERACTIVE_TOOLS, isInteractiveTool } from '@/utils/interactiveTools';

export interface PendingQuestion {
  toolCallId: string;
  toolName: string;
  question: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'agent';
  content: string;
  timeline: TimelineEvent[];
  outcome?: 'complete' | 'stopped' | 'error';
  isStreaming: boolean;
  timestamp: number;
}

let seq = 0;
function nextId(): string {
  return `msg_${Date.now()}_${++seq}`;
}

function generateSessionId(): string {
  return `session_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export function useAgentChat(initialSessionId?: string) {
  const messages = shallowRef<ChatMessage[]>([]);
  const isStreaming = ref(false);
  const sessionId = ref(initialSessionId || generateSessionId());
  // 待绑定/已绑定的数据源 id；新会话选源后设置，首条消息随请求落库。
  const datasourceId = ref<number | null>(null);
  const abortController = shallowRef<AbortController | null>(null);
  const pendingQuestion = ref<PendingQuestion | null>(null);
  const lastReportContent = ref<string | null>(null);

  function addUserMessage(text: string): ChatMessage {
    const msg: ChatMessage = {
      id: nextId(),
      role: 'user',
      content: text,
      timeline: [],
      isStreaming: false,
      timestamp: Date.now(),
    };
    messages.value = [...messages.value, msg];
    return msg;
  }

  function addAgentMessage(): ChatMessage {
    const msg: ChatMessage = {
      id: nextId(),
      role: 'agent',
      content: '',
      timeline: [],
      isStreaming: true,
      timestamp: Date.now(),
    };
    messages.value = [...messages.value, msg];
    return msg;
  }

  function updateAgentMessage(msgId: string, updater: (msg: ChatMessage) => void) {
    messages.value = messages.value.map(m => {
      if (m.id !== msgId) return m;
      const cloned = { ...m, timeline: [...m.timeline] };
      updater(cloned);
      return cloned;
    });
  }

  async function loadHistory(sid: string) {
    stopStreaming();
    messages.value = [];
    sessionId.value = sid;

    const turns = await fetchSessionHistory(sid);
    messages.value = turns.map(turn => ({
      id: nextId(),
      role: (turn.role === 'USER' ? 'user' : 'agent') as 'user' | 'agent',
      content: turn.content,
      timeline: restoreTimeline(turn.content, turn.traceSteps, turn.timeline),
      outcome: 'complete',
      isStreaming: false,
      timestamp: Date.now(),
    }));
  }

  async function sendMessage(text: string) {
    if (isStreaming.value || !text.trim()) return;

    addUserMessage(text);
    const agentMsg = addAgentMessage();
    isStreaming.value = true;

    const controller = new AbortController();
    abortController.value = controller;
    let summaryStarted = false;

    const pq = pendingQuestion.value;
    pendingQuestion.value = null;

    const request =
      pq != null
        ? {
            sessionId: sessionId.value,
            toolResults: [{ toolCallId: pq.toolCallId, toolName: pq.toolName, output: text }],
          }
        : {
            sessionId: sessionId.value,
            message: text,
            datasourceId: datasourceId.value ?? undefined,
          };

    try {
      for await (const event of streamChat(request, controller.signal)) {
        updateAgentMessage(agentMsg.id, msg => {
          msg.timeline = appendTimeline(msg.timeline, event);

          if (event.type === 'text' && event.content) {
            msg.content += event.content;
          }

          if (event.type === 'summary' && event.content) {
            if (!summaryStarted) {
              msg.content += '\n\nSummary:\n' + event.content;
              summaryStarted = true;
            } else {
              msg.content += event.content;
            }
          }

          if (event.type === 'error') {
            msg.outcome = 'error';
          }

          if (
            event.type === 'tool_call' ||
            event.type === 'tool_result' ||
            event.type === 'report'
          ) {
            if (event.type === 'tool_call' && isInteractiveTool(event.toolCall?.name)) {
              const def = INTERACTIVE_TOOLS[event.toolCall!.name];
              pendingQuestion.value = {
                toolCallId: event.toolCall!.id,
                toolName: event.toolCall!.name,
                question: (event.toolCall!.input[def.questionField] as string) ?? '',
              };
            }

            if (event.type === 'report') {
              lastReportContent.value = event.toolResult?.output ?? null;
            }
          }
        });
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        updateAgentMessage(agentMsg.id, msg => {
          msg.outcome = 'error';
          msg.timeline = appendTimeline(msg.timeline, {
            type: 'error',
            content: `请求失败: ${(e as Error).message}`,
            toolCall: null,
            toolResult: null,
            errorCode: null,
          });
        });
      }
    } finally {
      updateAgentMessage(agentMsg.id, msg => {
        msg.isStreaming = false;
        msg.outcome ??= controller.signal.aborted ? 'stopped' : 'complete';
      });
      isStreaming.value = false;
      abortController.value = null;
    }
  }

  function stopStreaming() {
    abortController.value?.abort();
  }

  function clearMessages() {
    messages.value = [];
  }

  function newSession() {
    stopStreaming();
    clearMessages();
    sessionId.value = generateSessionId();
    datasourceId.value = null;
  }

  return {
    messages,
    isStreaming,
    sessionId,
    datasourceId,
    pendingQuestion,
    lastReportContent,
    loadHistory,
    sendMessage,
    stopStreaming,
    clearMessages,
    newSession,
  };
}
