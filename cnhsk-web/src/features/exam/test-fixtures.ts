import type { ExamSummary, PlacementSummary } from './types'
import type { SessionView } from '@/features/practice'

export const mockAttemptFixture: SessionView = {
  sessionId: 5101,
  source: 'MOCK_EXAM',
  feedback: 'DEFERRED',
  status: 'IN_PROGRESS',
  servedAt: '2026-11-04T00:00:00Z',
  expiresAt: '2026-11-05T00:00:00Z',
  answered: [],
  items: [1, 2, 3].map((no) => ({
    no,
    type: 'QUESTION_CHOICE',
    skill: 'READING',
    prompt: { text: 'Câu tự soạn ' + no },
    options: [
      { key: 'A', text: 'Phương án một' },
      { key: 'B', text: 'Phương án hai' },
    ],
    rules: {},
  })),
  meta: {
    exam: {
      title: 'Đề thi tự soạn',
      sections: [
        { index: 1, name: 'Đọc hiểu' },
        { index: 2, name: 'Ngữ pháp' },
      ],
      sectionOf: { 1: 1, 2: 2, 3: 2 },
    },
  },
}

export const placementAttemptFixture: SessionView = {
  ...mockAttemptFixture,
  sessionId: 5201,
  source: 'PLACEMENT',
  refId: null,
  items: Array.from({ length: 5 }, (_, index) => ({
    ...mockAttemptFixture.items[0]!,
    no: index + 1,
  })),
  meta: { placement: { runId: null, block: 1, level: 2 } },
}

export const examListFixture: ExamSummary[] = [
  {
    id: 1201,
    code: 'TEST-HSK-1',
    title: 'Đề luyện tự soạn cấp 1',
    hskLevel: 1,
    questionCount: 10,
    sections: [{ index: 1, name: 'Đọc hiểu', questionCount: 10 }],
    myAttempts: 2,
    myBest: { score: 0, maxScore: 10, percent: 0 },
    activeSessionId: null,
  },
  {
    id: 1202,
    code: 'TEST-HSK-1-B',
    title: 'Đề luyện tự soạn bổ sung',
    hskLevel: 1,
    questionCount: 12,
    sections: [{ index: 1, name: 'Đọc hiểu', questionCount: 12 }],
    myAttempts: 0,
    myBest: null,
    activeSessionId: 2202,
  },
]
export const placementSummaryFixture: PlacementSummary = {
  active: null,
  last: {
    runId: 3301,
    level: 'HSK2',
    confidence: 'TENTATIVE',
    completedAt: '2026-11-03T16:45:00.000Z',
  },
}
