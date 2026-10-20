import type { AnswerResult, ServedItemView } from '@/features/practice'

import type { PronunciationFinishExtra, PronunciationStageSummary } from '../types'
import type { PronunciationPracticeViewProps } from './PronunciationPracticeView'

const noop = () => undefined
const answer = async () => undefined

const stage: PronunciationStageSummary = {
  activeAnswered: 3,
  activeSessionId: 202,
  bestAccuracy: 75,
  description: 'Nghe và phân biệt các âm cuốn lưỡi.',
  id: 103,
  name: 'Âm cuốn lưỡi',
  passPercent: 80,
  playable: true,
  stageIndex: 3,
  state: 'AVAILABLE',
  targetSounds: ['zh', 'ch', 'sh', 'r'],
  unitCount: 10,
}

const item: ServedItemView = {
  no: 4,
  options: [
    { key: 'A', text: 'zhī' },
    { key: 'B', text: 'chī' },
    { key: 'C', text: 'shī' },
    { key: 'D', text: 'rì' },
  ],
  prompt: { audioUrl: '/audio/pronunciation/zh.mp3', text: 'Nghe rồi chọn âm bạn vừa nghe' },
  rules: { hasAudio: true, maxReplays: 3, minDurationMs: 500 },
  skill: 'PRONUNCIATION',
  type: 'PRON_CHOICE',
}

const feedback: AnswerResult = {
  accepted: true,
  correct: false,
  correctAnswer: 'A',
  counted: true,
  explanation: 'Âm zh không bật hơi, khác với ch bật hơi mạnh.',
  no: 4,
}

function result(overrides: Partial<PronunciationFinishExtra> = {}): PronunciationFinishExtra {
  return {
    accuracy: 90,
    bestAccuracy: 90,
    completedAllStages: false,
    needed: 0,
    nextStageIndex: 4,
    noScore: false,
    passed: true,
    passPercent: 80,
    stageIndex: 3,
    unlockedNextStage: true,
    ...overrides,
  }
}

const base: Pick<PronunciationPracticeViewProps, 'onRetry' | 'stage'> = { onRetry: noop, stage }

export const pronunciationPracticePreviews = {
  completedAll: {
    ...base,
    finishExtra: result({ completedAllStages: true, nextStageIndex: null }),
    onRestart: noop,
    state: 'finished',
  },
  feedback: {
    ...base,
    answered: 4,
    item,
    onAnswer: answer,
    onAudioFailed: noop,
    onNext: noop,
    result: feedback,
    state: 'ready',
    total: 10,
  },
  loading: { onRetry: noop, state: 'loading' },
  needed: {
    ...base,
    finishExtra: result({ accuracy: 72.5, needed: 7.5, passed: false, unlockedNextStage: false }),
    onRestart: noop,
    state: 'finished',
  },
  noScore: {
    ...base,
    finishExtra: result({
      accuracy: null,
      needed: null,
      noScore: true,
      passed: false,
      unlockedNextStage: false,
    }),
    onRestart: noop,
    state: 'finished',
  },
  ready: {
    ...base,
    answered: 3,
    item,
    onAnswer: answer,
    onAudioFailed: noop,
    onNext: noop,
    state: 'ready',
    total: 10,
  },
  unlocked: {
    ...base,
    finishExtra: result(),
    onRestart: noop,
    state: 'finished',
  },
} satisfies Record<string, PronunciationPracticeViewProps>
