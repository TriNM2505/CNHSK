import type { QuestionDetail, QuestionPage } from '@/features/question-bank/types'
import type { AdminExam, ExamDetail, ExamPage } from './types'
export const examFixture: ExamDetail = {
  id: 2301,
  code: 'TU-SOAN-01',
  title: 'Giao tiếp hằng ngày · 日常交流',
  hskLevel: 1,
  totalScore: 10,
  status: 'DRAFT',
  publishedAt: null,
  etag: 'opaque-revision-a',
  sections: [{ name: 'Đọc hiểu', type: 'READING', durationMin: 20 }],
  questions: [
    {
      questionId: 901,
      sectionIndex: 0,
      orderIndex: 0,
      points: 10,
      code: 'CAU-901',
      qtype: 'MULTIPLE_CHOICE',
      status: 'APPROVED',
      kid: 'VB1-001',
      hasAudio: false,
    },
  ],
  publicationErrors: [],
}
export const summaryFixture: AdminExam = {
  id: examFixture.id,
  code: examFixture.code,
  title: examFixture.title,
  hskLevel: 1,
  status: 'DRAFT',
  publishedAt: null,
  questionCount: 1,
  inUse: false,
}
export const pageFixture: ExamPage = {
  items: [summaryFixture],
  page: 0,
  size: 20,
  totalElements: 1,
}
export const questionFixture: QuestionDetail = {
  id: 902,
  code: 'CAU-902',
  qtype: 'TRUE_FALSE',
  hskLevel: 1,
  difficulty: 1,
  stem: '水 có nghĩa là nước.',
  options: [
    { key: 'A', text: 'Đúng', isCorrect: true },
    { key: 'B', text: 'Sai', isCorrect: false },
  ],
  source: 'MANUAL',
  status: 'APPROVED',
  labels: [{ kid: 'VB1-001', nameVi: 'Đọc hiểu', role: 'PRIMARY' }],
  used: false,
}
export const questionPageFixture: QuestionPage = {
  items: [
    {
      id: 902,
      code: 'CAU-902',
      qtype: 'TRUE_FALSE',
      hskLevel: 1,
      difficulty: 1,
      stemSummary: questionFixture.stem,
      source: 'MANUAL',
      status: 'APPROVED',
      primaryKid: 'VB1-001',
      secondaryKids: [],
      used: false,
    },
  ],
  page: 0,
  size: 10,
  totalElements: 1,
}
