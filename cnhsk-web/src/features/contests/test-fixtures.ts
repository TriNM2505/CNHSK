import type { ContestDetail, ContestPage, ContestResults, ContestSession } from './types'

export const contestFixture: ContestDetail = {
  id: 2101,
  name: 'Thử thách đọc hiểu HSK 2',
  description: 'Đọc những câu ngắn quen thuộc và thử sức cùng cộng đồng học tiếng Trung.',
  startsAt: '2026-11-14T12:00:00Z',
  endsAt: '2026-11-14T13:00:00Z',
  state: 'ONGOING',
  prizes: [
    { fromRank: 1, toRank: 1, description: 'Bộ sách luyện đọc tiếng Trung' },
    { fromRank: 2, toRank: 3, description: 'Sổ tay học từ vựng' },
  ],
  maxParticipants: 200,
  participantCount: 48,
  rankingPublished: false,
  examTitle: 'Đọc hiểu HSK 2 — mẫu nội bộ',
  durationMinutes: 20,
  questionCount: 3,
}

export const contestPageFixture: ContestPage = {
  items: [
    contestFixture,
    {
      ...contestFixture,
      id: 2102,
      name: 'Khởi động cùng HSK 1',
      state: 'UPCOMING',
      startsAt: '2026-11-21T12:00:00Z',
      endsAt: '2026-11-21T13:00:00Z',
      participantCount: 0,
    },
    {
      ...contestFixture,
      id: 2103,
      name: 'Chinh phục câu ngắn HSK 3',
      state: 'ENDED',
      startsAt: '2026-11-07T12:00:00Z',
      endsAt: '2026-11-07T13:00:00Z',
      rankingPublished: true,
      participantCount: 86,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 3,
}

export const contestSessionFixture: ContestSession = {
  contestId: 2101,
  sessionId: 2151,
  deadline: '2026-11-14T12:20:00Z',
  serverTime: '2026-11-14T12:00:00Z',
  resumed: false,
  items: [
    {
      no: 1,
      type: 'QUESTION_CHOICE',
      skill: 'READING',
      prompt: { text: '小林每天坐公共汽车去学校。小林怎么去学校？' },
      options: [
        { key: 'A', text: '坐公共汽车' },
        { key: 'B', text: '骑自行车' },
        { key: 'C', text: '走路' },
      ],
      rules: { minDurationMs: 1000, maxReplays: 0, hasAudio: false },
    },
    {
      no: 2,
      type: 'QUESTION_CHOICE',
      skill: 'READING',
      prompt: { text: '今天下雨了，我带了一把伞。今天的天气怎么样？' },
      options: [
        { key: 'A', text: '下雨了' },
        { key: 'B', text: '天气很热' },
      ],
      rules: { minDurationMs: 1000, maxReplays: 0, hasAudio: false },
    },
    {
      no: 3,
      type: 'QUESTION_CHOICE',
      skill: 'READING',
      prompt: { text: '姐姐喜欢在周末看书。姐姐周末喜欢做什么？' },
      options: [
        { key: 'A', text: '看书' },
        { key: 'B', text: '打球' },
      ],
      rules: { minDurationMs: 1000, maxReplays: 0, hasAudio: false },
    },
  ],
}

export const unpublishedFixture: ContestResults = {
  contest: contestFixture,
  published: false,
  mine: {
    status: 'SUBMITTED',
    submittedAt: '2026-11-14T12:18:00Z',
    score: null,
    maxScore: null,
    durationMs: null,
    rank: null,
    excluded: null,
  },
  ranking: null,
}

export const publishedFixture: ContestResults = {
  contest: { ...contestFixture, state: 'ENDED', rankingPublished: true },
  published: true,
  mine: {
    status: 'SUBMITTED',
    submittedAt: '2026-11-14T12:18:00Z',
    score: 20,
    maxScore: 30,
    durationMs: 1_080_000,
    rank: 2,
    excluded: false,
  },
  ranking: {
    items: [
      {
        rank: 1,
        userId: 201,
        displayName: 'Mây Nhỏ',
        score: 30,
        maxScore: 30,
        durationMs: 960_000,
      },
      {
        rank: 2,
        userId: 202,
        displayName: 'An Nhiên',
        score: 20,
        maxScore: 30,
        durationMs: 1_080_000,
      },
      {
        rank: 3,
        userId: 203,
        displayName: 'Học Mỗi Ngày',
        score: 20,
        maxScore: 30,
        durationMs: 1_100_000,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 3,
  },
}
