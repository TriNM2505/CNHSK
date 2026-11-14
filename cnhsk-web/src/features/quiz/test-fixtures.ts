import type { QuizListingPage, QuizSession } from './types'

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
