import type { ExamSummary, PlacementSummary } from './types'
import type { SessionView } from '@/features/practice'
import type { ExamResult, PlacementResult, ResultItem } from './types'

const resultItem: ResultItem = {
  no: 1,
  sectionIndex: 1,
  stem: 'Câu tự soạn đúng',
  result: 'CORRECT',
  correctKey: 'A',
  yourAnswer: 'A',
  explanation: 'Lời giải tự soạn',
  options: [
    { key: 'A', text: 'Phương án một' },
    { key: 'B', text: 'Phương án hai' },
  ],
  primaryKid: null,
  primaryKidName: null,
  errorKid: null,
  errorNote: null,
}
const wrongKid = { kid: 'NP1-101', nameVi: 'Mẫu câu cơ bản', wrong: 1, total: 2 }
export const examReviewFixture: ExamResult = {
  sessionId: 5101,
  exam: { id: 1201, hskLevel: 1, title: 'Đề thi tự soạn' },
  score: 2,
  maxScore: 5,
  percent: 40,
  correctCount: 1,
  totalCount: 3,
  submittedAt: '2026-11-04T00:00:00Z',
  previous: null,
  bySection: [{ index: 1, name: 'Đọc hiểu', score: 2, maxScore: 5 }],
  items: [
    resultItem,
    {
      ...resultItem,
      no: 2,
      stem: 'Câu tự soạn sai',
      result: 'WRONG',
      yourAnswer: 'B',
      primaryKid: wrongKid.kid,
    },
    {
      ...resultItem,
      no: 3,
      stem: 'Câu tự soạn bỏ trống',
      result: 'BLANK',
      yourAnswer: null,
      explanation: ' ',
    },
  ],
  wrongByKid: [wrongKid],
}
export const placementReviewFixture: PlacementResult = {
  runId: 5201,
  completedAt: '2026-11-04T00:00:00Z',
  level: 'HSK2',
  confidence: 'TENTATIVE',
  itemCount: 5,
  byLevel: [{ level: 2, correct: 2, total: 5, percent: 40 }],
  domains: [
    { domain: 'NA', measured: false, tentative: false, correct: 0, total: 0, percent: null },
    { domain: 'CH', measured: false, tentative: false, correct: 0, total: 0, percent: null },
    { domain: 'TV', measured: true, tentative: false, correct: 1, total: 3, percent: 33 },
    { domain: 'NP', measured: true, tentative: true, correct: 1, total: 2, percent: 50 },
    { domain: 'NL', measured: false, tentative: false, correct: 0, total: 0, percent: null },
    { domain: 'VB', measured: false, tentative: false, correct: 0, total: 0, percent: null },
  ],
  wrongKids: [wrongKid],
}

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
