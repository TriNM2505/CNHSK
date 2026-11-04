import type { PreviewEntry } from '@/app/preview/types'
import type { ServedItemView, SessionView } from '@/features/practice/types'
import type { KnowledgeDomain, KnowledgeTree } from '@/features/question-bank/types'

import type {
  EvidenceView,
  MasteryNode,
  MasteryNodeDetail,
  MasteryState,
  MasteryTree,
  NothingDue,
  Recommendation,
  ReviewSummary,
} from './types'

// Original demo content; domain codes stay unchanged while OQ-1 is open.
const domainSamples: { domain: KnowledgeDomain; names: string[] }[] = [
  {
    domain: 'NA',
    names: ['Nghe lời chào', 'Nghe số phòng', 'Nghe giờ hẹn', 'Nghe giá món ăn', 'Nghe chỉ đường'],
  },
  {
    domain: 'CH',
    names: [
      'Nhận diện nét ngang',
      'Phân biệt chữ gần giống',
      'Ghép bộ đơn giản',
      'Nhận diện chữ trong câu',
      'So sánh cấu tạo chữ',
    ],
  },
  {
    domain: 'TV',
    names: [
      'Từ chỉ đồ dùng',
      'Từ chỉ gia đình',
      'Từ chỉ thời tiết',
      'Từ chỉ phương tiện',
      'Từ chỉ kế hoạch',
    ],
  },
  {
    domain: 'NP',
    names: [
      'Câu giới thiệu',
      'Câu hỏi có 吗',
      'Câu phủ định với 不',
      'Câu chỉ vị trí',
      'Câu so sánh ngắn',
    ],
  },
  {
    domain: 'NL',
    names: [
      'Đọc tên người',
      'Đọc thông báo lớp',
      'Đọc lịch sinh hoạt',
      'Đọc đoạn giới thiệu',
      'Đọc lời nhắn công việc',
    ],
  },
  {
    domain: 'VB',
    names: [
      'Ghép lời chào',
      'Nối hai ý ngắn',
      'Sắp xếp lời nhắn',
      'Kể việc trong ngày',
      'Viết lời mời ngắn',
    ],
  },
]

const levels = [1, 1, 2, 2, 3] as const
const states: MasteryState[] = ['review', 'consolidate', 'notyet', 'extend', 'insufficient']

export const knowledgeTreePreview: KnowledgeTree = {
  nodes: domainSamples.flatMap(({ domain, names }) =>
    names.map((nameVi, index) => ({
      kid: `${domain}${levels[index]}-${901 + index}`,
      domain,
      hskLevel: levels[index]!,
      nameVi,
      nameZh: `练习${domain}${index + 1}`,
      prereqKids:
        index === 2
          ? [`${domain}1-902`]
          : index === 3
            ? [`${domain}1-901`]
            : index === 4
              ? [`${domain}2-904`]
              : domain === 'NP' && index === 0
                ? ['TV1-901']
                : [],
      supportKids: [],
    })),
  ),
}

// Snapshots represent API responses, not a client implementation of MP-1.0.
const snapshots: Record<
  MasteryState,
  Pick<MasteryNode, 'value' | 'confidence' | 'evidenceCount' | 'dueAt'>
> = {
  notyet: { value: null, confidence: 0, evidenceCount: 0, dueAt: null },
  insufficient: { value: 100, confidence: 0.6, evidenceCount: 1, dueAt: null },
  review: { value: 34.1, confidence: 1.759, evidenceCount: 3, dueAt: '2026-11-07T10:00:00Z' },
  consolidate: { value: 67.4, confidence: 1.497, evidenceCount: 3, dueAt: '2026-11-04T09:00:00Z' },
  extend: { value: 100, confidence: 2.932, evidenceCount: 3, dueAt: '2026-11-18T10:00:00Z' },
  inferred: { value: null, confidence: 0, evidenceCount: 0, dueAt: null },
}

export const masteryTreePreview: MasteryTree = {
  nodes: knowledgeTreePreview.nodes.map(({ kid }, index) => {
    const state = kid === 'NA1-901' ? 'inferred' : states[index % states.length]!
    return {
      kid,
      ...snapshots[state],
      state,
      frontier: state !== 'extend' && state !== 'inferred' && kid !== 'NP1-901',
    }
  }),
}

export const masteryNodeDetailsPreview: MasteryNodeDetail[] = masteryTreePreview.nodes.map(
  (node, index) => {
    const knowledgeNode = knowledgeTreePreview.nodes[index]!
    const evidence: EvidenceView[] = Array.from(
      { length: node.evidenceCount },
      (_, evidenceIndex) => ({
        answeredAt:
          node.state === 'consolidate'
            ? `2026-10-${28 - evidenceIndex}T09:00:00Z`
            : `2026-11-0${4 - evidenceIndex}T10:00:00Z`,
        source: node.state === 'extend' ? 'MOCK_EXAM' : 'TOPIC_TEST',
        context: node.state === 'extend' ? 'EXAM' : 'QUIZ',
        form: node.state === 'extend' ? 'OPEN' : 'MCQ4',
        role: 'PRIMARY',
        score:
          (node.state === 'review' && evidenceIndex > 0) ||
          (node.state === 'consolidate' && evidenceIndex === 2)
            ? 0
            : 100,
        weight: (node.state === 'extend'
          ? [1, 0.977, 0.955]
          : node.state === 'consolidate'
            ? [0.51, 0.499, 0.488]
            : [0.6, 0.586, 0.573])[evidenceIndex]!,
      }),
    )
    return {
      ...node,
      prereqs: knowledgeNode.prereqKids.map((kid) => ({
        kid,
        nameVi: knowledgeTreePreview.nodes.find((candidate) => candidate.kid === kid)!.nameVi,
        state: masteryTreePreview.nodes.find((candidate) => candidate.kid === kid)!.state,
      })),
      evidence,
      approvedQuestionCount: 3,
    }
  },
)

