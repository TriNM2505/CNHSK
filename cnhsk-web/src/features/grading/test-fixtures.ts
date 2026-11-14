import type { GradingPolicy, GradingRequest, GradingRequestPage } from './types'

export const policy: GradingPolicy = {
  claimHours: 48,
  gradingHours: 24,
  minHanzi: 50,
  maxHanzi: 500,
  maxChars: 3000,
  cost: 1,
  refundTerms:
    'Được hoàn lượt nếu bạn huỷ trước khi có giáo viên nhận, hoặc nếu bài không được nhận/chấm đúng hạn',
}
export const essay =
  '每天早上我喜欢在家学习中文。吃完早饭以后，我先读一段短文，然后写下自己的想法。周末我和朋友一起练习，我们互相帮助，慢慢发现学习也可以很有趣。'
export const pendingRequest: GradingRequest = {
  id: 42,
  reference: 'GR-000042',
  status: 'PENDING',
  essay,
  prompt: 'Viết về một thói quen học tập.',
  hskLevel: 2,
  createdAt: '2035-11-15T01:00:00Z',
  deadline: '2035-11-17T01:00:00Z',
  completedAt: null,
  result: null,
  refunded: false,
}
export const completedRequest: GradingRequest = {
  ...pendingRequest,
  status: 'COMPLETED',
  completedAt: '2035-11-16T01:00:00Z',
  result: {
    score: 78,
    criteria: { grammar: 20, vocabulary: 18, coherence: 21, writing: 19 },
    feedback: 'Ý rõ ràng và có ví dụ cụ thể. Bạn có thể nối các câu ngắn để bài viết tự nhiên hơn.',
    corrections: [
      {
        original: '我们互相帮助',
        corrected: '我们也会互相帮助',
        note: 'Có thể bổ sung từ nối để làm rõ thói quen.',
      },
    ],
  },
}
export const requestPage: GradingRequestPage = {
  page: 0,
  size: 20,
  totalElements: 2,
  items: [
    {
      id: 42,
      reference: 'GR-000042',
      status: 'PENDING',
      essayPreview: essay.slice(0, 60),
      createdAt: pendingRequest.createdAt,
      deadline: pendingRequest.deadline,
      completedAt: null,
      score: null,
    },
    {
      id: 41,
      reference: 'GR-000041',
      status: 'COMPLETED',
      essayPreview: 'Bài viết mẫu tự soạn về một ngày đi học.',
      createdAt: pendingRequest.createdAt,
      deadline: null,
      completedAt: completedRequest.completedAt,
      score: 78,
    },
  ],
}
