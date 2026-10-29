import type { AnswerResult, FinishResult, ServedItemView } from '@/features/practice'

import type { RecognitionViewProps } from './RecognitionView'

const noop = () => undefined
const answer = async () => undefined

const meaningItem: ServedItemView = {
  no: 3,
  options: [
    { key: 'A', text: 'mẹ' },
    { key: 'B', text: 'tốt; hay' },
    { key: 'C', text: 'cô ấy' },
    { key: 'D', text: 'nữ' },
  ],
  prompt: { text: '好' },
  rules: { minDurationMs: 800 },
  skill: 'CHARACTER',
  type: 'CHAR_MEANING_CHOICE',
}

const audioItem: ServedItemView = {
  no: 5,
  options: [
    { key: 'A', text: '你' },
    { key: 'B', text: '他' },
    { key: 'C', text: '我' },
    { key: 'D', text: '是' },
  ],
  prompt: { audioUrl: '/audio/hanzi/ni.mp3' },
  rules: { hasAudio: true, maxReplays: 3, minDurationMs: 800 },
  skill: 'LISTENING',
  type: 'AUDIO_CHAR_CHOICE',
}

const wrong: AnswerResult = { accepted: true, correct: false, correctAnswer: 'B', counted: true, no: 3 }

const finishResult: FinishResult = {
  items: [
    { correct: true, correctAnswer: 'B', no: 1, yourAnswer: 'B' },
    { correct: false, correctAnswer: 'A', no: 2, yourAnswer: 'C' },
  ],
  maxScore: 10,
  percent: 80,
  score: 8,
  sessionId: 401,
  status: 'SUBMITTED',
  suspicious: false,
}

const setupBase = { hskLevel: 1, mode: 'CHAR_TO_MEANING', onStart: noop } as const

export const recognitionPreviews = {
  audio: {
    current: 5,
    hskLevel: 1,
    item: audioItem,
    mode: 'AUDIO_TO_CHAR',
    onAnswer: answer,
    onAudioFailed: noop,
    state: 'ready',
    total: 10,
  },
  error: {
    error: { message: 'Không kết nối được máy chủ.', requestId: 'req-015' },
    hskLevel: 1,
    mode: 'CHAR_TO_MEANING',
    onRetry: noop,
    state: 'error',
  },
  feedback: {
    current: 3,
    hskLevel: 1,
    item: meaningItem,
    mode: 'CHAR_TO_MEANING',
    onAnswer: answer,
    onNext: noop,
    result: wrong,
    state: 'ready',
    total: 10,
  },
  finished: {
    finishResult,
    hskLevel: 1,
    mode: 'CHAR_TO_MEANING',
    onRestart: noop,
    state: 'finished',
  },
  loading: { hskLevel: 1, mode: 'CHAR_TO_MEANING', state: 'loading' },
  noCharacters: { ...setupBase, hskLevel: 3, noCharacters: true, state: 'setup' },
  question: {
    current: 3,
    hskLevel: 1,
    item: meaningItem,
    mode: 'CHAR_TO_MEANING',
    onAnswer: answer,
    onSkip: noop,
    state: 'ready',
    total: 10,
  },
  setup: { ...setupBase, state: 'setup' },
} satisfies Record<string, RecognitionViewProps>
