import { ArrowRight } from 'lucide-react'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'

import { DOMAIN_LABELS, KNOWLEDGE_DOMAINS } from '@/features/knowledge-graph/graphModel'
import type { KnowledgeNode } from '@/features/question-bank/types'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  PageBanner,
  Skeleton,
  StatTile,
  type PageStateValue,
} from '@/shared/ui'

import { formatMasteryDateTime } from '../formatDateTime'
import type {
  EvidenceContext,
  EvidenceForm,
  EvidenceView,
  MasteryNode,
  MasteryNodeDetail,
  MasteryState,
} from '../types'

const LEVELS = [1, 2, 3] as const

const STATE_LABELS: Record<MasteryState, string> = {
  notyet: 'Chưa học',
  insufficient: 'Chưa đủ minh chứng',
  review: 'Cần ôn',
  consolidate: 'Củng cố',
  extend: 'Vững',
  inferred: 'Suy ra vững',
}

const STATE_CLASSES: Record<MasteryState, string> = {
  notyet: 'border-line bg-canvas text-ink-secondary hover:border-brand-600 hover:text-ink',
  insufficient: 'border-danger bg-danger-bg text-danger hover:border-danger hover:text-danger',
  review: 'border-danger bg-danger-bg text-danger hover:border-danger hover:text-danger',
  consolidate: 'border-warning bg-warning-bg text-warning hover:border-warning hover:text-warning',
  extend: 'border-success bg-success-bg text-success hover:border-success hover:text-success',
  inferred: 'border-brand-100 bg-brand-50 text-brand-700 hover:border-brand-600',
}

/** Tên hoạt động sinh ra minh chứng, theo nguồn phiên luyện của server. */
const SOURCE_LABELS: Record<string, string> = {
  MOCK_EXAM: 'Thi thử',
  PLACEMENT: 'Kiểm tra trình độ',
  TOPIC_TEST: 'Kiểm tra chủ đề',
  TOPIC_PRACTICE: 'Luyện chủ đề',
  GRAMMAR_LESSON: 'Bài ngữ pháp',
  CHAR_RECOGNITION: 'Nhận diện chữ',
  TARGETED_PRACTICE: 'Luyện theo gợi ý',
  GAME_ROUND: 'Trò chơi',
  DUE_REVIEW: 'Ôn tập',
  GRADED_PRACTICE: 'Bài được chấm',
}

const EVIDENCE_CONTEXT_LABELS: Record<EvidenceContext, string> = {
  EXAM: 'Bài thi',
  QUIZ: 'Bài kiểm tra',
  GRADED: 'Bài được chấm',
  PRACTICE: 'Luyện tập',
}

const EVIDENCE_FORM_LABELS: Record<EvidenceForm, string> = {
  OPEN: 'Trả lời mở',
  MCQ4: 'Trắc nghiệm 4 lựa chọn',
  MCQ3: 'Trắc nghiệm 3 lựa chọn',
  BINARY: 'Đúng/Sai',
}

export interface MasteryProgressViewProps {
  detail?: MasteryNodeDetail
  detailError?: string
  detailLoading?: boolean
  isNewLearner: boolean
  knowledgeNodes: readonly KnowledgeNode[]
  masteryNodes: readonly MasteryNode[]
  onRetry: () => void
  onRetryDetail?: () => void
  onSelectKid: (kid: string) => void
  selectedKid?: string
  state: PageStateValue
}

function formatValue(value: number | null) {
  return value === null ? '—' : `${value}/100`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value)
}

function EvidenceRow({ evidence }: { evidence: EvidenceView }) {
  const source = SOURCE_LABELS[evidence.source] ?? EVIDENCE_CONTEXT_LABELS[evidence.context]

  return (
    <li className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-3">
      <p className="text-body-sm text-ink">
        {source} · {EVIDENCE_FORM_LABELS[evidence.form]}
      </p>
      <p className="text-caption text-ink-secondary">
        <span className={evidence.score === 100 ? 'text-success' : 'text-danger'}>
          {evidence.score === 100 ? 'Đúng' : 'Sai'}
        </span>{' '}
        · <time dateTime={evidence.answeredAt}>{formatMasteryDateTime(evidence.answeredAt)}</time>
      </p>
    </li>
  )
}

