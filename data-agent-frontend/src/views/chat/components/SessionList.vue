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
  import { ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { fetchSessionList, clearSession, type SessionInfo } from '@/api/agent';

  const props = defineProps<{
    activeSessionId: string | null;
  }>();

  const emit = defineEmits<{
    newSession: [];
    sessionDeleted: [sessionId: string];
  }>();

  const router = useRouter();
  const sessions = ref<SessionInfo[]>([]);
  const loading = ref(false);

  async function loadList() {
    loading.value = true;
    try {
      sessions.value = await fetchSessionList();
    } finally {
      loading.value = false;
    }
  }

  function formatTime(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return '刚刚';
    if (diffMin < 60) return `${diffMin}分钟前`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return `${diffHour}小时前`;
    const diffDay = Math.floor(diffHour / 24);
    if (diffDay < 7) return `${diffDay}天前`;
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
  }

  function selectSession(sid: string) {
    router.push(`/chat/${sid}`);
  }

  function handleNewSession() {
    emit('newSession');
  }

  async function handleDelete(e: MouseEvent, s: SessionInfo) {
    e.stopPropagation();
    try {
      await ElMessageBox.confirm(`确定要删除会话「${s.title || s.sessionId}」吗？`, '提示', {
        type: 'warning',
      });
    } catch {
      return;
    }
    await clearSession(s.sessionId);
    ElMessage.success('删除成功');
    emit('sessionDeleted', s.sessionId);
    if (s.sessionId === props.activeSessionId) {
      router.push('/chat');
    }
  }

  defineExpose({ loadList });

  onMounted(() => loadList());
</script>

<template>
  <div class="session-list">
    <div class="session-list__header">
      <div>
        <div class="session-list__eyebrow">WORKSPACE</div>
        <span class="session-list__title">会话</span>
      </div>
      <button class="session-list__refresh" title="刷新会话" @click="loadList">
        <el-icon :class="{ spinning: loading }"><Refresh /></el-icon>
      </button>
    </div>

    <button class="session-list__new" @click="handleNewSession">
      <el-icon><Plus /></el-icon>
      <span>新建会话</span>
    </button>

    <div class="session-list__body">
      <div class="session-list__section-label">最近会话</div>
      <div v-if="sessions.length === 0 && !loading" class="session-list__empty">暂无会话</div>
      <div
        v-for="s in sessions"
        :key="s.sessionId"
        class="session-item"
        :class="{ active: s.sessionId === activeSessionId }"
        @click="selectSession(s.sessionId)"
      >
        <div class="session-item__row">
          <div class="session-item__main">
            <div class="session-item__title">{{ s.title || s.sessionId }}</div>
            <div class="session-item__ds">{{ s.datasourceName ?? '未绑定数据源' }}</div>
            <div class="session-item__time">{{ formatTime(s.lastActiveAt) }}</div>
          </div>
          <button class="session-item__delete" @click="e => handleDelete(e, s)" title="删除会话">
            ×
          </button>
        </div>
      </div>
    </div>

    <div class="session-list__footer">
      <span class="session-list__footer-tip">对话会自动保存</span>
    </div>
  </div>
</template>

<style scoped>
  .session-list {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--app-bg-sidebar);
    border-right: 1px solid var(--app-border);
    border-radius: 0;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .session-list__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px 17px 14px;
    flex-shrink: 0;
  }

  .session-list__title {
    display: block;
    margin-top: 2px;
    font-size: 16px;
    font-weight: 650;
    color: var(--app-text-primary);
  }

  .session-list__eyebrow {
    color: var(--app-text-muted);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.16em;
  }

  .session-list__refresh {
    display: grid;
    width: 28px;
    height: 28px;
    place-items: center;
    border: none;
    border-radius: 7px;
    color: var(--app-text-muted);
    background: transparent;
  }

  .session-list__refresh:hover {
    color: var(--app-text-primary);
    background: var(--app-bg-hover);
  }

  .session-list__refresh .spinning {
    animation: spin 0.8s linear infinite;
  }

  .session-list__new {
    display: flex;
    align-items: center;
    gap: 9px;
    width: calc(100% - 24px);
    margin: 6px 12px 13px;
    padding: 10px 12px;
    border: 1px solid var(--app-border);
    border-radius: 9px;
    color: var(--app-text-primary);
    background: var(--app-bg-card);
    font-size: 13px;
    font-weight: 550;
    box-shadow: var(--app-shadow-sm);
    transition:
      border-color 0.15s,
      background-color 0.15s;
  }

  .session-list__new:hover {
    border-color: var(--app-text-muted);
    background: var(--app-bg-hover);
  }

  .session-list__new-shortcut {
    margin-left: auto;
    color: var(--app-text-muted);
    font-size: 11px;
  }

  .session-list__body {
    flex: 1;
    overflow-y: auto;
    padding: 0 10px 10px;
  }

  .session-list__section-label {
    padding: 7px 7px 8px;
    color: var(--app-text-muted);
    font-size: 11px;
    font-weight: 600;
  }

  .session-list__empty {
    padding: 24px 16px;
    text-align: center;
    color: var(--app-text-muted);
    font-size: 13px;
  }

  .session-item {
    padding: 10px 11px;
    border-radius: 8px;
    cursor: pointer;
    transition: background-color 0.1s;
    margin-bottom: 3px;
  }

  .session-item__row {
    display: flex;
    align-items: flex-start;
  }

  .session-item__main {
    flex: 1;
    min-width: 0;
  }

  .session-item__delete {
    display: none;
    flex-shrink: 0;
    margin-left: 8px;
    margin-top: 2px;
    width: 20px;
    height: 20px;
    line-height: 20px;
    font-size: 14px;
    color: var(--app-text-muted);
    background: none;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    text-align: center;
    padding: 0;
  }

  .session-item__delete:hover {
    color: #f56c6c;
    background: rgba(245, 108, 108, 0.1);
  }

  .session-item:hover .session-item__delete {
    display: block;
  }

  .session-item:hover {
    background: var(--app-bg-hover);
  }

  .session-item.active {
    background: var(--app-bg-hover);
    box-shadow: inset 2px 0 0 var(--app-accent);
  }

  .session-item__title {
    font-size: 13px;
    color: var(--app-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.4;
  }

  .session-item.active .session-item__title {
    color: var(--app-text-primary);
    font-weight: 600;
  }

  .session-item__time {
    font-size: 11px;
    color: var(--app-text-muted);
    margin-top: 4px;
  }

  .session-item__ds {
    font-size: 11px;
    color: var(--app-text-muted);
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .session-list__footer {
    padding: 12px 17px 17px;
    border-top: 1px solid var(--app-border);
    flex-shrink: 0;
  }

  .session-list__footer-tip {
    color: var(--app-text-muted);
    font-size: 11px;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
