import type { ExamSummary, PlacementSummary } from './types'

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
