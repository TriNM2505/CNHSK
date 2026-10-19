import type { ServedItemView } from '@/features/practice'

import type { TopicPracticeStepViewProps } from './TopicPracticeStepView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const recognitionItem: ServedItemView = {
  lexemeId: 101,
  no: 1,
  options: [
    { key: 'A', text: 'mẹ' },
    { key: 'B', text: 'bố' },
    { key: 'C', text: 'anh trai' },
    { key: 'D', text: 'em gái' },
  ],
  prompt: { text: '妈妈' },
  rules: { minDurationMs: 800 },
  skill: 'VOCAB',
  type: 'MEANING_CHOICE',
}

const listeningItem: ServedItemView = {
  lexemeId: 102,
  no: 1,
  options: [
    { key: 'A', text: '妈妈' },
    { key: 'B', text: '爸爸' },
    { key: 'C', text: '哥哥' },
    { key: 'D', text: '妹妹' },
  ],
  prompt: { audioUrl: '/audio/mau.mp3' },
  rules: { hasAudio: true, maxReplays: 3 },
  skill: 'LISTENING',
  type: 'AUDIO_WORD_CHOICE',
}

const noop = () => undefined

const base = {
  current: 1,
  item: recognitionItem,
  mode: 'RECOGNITION',
  onAnswer: async () => undefined,
  onAudioFailed: noop,
  onNext: noop,
  onSkip: noop,
  recognitionPath: '/learn/topics/11/practice/recognition',
  state: 'ready',
  studyPath: '/learn/topics/11/study',
  total: 10,
} satisfies TopicPracticeStepViewProps

export const topicPracticePreviews = {
  recognition: base,
  feedback: {
    ...base,
    result: {
      correct: false,
      correctAnswer: 'A',
      counted: true,
      explanation: '妈妈 nghĩa là mẹ.',
      no: 1,
    },
  },
  listening: { ...base, item: listeningItem, mode: 'LISTENING' },
  loading: { ...base, state: 'loading' },
  mismatch: {
    ...base,
    continuePath: '/learn/topics/11/practice/recognition',
    mode: 'LISTENING',
    state: 'mismatch',
  },
  unavailable: {
    ...base,
    error: { message: 'Bạn cần học ít nhất một từ trước khi luyện.' },
    state: 'unavailable',
  },
  error: {
    ...base,
    error: { message: 'Không thể kết nối tới máy chủ.', requestId: 'mau-req-0261' },
    state: 'error',
  },
  finished: {
    ...base,
    finishResult: {
      items: [],
      maxScore: 10,
      percent: 80,
      score: 8,
      sessionId: 81,
      status: 'SUBMITTED',
      suspicious: false,
    },
    state: 'finished',
  },
} satisfies Record<string, TopicPracticeStepViewProps>
