import type { AnswerResult, FinishResult, ServedItemView } from '@/features/practice'

import type { GrammarLesson } from '../types'
import type { GrammarLessonViewProps } from './GrammarLessonView'

export const previewGrammarLesson: GrammarLesson = {
  bestPercent: 80,
  commonMistake: 'Không bỏ động từ chính sau cấu trúc 是……的.',
  examples: [
    { cn: '我是昨天来的。', pinyin: 'Wǒ shì zuótiān lái de.', vi: 'Tôi đến hôm qua.' },
    { cn: '他是坐飞机来的。', pinyin: 'Tā shì zuò fēijī lái de.', vi: 'Anh ấy đến bằng máy bay.' },
  ],
  exerciseCount: 3,
  explanation: 'Dùng để nhấn mạnh thời gian, địa điểm hoặc cách thức của một hành động đã xảy ra.',
  hskLevel: 2,
  id: 21,
  learned: true,
  structure: 'S + 是 + thành phần được nhấn mạnh + V + 的',
  text: '是……的',
  titleVi: 'Nhấn mạnh chi tiết của hành động',
}

export const previewGrammarItem: ServedItemView = {
  no: 2,
  options: [
    { key: 'A', text: '是' },
    { key: 'B', text: '在' },
    { key: 'C', text: '有' },
  ],
  prompt: { sentence: '他___坐飞机来的。' },
  rules: { minDurationMs: 800 },
  skill: 'GRAMMAR',
  type: 'GRAMMAR_CHOICE',
}

const answerResult: AnswerResult = {
  accepted: true,
  correct: true,
  correctAnswer: 'A',
  counted: true,
  explanation: 'Cấu trúc 是……的 nhấn mạnh cách thức đi lại.',
  no: 2,
}

const finishResult: FinishResult = {
  extra: {
    answered: 3,
    correct: 2,
    exercisesTotal: 3,
    firstTimeLearned: true,
    percent: 66,
    skippedExercises: false,
  },
  items: [
    { correct: true, no: 2 },
    { correct: true, no: 3 },
    { correct: false, no: 4 },
  ],
  maxScore: 3,
  score: 2,
  sessionId: 99,
  status: 'SUBMITTED',
  suspicious: false,
}

const noop = () => undefined
const answer = async () => undefined
const base: GrammarLessonViewProps = {
  lesson: previewGrammarLesson,
  onRetry: noop,
  stage: 'reading',
  state: 'ready',
}

export const grammarLessonPreviews = {
  empty: { ...base, lesson: undefined, state: 'empty' },
  error: {
    ...base,
    error: { message: 'Không thể kết nối tới máy chủ.', requestId: 'req-preview' },
    lesson: undefined,
    state: 'error',
  },
  exercise: {
    ...base,
    current: 1,
    item: previewGrammarItem,
    onAnswer: answer,
    onNext: noop,
    onSkipRequest: noop,
    stage: 'exercise',
    total: 3,
  },
  feedback: {
    ...base,
    current: 1,
    item: previewGrammarItem,
    onAnswer: answer,
    onNext: noop,
    onSkipRequest: noop,
    result: answerResult,
    stage: 'exercise',
    total: 3,
  },
  finished: { ...base, finishResult, stage: 'finished' },
  loading: { ...base, lesson: undefined, state: 'loading' },
  ready: { ...base, onBegin: noop },
  restored: { ...base, hasActiveSession: true, onBegin: noop },
} satisfies Record<string, GrammarLessonViewProps>
