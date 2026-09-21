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
  import { computed, ref } from 'vue';
  import {
    formatQuestionAnswers,
    type PendingQuestion,
    type QuestionAnswer,
  } from '@/utils/agentQuestions';

  const props = defineProps<{ pending: PendingQuestion }>();
  const emit = defineEmits<{ send: [text: string] }>();
  const answers = ref<QuestionAnswer[]>(
    props.pending.questions.map(() => ({ selected: [], custom: '' })),
  );
  const note = ref('');
  const output = computed(() =>
    formatQuestionAnswers(props.pending.questions, answers.value, note.value),
  );

  function select(index: number, option: string) {
    const answer = answers.value[index];
    if (!props.pending.questions[index].multiple) {
      answer.selected = answer.selected.includes(option) ? [] : [option];
    } else {
      answer.selected = answer.selected.includes(option)
        ? answer.selected.filter(value => value !== option)
        : [...answer.selected, option];
    }
  }

  function submit() {
    if (output.value) emit('send', output.value);
  }
</script>

<template>
  <form class="question-form" @submit.prevent="submit">
    <div class="question-form__body">
      <p v-if="pending.question" class="question-form__intro">{{ pending.question }}</p>
      <fieldset v-for="(question, index) in pending.questions" :key="index">
        <legend>
          {{ index + 1 }}. {{ question.question }}
          <span v-if="question.options.length" class="question-form__hint">
            {{ question.multiple ? '多选' : '单选' }}
          </span>
        </legend>
        <div v-if="question.options.length" class="question-form__options">
          <label
            v-for="option in question.options"
            :key="option"
            class="question-form__option"
            :class="{ 'question-form__option--selected': answers[index].selected.includes(option) }"
          >
            <input
              :type="question.multiple ? 'checkbox' : 'radio'"
              :name="`${pending.toolCallId}-${index}`"
              :value="option"
              :checked="answers[index].selected.includes(option)"
              @change="select(index, option)"
            />
            <span>{{ option }}</span>
          </label>
        </div>
        <textarea
          v-model="answers[index].custom"
          rows="1"
          :aria-label="`${question.question}：自行填写`"
          :placeholder="
            question.options.length ? '其他答案或本题补充（可直接填写）' : '请输入你的回答'
          "
        />
      </fieldset>
      <textarea v-model="note" rows="1" aria-label="补充说明" placeholder="补充说明（可选）" />
    </div>
    <div class="question-form__footer">
      <span class="question-form__hint">每题请选择选项或自行填写</span>
      <button type="submit" :disabled="!output">提交回答</button>
    </div>
  </form>
</template>

<style scoped>
  .question-form {
    font-size: 15px;
    line-height: 1.75;
    max-width: 900px;
    margin: 0 auto;
  }
  .question-form__body {
    max-height: 45vh;
    overflow-y: auto;
    padding: 2px 4px;
  }
  .question-form__intro {
    margin: 0 0 14px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    color: var(--app-text-primary);
  }
  fieldset {
    min-width: 0;
    margin: 0 0 14px;
    padding: 0;
    border: 0;
  }
  legend {
    margin-bottom: 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--app-text-primary);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
  .question-form__hint {
    color: var(--app-text-muted);
    font-size: 15px;
    font-weight: 400;
  }
  .question-form__options {
    display: grid;
    gap: 6px;
    margin-bottom: 8px;
  }
  .question-form__option {
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding: 9px 12px;
    border: 1px solid var(--app-border);
    border-radius: 8px;
    cursor: pointer;
    font-size: 15px;
    color: var(--app-text-primary);
    background: var(--app-bg-page);
  }
  .question-form__option span {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    min-width: 0;
  }
  .question-form__option--selected {
    border-color: var(--app-accent);
    background: var(--app-bg-input);
  }
  input {
    accent-color: var(--app-accent);
  }
  textarea {
    display: block;
    box-sizing: border-box;
    width: 100%;
    padding: 8px 10px;
    border: 1px solid var(--app-border);
    border-radius: 8px;
    resize: vertical;
    font: inherit;
    font-size: 15px;
    color: var(--app-text-primary);
    background: var(--app-bg-input);
  }
  textarea:focus-visible,
  input:focus-visible,
  button:focus-visible {
    outline: 2px solid var(--app-accent);
    outline-offset: 2px;
  }
  .question-form__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 10px;
  }
  button {
    font: inherit;
    border: 0;
    border-radius: 8px;
    padding: 8px 16px;
    background: var(--app-accent);
    color: var(--app-accent-text);
    cursor: pointer;
  }
  button:disabled {
    opacity: 0.4;
    cursor: default;
  }
</style>
