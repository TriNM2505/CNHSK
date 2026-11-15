import type { AdminContest, AdminExamSummary, Page, Participant, Winner } from './types'

export const examFixture: AdminExamSummary = {
  id: 41,
  code: 'DEMO-HSK3',
  title: 'Đề tự soạn HSK 3',
  hskLevel: 3,
  status: 'DRAFT',
  publishedAt: null,
  questionCount: 30,
  inUse: false,
}
export const examPageFixture: Page<AdminExamSummary> = {
  items: [examFixture],
  page: 0,
  size: 20,
  totalElements: 1,
}
export const contestFixture: AdminContest = {
  id: 2109,
  name: 'Thử thách HSK 3 cuối tuần',
  description: 'Cuộc thi demo với câu hỏi tự soạn.',
  examId: 41,
  examTitle: examFixture.title,
  startsAt: '2030-12-06T19:00:00+07:00',
  endsAt: '2030-12-06T21:00:00+07:00',
  maxParticipants: 200,
  prizes: [{ fromRank: 1, toRank: 1, description: 'Bộ sách học tiếng Trung' }],
  status: 'DRAFT',
  state: 'UPCOMING',
  rankingPublishedAt: null,
  participantCount: 0,
}
export const endedFixture: AdminContest = {
  ...contestFixture,
  status: 'PUBLISHED',
  state: 'ENDED',
  participantCount: 2,
  startsAt: '2026-01-01T19:00:00+07:00',
  endsAt: '2026-01-01T21:00:00+07:00',
}
export const publishedFixture: AdminContest = {
  ...endedFixture,
  rankingPublishedAt: '2026-01-02T09:00:00+07:00',
}
export const participantFixtures: Participant[] = [
  {
    userId: 31,
    displayName: 'Minh Anh',
    status: 'SUBMITTED',
    score: 28,
    maxScore: 30,
    durationMs: 780000,
    submittedAt: '2026-01-01T19:13:00+07:00',
    suspicious: false,
    cheatEventCount: 0,
  },
  {
    userId: 32,
    displayName: 'Thanh Bình',
    status: 'SUBMITTED',
    score: 25,
    maxScore: 30,
    durationMs: 90000,
    submittedAt: '2026-01-01T19:01:30+07:00',
    suspicious: true,
    cheatEventCount: 3,
  },
]
export const winnerFixtures: Winner[] = [
  {
    rank: 1,
    userId: 31,
    displayName: 'Minh Anh',
    email: 'minh.anh@example.test',
    score: 28,
    durationMs: 780000,
    prize: 'Bộ sách học tiếng Trung',
  },
]