const recommendationSamples: Pick<Recommendation, 'kid' | 'kind' | 'reason'>[] = [
  { kid: 'TV1-901', kind: 'PREREQ', reason: 'Ôn từ chỉ đồ dùng trước khi luyện câu giới thiệu.' },
  { kid: 'NP1-901', kind: 'REVIEW', reason: 'Cần ôn: 34,1/100.' },
  { kid: 'CH1-901', kind: 'REVIEW', reason: 'Cần ôn: 34,1/100.' },
  { kid: 'TV1-902', kind: 'CONSOLIDATE', reason: 'Đến hạn củng cố từ chỉ gia đình.' },
  { kid: 'TV2-903', kind: 'NEW', reason: 'Nút mới đã đủ tiên quyết.' },
]

export const recommendationsPreview: Recommendation[] = recommendationSamples.map(
  ({ kid, kind, reason }) => ({
    kid,
    kind,
    reason,
    nameVi: knowledgeTreePreview.nodes.find((node) => node.kid === kid)!.nameVi,
    value: masteryTreePreview.nodes.find((node) => node.kid === kid)!.value,
  }),
)

export const newLearnerMasteryPreview: MasteryTree = {
  nodes: knowledgeTreePreview.nodes.map(({ kid, prereqKids }) => ({
    kid,
    ...snapshots.notyet,
    state: 'notyet',
    frontier: prereqKids.length === 0,
  })),
}

export const newLearnerRecommendationsPreview: Recommendation[] = knowledgeTreePreview.nodes
  .filter(({ prereqKids }) => prereqKids.length === 0)
  .sort((a, b) => a.hskLevel - b.hskLevel || a.kid.localeCompare(b.kid))
  .slice(0, 5)
  .map(({ kid, nameVi }) => ({
    kid,
    nameVi,
    kind: 'NEW',
    reason: 'Bắt đầu với một nút chưa có tiên quyết.',
    value: null,
  }))

const words = ['今天', '明天', '学校', '朋友', '水', '书', '猫', '桌子']
const meanings = ['hôm nay', 'ngày mai', 'trường học', 'bạn', 'nước', 'sách', 'mèo', 'cái bàn']
const grammarCards = [
  { text: '我是学生。', meaning: 'Tôi là học sinh.', pinyin: 'Wǒ shì xuésheng.' },
  { text: '你喝茶吗？', meaning: 'Bạn uống trà không?', pinyin: 'Nǐ hē chá ma?' },
  { text: '我不坐车。', meaning: 'Tôi không đi xe.', pinyin: 'Wǒ bù zuò chē.' },
  { text: '书在桌子上。', meaning: 'Sách ở trên bàn.', pinyin: 'Shū zài zhuōzi shàng.' },
]

export const dueReviewSessionPreview: SessionView = {
  sessionId: 11001,
  source: 'DUE_REVIEW',
  refId: null,
  feedback: 'IMMEDIATE',
  status: 'IN_PROGRESS',
  servedAt: '2026-11-04T10:00:00Z',
  expiresAt: '2026-11-04T12:00:00Z',
  answered: [],
  items: [
    ...words.map((text, index): ServedItemView => ({
      no: index + 1,
      type: 'MEANING_CHOICE',
      skill: 'VOCAB',
      lexemeId: 11101 + index,
      prompt: { text },
      options: ['A', 'B', 'C', 'D'].map((key, offset) => ({
        key,
        text: meanings[(index + offset - (index % 4) + meanings.length) % meanings.length]!,
      })),
      rules: {},
    })),
    ...grammarCards.map((prompt, index): ServedItemView => ({
      no: words.length + index + 1,
      type: 'FLASHCARD_RATING',
      skill: 'GRAMMAR',
      lexemeId: 11201 + index,
      prompt,
      rules: {},
    })),
  ],
}

export const reviewSummaryPreview: ReviewSummary = {
  dueCount: 12,
  nextDueAt: '2026-11-05T10:00:00Z',
}
export const reviewAheadSummaryPreview: ReviewSummary = {
  dueCount: 0,
  nextDueAt: '2026-11-05T10:00:00Z',
}
export const emptyReviewSummaryPreview: ReviewSummary = { dueCount: 0, nextDueAt: null }
export const nothingDuePreview: NothingDue = { session: null, reason: 'NOTHING_DUE' }

export const previews = [] satisfies readonly PreviewEntry[]
