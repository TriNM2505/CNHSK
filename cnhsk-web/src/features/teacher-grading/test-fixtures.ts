import type {
  AssignedItem,
  GradingResultWrite,
  QueuePage,
  ResultDraft,
  TeacherGradingDetail,
} from './types'

export const assigned: TeacherGradingDetail = {
  id: 42,
  reference: 'GR-000042',
  status: 'ASSIGNED',
  essay:
    '今天我和朋友一起学习中文。我们先读一段短文，然后写下自己的想法。遇到不懂的词语时，我们会先看例句，再试着自己造句。这样学习让我觉得很开心。',
  prompt: 'Viết về một buổi học cùng bạn.',
  hskLevel: 2,
  hanziCount: 62,
  assignedAt: '2035-11-15T01:00:00Z',
  gradingDeadline: '2035-11-16T01:00:00Z',
  payoutCredits: 0,
  result: null,
}
export const queue: QueuePage = {
  items: [
    {
      id: 43,
      reference: 'GR-000043',
      hanziCount: 120,
      hskLevel: null,
      sentAt: '2035-11-15T01:00:00Z',
      claimDeadline: '2035-11-17T01:00:00Z',
      payoutCredits: 0,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
}
export const mine: AssignedItem[] = [
  {
    id: assigned.id,
    reference: assigned.reference,
    hanziCount: assigned.hanziCount,
    assignedAt: assigned.assignedAt,
    gradingDeadline: assigned.gradingDeadline,
  },
]
export const result: GradingResultWrite = {
  criteria: { grammar: 20, vocabulary: 18, coherence: 21, writing: 19 },
  feedback: 'Bài có ý rõ ràng. Bạn có thể thêm một ví dụ để làm rõ cách học của mình.',
  corrections: [
    { original: '自己造句', corrected: '用自己的话造句', note: 'Có thể diễn đạt cụ thể hơn.' },
  ],
}
export const draft: ResultDraft = {
  criteria: { grammar: '20', vocabulary: '18', coherence: '21', writing: '19' },
  feedback: result.feedback,
  corrections: result.corrections.map((item) => ({ ...item, note: item.note ?? '' })),
}
export const completed: TeacherGradingDetail = {
  ...assigned,
  status: 'COMPLETED',
  result: { ...result, score: 78 },
  payoutCredits: 2,
}
