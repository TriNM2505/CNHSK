import { useMemo } from 'react'
import { Link } from 'react-router-dom'

import type { KnowledgeDomain, KnowledgeNode } from '@/features/question-bank/types'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  PageHeader,
  Skeleton,
  type PageStateValue,
} from '@/shared/ui'

import type { EvidenceView, MasteryNode, MasteryNodeDetail, MasteryState } from '../types'

const DOMAINS: readonly KnowledgeDomain[] = ['NA', 'CH', 'TV', 'NP', 'NL', 'VB']
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
  notyet: 'border-line bg-canvas text-ink-secondary',
  insufficient: 'border-danger bg-danger-bg text-danger',
  review: 'border-danger bg-danger-bg text-danger',
  consolidate: 'border-warning bg-warning-bg text-warning',
  extend: 'border-success bg-success-bg text-success',
  inferred: 'border-brand-100 bg-brand-50 text-brand-700',
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

function masteryFor(kid: string, byKid: ReadonlyMap<string, MasteryNode>): MasteryNode {
  return (
    byKid.get(kid) ?? {
      kid,
      value: null,
      confidence: 0,
      evidenceCount: 0,
      state: 'notyet',
      frontier: false,
      dueAt: null,
    }
  )
}

function formatValue(value: number | null) {
  return value === null ? '—' : `${value}/100`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value)
}

