import type { FinishResult, ServedItemView } from '@/features/practice'

import type { StudyStepViewProps, TopicStudyViewProps } from './TopicStudyView'
import type { TopicDetail } from '../types'

export const previewTopic: TopicDetail = {
  actions: {
    canPracticeListening: false,
    canPracticeRecognition: true,
    canStudy: true,
    canTakeTest: false,
    requiredWords: 8,
    retryAvailableAt: null,
    testBlockedReason: 'NOT_ENOUGH_LEARNED',
  },
  bestTestPercent: null,
  code: 'FAMILY',
  hskLevel: 1,
  id: 11,
  learnedWords: 6,
  name: 'Gia đình 家庭',
  percent: 60,
  status: 'IN_PROGRESS',
  totalWords: 10,
  words: [],
}

export const previewStudyItem: ServedItemView = {
  lexemeId: 101,
  no: 3,
  prompt: {
    audioUrl: '/audio/mama.mp3',
    examples: [{ cn: '我妈妈是老师。', pinyin: 'Wǒ māma shì lǎoshī.', vi: 'Mẹ tôi là giáo viên.' }],
    meaning: 'mẹ',
    pinyin: 'māma',
    sinoViet: 'ma ma',
    text: '妈妈',
  },
  rules: { hasAudio: true, maxReplays: 3, minDurationMs: 1000 },
  skill: 'VOCAB',
  type: 'STUDY_CARD',
}

const previewFinishResult: FinishResult = {
  items: [],
  maxScore: null,
  score: null,
  sessionId: 91,
  status: 'SUBMITTED',
  suspicious: false,
}

const noop = () => undefined

export const studyStepPreviews = {
  error: {
    error: { message: 'Chưa tải được lượt học.', requestId: 'req-preview' },
    onRetry: noop,
    recognitionPath: '/learn/topics/11/practice/recognition',
    state: 'error',
  },
  finished: {
    finishResult: previewFinishResult,
    learnedCount: 10,
    recognitionPath: '/learn/topics/11/practice/recognition',
    state: 'finished',
  },
  loading: {
    recognitionPath: '/learn/topics/11/practice/recognition',
    state: 'loading',
  },
  noNew: {
    recognitionPath: '/learn/topics/11/practice/recognition',
    state: 'no-new',
  },
  ready: {
    current: 3,
    item: previewStudyItem,
    onAudioFailed: noop,
    onContinue: noop,
    onKnown: noop,
    onReplayCountChange: noop,
    recognitionPath: '/learn/topics/11/practice/recognition',
    state: 'ready',
    total: 10,
  },
} satisfies Record<string, StudyStepViewProps>

const base: TopicStudyViewProps = {
  activeStep: 'study',
  onRetry: noop,
  state: 'ready',
  topic: previewTopic,
}

export const topicStudyPreviews = {
  empty: { ...base, state: 'empty', topic: undefined },
  error: {
    ...base,
    error: { message: 'Không thể tải chủ đề.', requestId: 'req-topic' },
    state: 'error',
    topic: undefined,
  },
  finished: {
    ...base,
    studyPanel: null,
  },
  loading: { ...base, state: 'loading', topic: undefined },
  locked: { ...base, activeStep: 'listening' },
  noNew: { ...base, studyPanel: null },
  ready: { ...base, studyPanel: null },
} satisfies Record<string, TopicStudyViewProps>