function NodeDetail({
  detail,
  knowledgeNodes,
}: {
  detail: MasteryNodeDetail
  knowledgeNodes: readonly KnowledgeNode[]
}) {
  const nameByKid = useMemo(
    () => new Map(knowledgeNodes.map((node) => [node.kid, node.nameVi])),
    [knowledgeNodes],
  )

  return (
    <Card
      aria-labelledby="mastery-detail-title"
      className="space-y-5 p-6"
      data-testid="mastery-detail"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-h3 text-ink" id="mastery-detail-title">
          {nameByKid.get(detail.kid) ?? detail.kid}
        </h2>
        <span
          className={`rounded-pill border px-3 py-1 text-caption font-semibold ${STATE_CLASSES[detail.state]}`}
        >
          {STATE_LABELS[detail.state]}
        </span>
      </div>

      <dl className="grid grid-cols-3 gap-4 border-y border-line py-4">
        <div>
          <dt className="text-caption text-ink-secondary">Mức thành thạo</dt>
          <dd className="mt-1 text-h3 tabular-nums text-ink">{formatValue(detail.value)}</dd>
        </div>
        <div>
          <dt className="text-caption text-ink-secondary">Độ tin cậy</dt>
          <dd className="mt-1 text-h3 tabular-nums text-ink">{formatNumber(detail.confidence)}</dd>
        </div>
        <div>
          <dt className="text-caption text-ink-secondary">Số câu đã làm</dt>
          <dd className="mt-1 text-h3 tabular-nums text-ink">{detail.evidenceCount}</dd>
        </div>
      </dl>

      <section aria-labelledby="mastery-evidence-title">
        <h3 className="text-label text-ink" id="mastery-evidence-title">
          Câu làm gần đây
        </h3>
        {detail.evidence.length ? (
          <ol aria-label="Danh sách minh chứng" className="divide-y divide-line">
            {detail.evidence.map((evidence, index) => (
              <EvidenceRow evidence={evidence} key={`${evidence.answeredAt}-${index}`} />
            ))}
          </ol>
        ) : (
          <p className="mt-2 text-body-sm text-ink-secondary">Chưa làm câu nào thuộc phần này.</p>
        )}
      </section>

      <section aria-labelledby="mastery-prereqs-title">
        <h3 className="text-label text-ink" id="mastery-prereqs-title">
          Cần học trước
        </h3>
        {detail.prereqs.length ? (
          <ul className="mt-2 flex flex-wrap gap-2">
            {detail.prereqs.map((prereq) => (
              <li
                className={`rounded-pill border px-3 py-1 text-caption ${STATE_CLASSES[prereq.state]}`}
                data-state={prereq.state}
                key={prereq.kid}
              >
                <span>{prereq.nameVi}</span> · {STATE_LABELS[prereq.state]}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-body-sm text-ink-secondary">Không cần học phần nào trước.</p>
        )}
      </section>

      <div className="space-y-2 border-t border-line pt-4">
        {detail.approvedQuestionCount === 0 ? (
          <>
            <Button disabled>Luyện phần này</Button>
            <p className="text-body-sm text-ink-secondary">Chưa có câu luyện cho phần này</p>
          </>
        ) : (
          <Button to={`/learn/practice/targeted?kid=${encodeURIComponent(detail.kid)}`}>
            Luyện phần này
            <ArrowRight aria-hidden="true" className="ml-1 inline-block size-4 align-middle" />
          </Button>
        )}
      </div>
    </Card>
  )
}

export function MasteryProgressView({
  detail,
  detailError,
  detailLoading = false,
  isNewLearner,
  knowledgeNodes,
  masteryNodes,
  onRetry,
  onRetryDetail,
  onSelectKid,
  selectedKid,
  state,
}: MasteryProgressViewProps) {
  const masteryByKid = useMemo(
    () => new Map(masteryNodes.map((node) => [node.kid, node])),
    [masteryNodes],
  )

  const visibleStates = useMemo(
    () => knowledgeNodes.map((node) => masteryByKid.get(node.kid)?.state ?? 'notyet'),
    [knowledgeNodes, masteryByKid],
  )
  const solidCount = visibleStates.filter((s) => s === 'extend' || s === 'inferred').length
  const reviewCount = visibleStates.filter((s) => s === 'review').length

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner
        subtitle="Mức thành thạo của bạn theo từng mảng và cấp HSK."
        title="Tiến độ học tập"
      />

      {state === 'loading' ? <Skeleton className="mt-6" variant="table" /> : null}

      {state === 'error' ? (
        <ErrorState message="Không tải được tiến độ học tập." onRetry={onRetry} />
      ) : null}

      {state === 'empty' ? (
        <EmptyState
          description="Chưa có nội dung HSK 1–3 để theo dõi."
          title="Chưa có dữ liệu tiến độ"
        />
      ) : null}

      {state === 'ready' ? (
        <div className="space-y-6">
          <div className="grid min-w-0 grid-cols-3 gap-3 sm:gap-4">
            <StatTile label="Nội dung HSK 1–3" value={knowledgeNodes.length} />
            <StatTile label="Đã vững" value={solidCount} />
            <StatTile label="Cần ôn" value={reviewCount} />
          </div>

          <div className="flex flex-wrap gap-3">
            <Button to="/learn/review">Ôn tập đến hạn</Button>
            <Button to="/learn" variant="outline">
              Về trang học tập
            </Button>
          </div>

          {isNewLearner ? (
            <Card className="border-brand-100 bg-brand-50 p-5" role="status">
              <p className="text-body text-ink">
                Bạn chưa có minh chứng — hãy làm bài kiểm tra trình độ hoặc luyện một chủ đề
              </p>
              <Link
                className="mt-2 inline-flex min-h-touch items-center text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                to="/learn/exams"
              >
                Làm bài kiểm tra trình độ
              </Link>
            </Card>
          ) : null}

          <section aria-label="Lưới tiến độ theo mảng và cấp HSK" className="min-w-0 space-y-3">
            <p className="text-body-sm text-ink-secondary">
              Bấm vào một ô để xem các câu bạn đã làm và luyện tiếp phần đó.
            </p>
            <table className="block w-full min-w-0" role="table">
              <caption className="sr-only">Tiến độ năng lực theo sáu mảng và ba cấp HSK</caption>
              <thead className="sr-only lg:not-sr-only lg:mb-2 lg:block" role="rowgroup">
                <tr className="grid gap-2 lg:grid-cols-7" role="row">
                  <th className="px-2 text-left text-label text-ink-secondary" role="columnheader" scope="col">
                    Cấp
                  </th>
                  {KNOWLEDGE_DOMAINS.map((domain) => (
                    <th
                      className="px-2 text-left text-label text-ink"
                      key={domain}
                      role="columnheader"
                      scope="col"
                    >
                      {DOMAIN_LABELS[domain]}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="block divide-y divide-line" role="rowgroup">
                {LEVELS.map((level) => {
                  const maxSlots = Math.max(
                    1,
                    ...KNOWLEDGE_DOMAINS.map(
                      (domain) =>
                        knowledgeNodes.filter(
                          (node) => node.domain === domain && node.hskLevel === level,
                        ).length,
                    ),
                  )
                  return (
                    <tr
                      className="grid min-w-0 gap-2 py-4 first:pt-0 last:pb-0 sm:grid-cols-2 lg:grid-cols-7 lg:items-stretch"
                      key={level}
                      role="row"
                    >
                      <th
                        className="px-2 text-left text-h3 text-brand-700 sm:col-span-2 lg:col-span-1"
                        role="rowheader"
                        scope="row"
                      >
                        HSK {level}
                      </th>
                      {KNOWLEDGE_DOMAINS.map((domain) => {
                        const cellNodes = knowledgeNodes.filter(
                          (node) => node.domain === domain && node.hskLevel === level,
                        )
                        return (
                          <td className="flex min-w-0 flex-col" key={`${domain}-${level}`} role="cell">
                            <p aria-hidden="true" className="mb-1 px-1 text-caption text-ink-secondary lg:hidden">
                              {DOMAIN_LABELS[domain]}
                            </p>
                            <ul
                              className="grid flex-1 gap-2"
                              style={{ gridTemplateRows: `repeat(${maxSlots}, minmax(0, 1fr))` }}
                            >
                              {cellNodes.map((node) => {
                                const mastery = masteryByKid.get(node.kid)!
                                const isSelected = selectedKid === node.kid
                                return (
                                  <li className="flex min-w-0" key={node.kid}>
                                    <Button
                                      aria-pressed={isSelected}
                                      className={`h-full min-h-20 w-full flex-col items-stretch justify-between whitespace-normal rounded-md border p-3 text-left text-body-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus [&>span]:flex [&>span]:h-full [&>span]:w-full [&>span]:flex-col [&>span]:justify-between ${STATE_CLASSES[mastery.state]} ${mastery.frontier ? 'border-2' : ''} ${isSelected ? 'shadow-sm ring-2 ring-brand-600' : ''}`}
                                      data-frontier={String(mastery.frontier)}
                                      data-kid={node.kid}
                                      data-state={mastery.state}
                                      onClick={() => onSelectKid(node.kid)}
                                      type="button"
                                      variant="outline"
                                    >
                                      <span className="block font-semibold leading-snug">
                                        {node.nameVi}
                                      </span>
                                      <span className="block pt-2 text-caption font-medium">
                                        {STATE_LABELS[mastery.state]}
                                      </span>
                                    </Button>
                                  </li>
                                )
                              })}
                            </ul>
                          </td>
                        )
                      })}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </section>

          <section
            aria-label="Chú giải trạng thái"
            className="flex flex-wrap items-center gap-2 border-t border-line pt-4"
          >
            {(Object.keys(STATE_LABELS) as MasteryState[]).map((masteryState) => (
              <span
                className={`rounded-pill border px-2.5 py-1 text-caption ${STATE_CLASSES[masteryState]}`}
                data-state={masteryState}
                key={masteryState}
              >
                {STATE_LABELS[masteryState]}
              </span>
            ))}
            <span className="ml-auto text-caption text-ink-secondary">Vững ≥ 80</span>
          </section>

          {selectedKid ? (
            <section aria-label="Chi tiết phần đã chọn" aria-live="polite">
              {detailLoading ? <Skeleton variant="card" /> : null}
              {detailError ? <ErrorState message={detailError} onRetry={onRetryDetail} /> : null}
              {detail && detail.kid === selectedKid ? (
                <NodeDetail detail={detail} knowledgeNodes={knowledgeNodes} />
              ) : null}
            </section>
          ) : null}
        </div>
      ) : null}
    </main>
  )
}