function EvidenceRow({ evidence }: { evidence: EvidenceView }) {
  return (
    <li className="border-b border-line py-3 last:border-0">
      <p className="text-body-sm text-ink">
        {evidence.source} · {evidence.context} · {evidence.form} · {evidence.role}
      </p>
      <p className="mt-1 text-caption text-ink-secondary">
        {evidence.score}/100 · trọng số {formatNumber(evidence.weight)} · {evidence.answeredAt}
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
  const nameByKid = useMemo(() => new Map(knowledgeNodes.map((node) => [node.kid, node.nameVi])), [knowledgeNodes])

  return (
    <Card aria-labelledby="mastery-detail-title" className="space-y-4" data-testid="mastery-detail">
      <div>
        <h2 className="text-h3 text-ink" id="mastery-detail-title">
          {nameByKid.get(detail.kid) ?? detail.kid}
        </h2>
        <p className="mt-1 text-caption text-ink-secondary">{detail.kid}</p>
      </div>

      <dl className="grid gap-3 sm:grid-cols-3">
        <div>
          <dt className="text-caption text-ink-secondary">Điểm</dt>
          <dd className="text-label text-ink">{formatValue(detail.value)}</dd>
        </div>
        <div>
          <dt className="text-caption text-ink-secondary">Độ tin cậy</dt>
          <dd className="text-label text-ink">{formatNumber(detail.confidence)}</dd>
        </div>
        <div>
          <dt className="text-caption text-ink-secondary">Số minh chứng</dt>
          <dd className="text-label text-ink">{detail.evidenceCount}</dd>
        </div>
      </dl>

      <section aria-labelledby="mastery-evidence-title">
        <h3 className="text-label text-ink" id="mastery-evidence-title">Minh chứng</h3>
        {detail.evidence.length ? (
          <ol aria-label="Danh sách minh chứng" className="mt-2 divide-y divide-line">
            {detail.evidence.map((evidence, index) => (
              <EvidenceRow evidence={evidence} key={`${evidence.answeredAt}-${index}`} />
            ))}
          </ol>
        ) : (
          <p className="mt-2 text-body-sm text-ink-secondary">Chưa có minh chứng.</p>
        )}
      </section>

      <section aria-labelledby="mastery-prereqs-title">
        <h3 className="text-label text-ink" id="mastery-prereqs-title">Tiên quyết</h3>
        {detail.prereqs.length ? (
          <ul className="mt-2 space-y-2">
            {detail.prereqs.map((prereq) => (
              <li className="flex flex-wrap items-center gap-2" key={prereq.kid}>
                <span className="text-body-sm text-ink">{prereq.nameVi}</span>
                <span
                  className={`rounded-pill border px-2 py-1 text-caption ${STATE_CLASSES[prereq.state]}`}
                  data-state={prereq.state}
                >
                  {STATE_LABELS[prereq.state]}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-body-sm text-ink-secondary">Không có tiên quyết.</p>
        )}
      </section>

      <div className="space-y-2">
        {detail.approvedQuestionCount === 0 ? (
          <Button disabled>Luyện nút này</Button>
        ) : (
          <Button to={`/learn/practice/targeted?kid=${encodeURIComponent(detail.kid)}`}>
            Luyện nút này
          </Button>
        )}
        {detail.approvedQuestionCount === 0 ? (
          <p className="text-body-sm text-ink-secondary">Chưa có câu luyện cho nút này</p>
        ) : null}
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
  const masteryByKid = useMemo(() => new Map(masteryNodes.map((node) => [node.kid, node])), [masteryNodes])

  return (
    <main className="mx-auto w-full max-w-screen-xl px-4 py-6 sm:px-6 lg:px-8">
      <PageHeader
        subtitle="Theo dõi mức độ thành thạo theo miền và cấp HSK."
        title="Tiến độ học tập"
      />

      {state === 'loading' ? <Skeleton className="mt-6" variant="table" /> : null}

      {state === 'error' ? (
        <ErrorState message="Không tải được tiến độ học tập." onRetry={onRetry} />
      ) : null}

      {state === 'empty' ? (
        <EmptyState
          description="Chưa có nút năng lực ở các cấp HSK 1–3."
          title="Chưa có dữ liệu tiến độ"
        />
      ) : null}

      {state === 'ready' ? (
        <div className="space-y-6">
          {isNewLearner ? (
            <Card className="border-info bg-info-bg" role="status">
              <p className="text-body text-ink">
                Bạn chưa có minh chứng — hãy làm bài kiểm tra trình độ hoặc luyện một chủ đề
              </p>
              <Link className="mt-2 inline-block text-label text-brand-700 underline" to="/learn/exams">
                Làm bài kiểm tra trình độ
              </Link>
            </Card>
          ) : null}

          <section aria-label="Lưới tiến độ theo miền và cấp HSK" className="overflow-x-auto">
            <table className="min-w-[58rem] border-separate border-spacing-2">
              <caption className="sr-only">Tiến độ năng lực theo sáu miền và ba cấp HSK</caption>
              <thead>
                <tr>
                  <th className="p-2 text-left text-label text-ink" scope="col">HSK</th>
                  {DOMAINS.map((domain) => (
                    <th className="p-2 text-center text-label text-ink" key={domain} scope="col">
                      {domain}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {LEVELS.map((level) => (
                  <tr key={level}>
                    <th className="p-2 text-left text-label text-ink" scope="row">{level}</th>
                    {DOMAINS.map((domain) => {
                      const cellNodes = knowledgeNodes.filter(
                        (node) => node.domain === domain && node.hskLevel === level,
                      )
                      return (
                        <td className="min-w-36 align-top" key={`${domain}-${level}`}>
                          <ul className="space-y-2">
                            {cellNodes.map((node) => {
                              const mastery = masteryFor(node.kid, masteryByKid)
                              const isSelected = selectedKid === node.kid
                              return (
                                <li key={node.kid}>
                                  <button
                                    aria-pressed={isSelected}
                                    className={`w-full rounded-md border p-3 text-left text-body-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${STATE_CLASSES[mastery.state]} ${mastery.frontier ? 'ring-2 ring-focus' : ''} ${isSelected ? 'shadow-sm' : ''}`}
                                    data-frontier={String(mastery.frontier)}
                                    data-kid={node.kid}
                                    data-state={mastery.state}
                                    onClick={() => onSelectKid(node.kid)}
                                    type="button"
                                  >
                                    <span className="block font-medium">{node.nameVi}</span>
                                    <span className="mt-1 block text-caption">{node.kid}</span>
                                    <span className="mt-1 block text-caption">{STATE_LABELS[mastery.state]}</span>
                                  </button>
                                </li>
                              )
                            })}
                          </ul>
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section aria-label="Chú giải trạng thái" className="flex flex-wrap items-center gap-2">
            {(Object.keys(STATE_LABELS) as MasteryState[]).map((masteryState) => (
              <span
                className={`rounded-pill border px-2 py-1 text-caption ${STATE_CLASSES[masteryState]}`}
                data-state={masteryState}
                key={masteryState}
              >
                {STATE_LABELS[masteryState]}
              </span>
            ))}
            <span className="text-caption text-ink-secondary">Vững ≥ 80</span>
          </section>

          {selectedKid ? (
            <section aria-label="Chi tiết nút năng lực" aria-live="polite">
              {detailLoading ? <Skeleton variant="card" /> : null}
              {detailError ? (
                <ErrorState message={detailError} onRetry={onRetryDetail} />
              ) : null}
              {detail && detail.kid === selectedKid ? (
                <NodeDetail detail={detail} knowledgeNodes={knowledgeNodes} />
              ) : null}
            </section>
          ) : (
            <p className="text-body-sm text-ink-secondary">Chọn một nút để xem chi tiết.</p>
          )}
        </div>
      ) : null}
    </main>
  )
}
