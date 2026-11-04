import { createElement } from 'react'
import type { PreviewEntry } from '@/app/preview/types'
import type { SessionView } from '@/features/practice'

import type { ExamResult, ExamSummary, PlacementResult, PlacementStep } from './types'
import { ExamListView } from './components/ExamListView'
import { placementSummaryFixture } from './test-fixtures'

const optionSet = [
  { key: 'A', text: 'Phương án mẫu A' },
  { key: 'B', text: 'Phương án mẫu B' },
] as const

export const examPreviews = [
  {
    activeSessionId: null,
    code: 'DEMO-HSK-1',
    hskLevel: 1,
    id: 1201,
    myAttempts: 1,
    myBest: { maxScore: 10, percent: 70, score: 7 },
    questionCount: 10,
    sections: [{ index: 1, name: 'Từ và mẫu câu', questionCount: 10 }],
    title: 'Đề luyện HSK 1 — Mẫu nội bộ',
  },
  {
    activeSessionId: 2202,
    code: 'DEMO-HSK-2',
    hskLevel: 2,
    id: 1202,
    myAttempts: 0,
    myBest: null,
    questionCount: 12,
    sections: [{ index: 1, name: 'Đọc hiểu', questionCount: 12 }],
    title: 'Đề luyện HSK 2 — Mẫu nội bộ',
  },
  {
    activeSessionId: null,
    code: 'DEMO-HSK-3',
    hskLevel: 3,
    id: 1203,
    myAttempts: 2,
    myBest: { maxScore: 15, percent: 80, score: 12 },
    questionCount: 15,
    sections: [{ index: 1, name: 'Ứng dụng ngôn ngữ', questionCount: 15 }],
    title: 'Đề luyện HSK 3 — Mẫu nội bộ',
  },
] satisfies ExamSummary[]

function placementSession(sessionId: number, level: 1 | 2 | 3): SessionView {
  return {
    answered: [],
    expiresAt: '2026-11-03T17:00:00.000Z',
    feedback: 'DEFERRED',
    items: [
      {
        no: 1,
        options: [...optionSet],
        prompt: { text: `Câu hỏi mẫu cho khối cấp ${level}.` },
        rules: {},
        skill: 'GRAMMAR',
        type: 'QUESTION_CHOICE',
      },
    ],
    meta: { block: level },
    refId: null,
    servedAt: '2026-11-03T16:00:00.000Z',
    sessionId,
    source: 'PLACEMENT',
    status: 'IN_PROGRESS',
  }
}

export const placementStepPreviews = [
  { concluded: false, runId: 3301, session: placementSession(4301, 1) },
  { concluded: false, runId: 3301, session: placementSession(4302, 2) },
  { concluded: false, runId: 3301, session: placementSession(4303, 3) },
] satisfies PlacementStep[]

export const examResultPreview = {
  bySection: [{ index: 1, maxScore: 10, name: 'Từ và mẫu câu', score: 7 }],
  correctCount: 7,
  exam: { hskLevel: 1, id: 1201, title: 'Đề luyện HSK 1 — Mẫu nội bộ' },
  items: [
    {
      correctKey: 'A',
      errorKid: null,
      errorNote: null,
      explanation: 'Lời giải tự soạn cho dữ liệu xem trước.',
      no: 1,
      options: [...optionSet],
      primaryKid: 'NP1-101',
      primaryKidName: 'Mẫu câu cơ bản',
      result: 'CORRECT',
      sectionIndex: 1,
      stem: 'Chọn phương án phù hợp cho câu mẫu.',
      yourAnswer: 'A',
    },
  ],
  maxScore: 10,
  percent: 70,
  previous: null,
  score: 7,
  sessionId: 2201,
  submittedAt: '2026-11-03T16:30:00.000Z',
  totalCount: 10,
  wrongByKid: [],
} satisfies ExamResult

export const placementResultPreview = {
  byLevel: [
    { correct: 4, level: 1, percent: 80, total: 5 },
    { correct: 3, level: 2, percent: 60, total: 5 },
    { correct: 2, level: 3, percent: 40, total: 5 },
  ],
  completedAt: '2026-11-03T16:45:00.000Z',
  confidence: 'TENTATIVE',
  domains: [
    { correct: 2, domain: 'NA', measured: true, percent: 67, tentative: true, total: 3 },
    { correct: 2, domain: 'CH', measured: true, percent: 67, tentative: true, total: 3 },
    { correct: 1, domain: 'TV', measured: true, percent: 33, tentative: true, total: 3 },
    { correct: 2, domain: 'NP', measured: true, percent: 67, tentative: true, total: 3 },
    { correct: 0, domain: 'NL', measured: false, percent: null, tentative: false, total: 0 },
    { correct: 2, domain: 'VB', measured: true, percent: 67, tentative: true, total: 3 },
  ],
  itemCount: 15,
  level: 'HSK2',
  runId: 3301,
  wrongKids: [{ kid: 'NP1-101', nameVi: 'Mẫu câu cơ bản', total: 2, wrong: 1 }],
} satisfies PlacementResult

export const previews = (['new', 'active', 'result'] as const).map((mode): PreviewEntry => ({
  group: 'screen',
  id: `scr-020-${mode}`,
  scr: 'SCR-020',
  title: `Đề thi thử — ${mode}`,
  states: ['ready', 'loading', 'empty', 'error'],
  render: (state) =>
    createElement(ExamListView, {
      exams: state === 'empty' ? [] : examPreviews.filter((exam) => exam.hskLevel === 1),
      placement:
        mode === 'active'
          ? { ...placementSummaryFixture, active: { runId: 3302, sessionId: 4302 } }
          : mode === 'result'
            ? placementSummaryFixture
            : { active: null, last: null },
      level: 1,
      state: state === 'empty' ? 'ready' : state,
      error: 'Không thể tải đề thi.',
      onLevelChange: () => undefined,
      onRetry: () => undefined,
      onStartExam: () => undefined,
      onStartPlacement: () => undefined,
    }),
})) satisfies readonly PreviewEntry[]
