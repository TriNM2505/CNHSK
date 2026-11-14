import type { SessionView } from '@/features/practice/types'
import type { QuizListingPage, QuizResult, QuizSession, TopicLeaderboard } from './types'

export const quizPage = {
  items: [
    {
      topicId: 43,
      topicName: 'Giao tiếp hằng ngày',
      hskLevel: 1,
      questionCount: 10,
      availableQuestions: 16,
      myFirstAttempt: null,
      myBestRetryScore: null,
    },
    {
      topicId: 44,
      topicName: 'Gia đình và bạn bè',
      hskLevel: 2,
      questionCount: 12,
      availableQuestions: 18,
      myFirstAttempt: { score: 80, submittedAt: '2026-11-14T04:00:00Z', suspicious: false },
      myBestRetryScore: 100,
    },
    {
      topicId: 45,
      topicName:
        'Chủ đề có tên rất dài để kiểm tra hiển thị trên thiết bị di động: Những hoạt động học tập và sinh hoạt mỗi ngày',
      hskLevel: 3,
      questionCount: 10,
      availableQuestions: 20,
      myFirstAttempt: { score: 0, submittedAt: '2026-11-14T04:00:00Z', suspicious: true },
      myBestRetryScore: 0,
    },
  ],
  page: 0,
  size: 20,
  total: 3,
} satisfies QuizListingPage

export const quizSession = {
  sessionId: 101,
  topicId: 43,
  rankingEligible: true,
  expiresAt: '2026-11-14T04:30:00Z',
  items: [
    {
      itemId: 1,
      prompt: 'Chọn nghĩa của 水.',
      options: [
        { key: 'A', text: 'nước' },
        { key: 'B', text: 'lửa' },
      ],
      audioUrl: null,
    },
  ],
} satisfies QuizSession

export const quizEngineSession: SessionView = {
  sessionId: 101,
  source: 'QUIZ',
  refId: 43,
  feedback: 'DEFERRED',
  status: 'IN_PROGRESS',
  servedAt: '2026-11-14T04:00:00Z',
  expiresAt: '2026-11-14T04:30:00Z',
  items: [1, 2].map((no) => ({
    no,
    type: 'QUESTION_CHOICE',
    skill: 'VOCAB',
    prompt: { text: no === 1 ? 'Chọn nghĩa của 水.' : 'Chọn nghĩa của 火.' },
    options: [
      { key: 'A', text: 'nước' },
      { key: 'B', text: 'lửa' },
    ],
    rules: {},
  })),
  answered: [],
}

export const quizResult: QuizResult = {
  sessionId: 101,
  topicId: 43,
  topicName: 'Giao tiếp hằng ngày',
  score: 50,
  correctCount: 1,
  wrongCount: 1,
  durationMs: 65000,
  rankingEligible: true,
  suspicious: false,
  firstAttempt: { score: 50, durationMs: 65000, rank: 12, total: 240 },
  items: [
    {
      itemId: 1,
      prompt: 'Chọn nghĩa của 水.',
      options: [
        { key: 'A', text: 'nước' },
        { key: 'B', text: 'lửa' },
      ],
      chosen: 'A',
      correct: 'A',
      explanation: '水 chỉ nước.',
    },
    {
      itemId: 2,
      prompt: 'Chọn nghĩa của 火.',
      options: [
        { key: 'A', text: 'nước' },
        { key: 'B', text: 'lửa' },
      ],
      chosen: null,
      correct: 'B',
      explanation: null,
    },
  ],
}

export const quizLeaderboard: TopicLeaderboard = {
  type: 'TOPIC',
  ref: '43',
  period: 'all',
  periodStart: null,
  periodEnd: null,
  entries: Array.from({ length: 12 }, (_, index) => ({
    rank: index + 1,
    userId: index + 100,
    displayName: `Học viên mẫu ${index + 1}`,
    avatarUrl: null,
    score: 100 - index,
    durationMs: 65000 + index * 1000,
  })),
  myEntry: null,
}
