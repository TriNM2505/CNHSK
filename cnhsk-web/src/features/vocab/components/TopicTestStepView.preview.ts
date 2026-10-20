import type { ServedItemView } from '@/features/practice'

import type { TopicTestStepViewProps } from './TopicTestStepView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const items: ServedItemView[] = [
  {
    no: 1,
    options: [
      { key: 'A', text: 'mẹ' },
      { key: 'B', text: 'bố' },
      { key: 'C', text: 'anh trai' },
      { key: 'D', text: 'em gái' },
    ],
    prompt: { text: '妈妈' },
    rules: {},
    skill: 'VOCAB',
    type: 'MEANING_CHOICE',
  },
  {
    no: 2,
    prompt: { hint: 'bạn bè', text: '朋友' },
    rules: {},
    skill: 'VOCAB',
    type: 'PINYIN_INPUT',
  },
  {
    no: 3,
    options: [
      { key: 'A', text: '朋友' },
      { key: 'B', text: '老师' },
      { key: 'C', text: '学生' },
      { key: 'D', text: '医生' },
    ],
    prompt: { hint: 'Chúng tôi là bạn.', sentence: '我们是＿＿' },
    rules: {},
    skill: 'READING',
    type: 'CLOZE_CHOICE',
  },
]

const base = {
  activeNo: 1,
  drafts: { 1: { answer: 'A', replayCount: 0 } },
  items,
  state: 'ready',
  unanswered: 2,
} satisfies TopicTestStepViewProps

export const topicTestPreviews = {
  intro: { state: 'intro' },
  ready: base,
  pinyin: { ...base, activeNo: 2 },
  cloze: { ...base, activeNo: 3 },
  confirm: { ...base, confirmOpen: true },
  notEnough: { learnedWords: 5, requiredWords: 8, state: 'not-enough' },
  retryLater: { state: 'retry-later', waitSeconds: 425 },
  expired: { state: 'expired' },
  finished: {
    result: {
      firstCompletion: false,
      maxScore: 20,
      passed: false,
      percent: 75,
      score: 15,
      wrongWords: [
        { lexemeId: 102, meaning: 'bố', pinyin: 'bàba', text: '爸爸' },
        { lexemeId: 103, meaning: 'anh trai', pinyin: 'gēge', text: '哥哥' },
      ],
    },
    state: 'finished',
  },
  passed: {
    result: {
      firstCompletion: true,
      maxScore: 20,
      passed: true,
      percent: 95,
      score: 19,
      wrongWords: [],
    },
    state: 'finished',
  },
} satisfies Record<string, TopicTestStepViewProps>
