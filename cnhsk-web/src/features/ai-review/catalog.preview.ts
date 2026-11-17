import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { knowledgeTreeSample, questionSamples } from '@/features/question-bank/catalog.preview'
import { QuestionEditView } from '@/features/question-bank/components/QuestionEditView'
import { ApiError } from '@/shared/api'

import { AIReviewQueueView } from './components/AIReviewQueueView'
import { rejectionReasonError, type ReviewQueueQuery } from './reviewQueueQuery'
import type { AIQuestionPage, BulkResult } from './types'

// Preview-only, self-authored examples. Runtime pages never import this catalog.
export const reviewQueueSample: AIQuestionPage = {
  page: 0,
  size: 20,
  totalElements: 3,
  items: [
    {
      id: 921,
      stemSummary: 'Chọn từ chỉ cuốn sách trong câu 我看书。',
      hskLevel: 1,
      labels: [{ kid: 'TV1-901', role: 'PRIMARY' }],
      generatedAt: '2026-11-16T03:00:00Z',
      runId: 12,
      viewedByMe: false,
    },
    {
      id: 922,
      stemSummary: 'Sắp xếp các từ thành câu về buổi học ngày mai.',
      hskLevel: 2,
      labels: [
        { kid: 'NP2-901', role: 'PRIMARY' },
        { kid: 'TV2-901', role: 'SECONDARY' },
      ],
      generatedAt: '2026-11-16T04:00:00Z',
      runId: 12,
      viewedByMe: true,
    },
    {
      id: 923,
      stemSummary: 'Chọn vị trí phù hợp trong câu giới thiệu thư viện.',
      hskLevel: 3,
      labels: [{ kid: 'NP3-901', role: 'PRIMARY' }],
      generatedAt: '2026-11-17T02:00:00Z',
      runId: null,
      viewedByMe: true,
    },
  ],
}

function QueuePreview({ state, mixed = false }: { state: PreviewState; mixed?: boolean }) {
  const [query, setQuery] = useState<ReviewQueueQuery>({
    kid: '',
    hsk: '',
    from: '',
    to: '',
    page: 0,
    size: 20,
  })
  const [current, setCurrent] = useState(state)
  const [selected, setSelected] = useState<number[]>([])
  const [result, setResult] = useState<BulkResult | undefined>(
    mixed
      ? {
          results: [
            { id: 921, outcome: 'NOT_VIEWED' },
            { id: 922, outcome: 'APPROVED' },
            { id: 923, outcome: 'NOT_PENDING' },
            { id: 924, outcome: 'MALFORMED' },
          ],
        }
      : undefined,
  )
  return createElement(AIReviewQueueView, {
    query,
    state: current,
    data:
      current === 'empty'
        ? { ...reviewQueueSample, items: [], totalElements: 0 }
        : reviewQueueSample,
    error: new ApiError({
      status: 503,
      errorCode: 'NETWORK_ERROR',
      message: 'Kết nối mẫu đang gián đoạn.',
      requestId: 'preview-review',
    }),
    selected,
    pending: false,
    result,
    onQueryChange: (next) => {
      setQuery(next)
      setSelected([])
    },
    onSelect: (id, checked) =>
      setSelected((ids) => (checked ? [...ids, id] : ids.filter((value) => value !== id))),
    onOpen: () => undefined,
    onRetry: () => setCurrent('ready'),
    onBulkApprove: () => {
      setResult({ results: selected.map((id) => ({ id, outcome: 'APPROVED' })) })
      setSelected([])
    },
  })
}

function ReviewDetailPreview() {
  const [data, setData] = useState({
    ...questionSamples[1]!,
    source: 'AI' as const,
    status: 'PENDING_REVIEW' as const,
  })
  const [reason, setReason] = useState('')
  const [action, setAction] = useState<'approve' | 'reject'>()
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  return createElement(QuestionEditView, {
    backTo: '/content/ai-review',
    canArchive: false,
    canEdit: true,
    data,
    nodes: knowledgeTreeSample.nodes,
    nodesState: 'ready',
    state: 'ready',
    pending: false,
    notice,
    onArchive: () => undefined,
    onCancelAction: () => undefined,
    onConfirmAction: () => undefined,
    onDismissError: () => undefined,
    onNodesRetry: () => undefined,
    onRetry: () => undefined,
    onSave: (body) => {
      setData({ ...data, ...body })
      setNotice('Xem trước: đã lưu thay đổi.')
    },
    review: {
      allowed: true,
      locked: false,
      reason,
      reasonError: error,
      action,
      pending: false,
      onReasonChange: (value) => {
        setReason(value)
        setError(undefined)
      },
      onRequest: (decision) => {
        const nextError = decision === 'reject' ? rejectionReasonError(reason) : undefined
        setError(nextError)
        if (!nextError) setAction(decision)
      },
      onCancel: () => setAction(undefined),
      onConfirm: () => {
        setNotice(
          action === 'approve' ? 'Xem trước: đã duyệt câu hỏi.' : 'Xem trước: đã từ chối câu hỏi.',
        )
        setAction(undefined)
      },
    },
  })
}

export const previews = [
  {
    id: 'ai-review-queue',
    group: 'screen',
    scr: 'SCR-058',
    title: 'Duyệt câu hỏi AI — Dữ liệu xem trước',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(QueuePreview, { key: state, state }),
  },
  {
    id: 'ai-review-mixed-results',
    group: 'screen',
    scr: 'SCR-058',
    title: 'Duyệt câu hỏi AI — Kết quả từng câu',
    states: ['ready'],
    render: (state) => createElement(QueuePreview, { state, mixed: true }),
  },
  {
    id: 'ai-review-question-detail',
    group: 'screen',
    scr: 'SCR-059',
    title: 'Câu hỏi AI — Chờ duyệt và lý do từ chối',
    states: ['ready'],
    render: () => createElement(ReviewDetailPreview),
  },
] satisfies readonly PreviewEntry[]
