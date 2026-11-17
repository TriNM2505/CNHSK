import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  BookOpen,
  CheckCircle2,
  Clock3,
  Compass,
  FileText,
  Headphones,
  Languages,
  Layers,
  Pencil,
  PlayCircle,
  Sparkles,
  UserRound,
  type LucideIcon,
} from 'lucide-react'
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

import { formatMasteryDateTime } from '../formatDateTime'
import type {
  EvidenceContext,
  EvidenceForm,
  EvidenceRole,
  EvidenceView,
  MasteryNode,
  MasteryNodeDetail,
  MasteryState,
} from '../types'

const DOMAINS: readonly KnowledgeDomain[] = ['NA', 'CH', 'TV', 'NP', 'NL', 'VB']
const LEVELS = [1, 2, 3] as const

const DOMAIN_ICONS: Record<KnowledgeDomain, LucideIcon> = {
  NA: Headphones,
  CH: Languages,
  TV: BookOpen,
  NP: UserRound,
  NL: FileText,
  VB: Pencil,
}

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

const STATE_ICONS: Record<MasteryState, LucideIcon> = {
  notyet: Clock3,
  insufficient: AlertCircle,
  review: PlayCircle,
  consolidate: BookOpen,
  extend: CheckCircle2,
  inferred: PlayCircle,
}

const EVIDENCE_CONTEXT_LABELS: Record<EvidenceContext, string> = {
  EXAM: 'Bài thi',
  QUIZ: 'Bài kiểm tra',
  GRADED: 'Bài có chấm điểm',
  PRACTICE: 'Luyện tập',
}

const EVIDENCE_FORM_LABELS: Record<EvidenceForm, string> = {
  OPEN: 'Trả lời mở',
  MCQ4: 'Trắc nghiệm 4 lựa chọn',
  MCQ3: 'Trắc nghiệm 3 lựa chọn',
  BINARY: 'Đúng/Sai',
}

const EVIDENCE_ROLE_LABELS: Record<EvidenceRole, string> = {
  PRIMARY: 'Chính',
  SECONDARY: 'Phụ',
  ERROR: 'Lỗi thường gặp',
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

function ChineseLandscapeBannerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-3/5 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMid slice"
      viewBox="0 0 680 132"
    >
      <defs>
        <linearGradient id="cnhsk-prog-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-prog-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-prog-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <circle cx="518" cy="34" fill="currentColor" opacity="0.2" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-prog-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-prog-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-prog-hill-front)"
      />
      <path
        d="M390 132 C456 104, 520 98, 585 112 C622 104, 654 106, 680 118 L680 132 Z"
        fill="currentColor"
        opacity="0.22"
      />

      <path
        d="M284 132 C328 96, 376 70, 428 72 C474 74, 514 104, 562 102 C598 100, 632 88, 676 94 L676 106 C632 100, 598 112, 562 114 C512 116, 472 86, 428 84 C378 82, 334 106, 298 132 Z"
        fill="currentColor"
        opacity="0.32"
      />
      <path
        d="M282 132 C326 94, 376 68, 428 70 C474 72, 514 102, 562 100 C598 98, 632 86, 676 92"
        opacity="0.58"
        stroke="currentColor"
        strokeDasharray="5 3.5"
        strokeWidth="4.5"
      />
      <path
        d="M294 132 C336 102, 380 78, 428 80 C474 82, 514 112, 562 110 C598 108, 632 96, 676 102"
        opacity="0.45"
        stroke="currentColor"
        strokeWidth="2"
      />

      <g opacity="0.55" transform="translate(506, 70) scale(0.58)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g opacity="0.5" transform="translate(594, 78) scale(0.62)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g className="text-brand-700" opacity="0.95" transform="translate(426, 26)">
        <path d="M28 0 L31 8 L25 8 Z" fill="currentColor" />
        <path
          d="M6 19 C15 17, 22 12, 28 6 C34 12, 41 17, 50 19 C42 21, 35 18, 28 15 C21 18, 14 21, 6 19 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="6" width="22" x="17" y="17" />
        <path
          d="M0 31 C12 28, 21 23, 28 18 C35 23, 44 28, 56 31 C45 33, 36 30, 28 27 C20 30, 11 33, 0 31 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="15" width="3" x="12" y="29" />
        <rect fill="currentColor" height="15" width="3" x="20" y="29" />
        <rect fill="currentColor" height="15" width="3" x="33" y="29" />
        <rect fill="currentColor" height="15" width="3" x="41" y="29" />
        <rect fill="currentColor" height="2.5" width="34" x="11" y="39" />
        <path d="M7 44 L49 44 L52 49 L4 49 Z" fill="currentColor" />
      </g>

      <g className="text-brand-700" opacity="0.92">
        <path d="M634 134 L600 -8" stroke="currentColor" strokeWidth="6" />
        <line stroke="white" strokeWidth="2" x1="622" x2="630" y1="96" y2="94" />
        <line stroke="white" strokeWidth="2" x1="613" x2="621" y1="58" y2="56" />
        <line stroke="white" strokeWidth="2" x1="604" x2="612" y1="20" y2="18" />

        <path d="M654 134 L634 -8" stroke="currentColor" strokeWidth="8.5" />
        <line stroke="white" strokeWidth="2.2" x1="644" x2="654" y1="102" y2="100" />
        <line stroke="white" strokeWidth="2.2" x1="639" x2="649" y1="64" y2="62" />
        <line stroke="white" strokeWidth="2.2" x1="634" x2="644" y1="26" y2="24" />

        <path d="M672 134 L662 -8" stroke="currentColor" strokeWidth="6.5" />
        <line stroke="white" strokeWidth="2" x1="664" x2="673" y1="86" y2="85" />
        <line stroke="white" strokeWidth="2" x1="661" x2="670" y1="46" y2="45" />

        <path
          d="M636 10 Q585 6 542 26"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="2"
        />
        <path
          d="M612 36 Q576 42 548 62"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="1.8"
        />

        <path d="M558 18 Q540 32 528 50 Q548 38 558 18 Z" fill="currentColor" />
        <path d="M570 14 Q558 36 550 58 Q568 40 570 14 Z" fill="currentColor" />
        <path d="M584 12 Q578 34 574 54 Q588 36 584 12 Z" fill="currentColor" />
        <path d="M554 20 Q534 20 518 28 Q540 28 554 20 Z" fill="currentColor" opacity="0.85" />

        <path d="M562 52 Q546 66 536 82 Q554 70 562 52 Z" fill="currentColor" />
        <path d="M576 46 Q566 66 560 86 Q576 68 576 46 Z" fill="currentColor" />
        <path d="M590 42 Q586 60 584 78 Q596 62 590 42 Z" fill="currentColor" />
        <path d="M556 54 Q538 56 524 64 Q544 64 556 54 Z" fill="currentColor" opacity="0.8" />

        <path d="M596 8 Q586 24 582 40 Q596 28 596 8 Z" fill="currentColor" />
        <path d="M618 62 Q606 76 600 92 Q614 80 618 62 Z" fill="currentColor" />
        <path d="M628 58 Q622 76 620 92 Q632 76 628 58 Z" fill="currentColor" />
      </g>
    </svg>
  )
}

function LaurelBranch({ flip = false }: { flip?: boolean }) {
  return (
    <svg
      aria-hidden="true"
      className={`hidden h-9 w-5 shrink-0 text-brand-600 opacity-80 lg:block ${flip ? '-scale-x-100' : ''}`}
      fill="currentColor"
      viewBox="0 0 24 40"
    >
      {/* Cuống cành cong nhẹ */}
      <path
        d="M17 35 C9 27, 8 15, 14 5"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.4"
      />
      {/* Các cặp lá nguyệt quế tách rời dọc theo cành */}
      {/* Cặp lá 1 (dưới cùng) */}
      <path d="M14 31 C9 31, 6 28, 7 25 C10 26, 13 28, 14 31 Z" />
      <path d="M15 30 C18 28, 20 25, 19 22 C16 24, 15 27, 15 30 Z" />
      {/* Cặp lá 2 */}
      <path d="M11.5 24.5 C6.5 24, 4 21, 5.5 18 C8.5 19, 11 21.5, 11.5 24.5 Z" />
      <path d="M12.5 23.5 C16 21.5, 18 18.5, 17 15.5 C14.5 17.5, 13 20.5, 12.5 23.5 Z" />
      {/* Cặp lá 3 */}
      <path d="M10.5 17.5 C6 16.5, 4 13.5, 6 10.5 C8.5 12, 10.5 14.5, 10.5 17.5 Z" />
      <path d="M11.5 16.5 C15 15, 17 12, 16.5 9 C14 11, 12 13.5, 11.5 16.5 Z" />
      {/* Cặp lá 4 */}
      <path d="M11.5 10.5 C8 9, 7 6, 9 3.5 C11 5.5, 12 8, 11.5 10.5 Z" />
      {/* Lá đỉnh ngọn */}
      <path d="M13.5 6.5 C13 3.5, 15 1.5, 17.5 1.5 C17 4, 15.5 6, 13.5 6.5 Z" />
    </svg>
  )
}

function LevelCellArt({ level }: { level: number }) {
  return (
    <div className="relative flex h-full min-h-28 w-full flex-col items-center justify-center overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-3 py-5 text-brand-700">
      {/* Vòng nguyệt quế + số cấp HSK */}
      <div className="relative z-nav mb-2 flex items-center justify-center gap-1.5">
        <LaurelBranch />
        <span className="text-h1 font-bold leading-none text-brand-700">
          <span className="lg:hidden">HSK </span>
          {level}
        </span>
        <LaurelBranch flip />
      </div>

      {/* Tranh thủy mặc núi đỏ & đình cổ ở chân ô HSK */}
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 bottom-0 hidden h-12 w-full text-brand-600 lg:block"
        fill="none"
        preserveAspectRatio="none"
        viewBox="0 0 120 48"
      >
        <path
          d="M0 48 L24 26 L46 38 L76 18 L104 34 L120 26 L120 48 Z"
          fill="currentColor"
          opacity="0.14"
        />
        <path d="M0 48 Q38 30 78 36 Q102 32 120 40 L120 48 Z" fill="currentColor" opacity="0.24" />
        <g className="text-brand-700" opacity="0.55" transform="translate(68, 12) scale(0.45)">
          <path d="M28 0 L31 8 L25 8 Z" fill="currentColor" />
          <path
            d="M6 19 C15 17, 22 12, 28 6 C34 12, 41 17, 50 19 C42 21, 35 18, 28 15 C21 18, 14 21, 6 19 Z"
            fill="currentColor"
          />
          <rect fill="currentColor" height="6" width="22" x="17" y="17" />
          <path
            d="M0 31 C12 28, 21 23, 28 18 C35 23, 44 28, 56 31 C45 33, 36 30, 28 27 C20 30, 11 33, 0 31 Z"
            fill="currentColor"
          />
          <rect fill="currentColor" height="14" width="3.5" x="13" y="29" />
          <rect fill="currentColor" height="14" width="3.5" x="22" y="29" />
          <rect fill="currentColor" height="14" width="3.5" x="31" y="29" />
          <rect fill="currentColor" height="14" width="3.5" x="40" y="29" />
          <path d="M7 43 L49 43 L52 48 L4 48 Z" fill="currentColor" />
        </g>
      </svg>
    </div>
  )
}

function EvidenceRow({ evidence }: { evidence: EvidenceView }) {
  return (
    <li className="border-b border-line py-3 last:border-0">
      <p className="text-body-sm text-ink">
        {evidence.source} · {EVIDENCE_CONTEXT_LABELS[evidence.context]} ·{' '}
        {EVIDENCE_FORM_LABELS[evidence.form]} · {EVIDENCE_ROLE_LABELS[evidence.role]}
      </p>
      <p className="mt-1 text-caption text-ink-secondary">
        {evidence.score}/100 · trọng số {formatNumber(evidence.weight)} ·{' '}
        <time dateTime={evidence.answeredAt}>{formatMasteryDateTime(evidence.answeredAt)}</time>
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
      className="space-y-5 border-brand-100 bg-surface p-6"
      data-testid="mastery-detail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line pb-4">
        <div>
          <h2 className="text-h3 text-ink" id="mastery-detail-title">
            {nameByKid.get(detail.kid) ?? detail.kid}
          </h2>
          <p className="mt-1 text-caption text-ink-secondary">{detail.kid}</p>
        </div>
        <span
          className={`rounded-pill border px-3 py-1 text-caption font-semibold ${STATE_CLASSES[detail.state]}`}
        >
          {STATE_LABELS[detail.state]}
        </span>
      </div>

      <dl className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-md border border-brand-100 bg-brand-50 p-3.5">
          <dt className="text-caption text-ink-secondary">Điểm</dt>
          <dd className="mt-1 text-h3 text-ink">{formatValue(detail.value)}</dd>
        </div>
        <div className="rounded-md border border-brand-100 bg-brand-50 p-3.5">
          <dt className="text-caption text-ink-secondary">Độ tin cậy</dt>
          <dd className="mt-1 text-h3 text-ink">{formatNumber(detail.confidence)}</dd>
        </div>
        <div className="rounded-md border border-brand-100 bg-brand-50 p-3.5">
          <dt className="text-caption text-ink-secondary">Số minh chứng</dt>
          <dd className="mt-1 text-h3 text-ink">{detail.evidenceCount}</dd>
        </div>
      </dl>

      <section aria-labelledby="mastery-evidence-title">
        <h3 className="text-label text-ink" id="mastery-evidence-title">
          Minh chứng
        </h3>
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
        <h3 className="text-label text-ink" id="mastery-prereqs-title">
          Tiên quyết
        </h3>
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

      <div className="space-y-2 border-t border-line pt-4">
        {detail.approvedQuestionCount === 0 ? (
          <Button disabled>Luyện nút này</Button>
        ) : (
          <Button to={`/learn/practice/targeted?kid=${encodeURIComponent(detail.kid)}`}>
            Luyện nút này{' '}
            <ArrowRight aria-hidden="true" className="ml-1 inline-block size-4 align-middle" />
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
  const masteryByKid = useMemo(
    () => new Map(masteryNodes.map((node) => [node.kid, node])),
    [masteryNodes],
  )

  const visibleStates = useMemo(
    () => knowledgeNodes.map((node) => masteryByKid.get(node.kid)?.state ?? 'notyet'),
    [knowledgeNodes, masteryByKid],
  )

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          subtitle="Theo dõi mức độ thành thạo theo miền và cấp HSK."
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>Tiến độ học tập</span>
            </span>
          }
        />
        <ChineseLandscapeBannerArt />
      </div>

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
          <div className="grid min-w-0 gap-4 sm:grid-cols-3">
            <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex min-w-0 items-start gap-3.5">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
                  <Layers aria-hidden="true" className="size-5" />
                </div>
                <div className="min-w-0">
                  <p className="text-h1 tabular-nums text-ink">{knowledgeNodes.length}</p>
                  <p className="mt-0.5 text-label text-ink">Nút năng lực</p>
                  <p className="mt-0.5 text-body-sm text-ink-secondary">Phạm vi HSK 1–3</p>
                </div>
              </div>
              <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
                Tổng số
              </span>
            </Card>

            <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex min-w-0 items-start gap-3.5">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-success">
                  <CheckCircle2 aria-hidden="true" className="size-5" />
                </div>
                <div className="min-w-0">
                  <p className="text-h1 tabular-nums text-ink">
                    {visibleStates.filter((s) => s === 'extend' || s === 'inferred').length}
                  </p>
                  <p className="mt-0.5 text-label text-ink">Vững</p>
                  <p className="mt-0.5 text-body-sm text-ink-secondary">Đã đạt chuẩn thành thạo</p>
                </div>
              </div>
              <span className="shrink-0 rounded-pill bg-success-bg px-3 py-1 text-caption font-semibold text-success">
                Thành thạo
              </span>
            </Card>

            <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex min-w-0 items-start gap-3.5">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
                  <AlertTriangle aria-hidden="true" className="size-5" />
                </div>
                <div className="min-w-0">
                  <p className="text-h1 tabular-nums text-ink">
                    {visibleStates.filter((s) => s === 'review').length}
                  </p>
                  <p className="mt-0.5 text-label text-ink">Cần ôn</p>
                  <p className="mt-0.5 text-body-sm text-ink-secondary">Ưu tiên củng cố lại</p>
                </div>
              </div>
              <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
                Ưu tiên
              </span>
            </Card>
          </div>

          <Card className="flex flex-wrap items-center justify-between gap-4 border-brand-100 bg-brand-50 p-5">
            <div className="flex items-center gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                <Compass aria-hidden="true" className="size-5" />
              </div>
              <div>
                <p className="text-label text-ink">Điều hướng học tập</p>
                <p className="text-body-sm text-ink-secondary">
                  Chọn một nút trong lưới bên dưới để xem minh chứng và luyện tập.
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button to="/learn/review">
                Ôn tập đến hạn{' '}
                <ArrowRight aria-hidden="true" className="ml-1 inline-block size-4 align-middle" />
              </Button>
              <Button to="/learn" variant="outline">
                Về trang học tập
              </Button>
            </div>
          </Card>

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

          <section
            aria-label="Lưới tiến độ theo miền và cấp HSK"
            className="min-w-0 rounded-lg border border-brand-100 bg-surface p-4 sm:p-5"
          >
            <table className="block w-full min-w-0" role="table">
              <caption className="sr-only">Tiến độ năng lực theo sáu miền và ba cấp HSK</caption>
              <thead className="sr-only lg:not-sr-only lg:mb-3 lg:block" role="rowgroup">
                <tr className="grid gap-3 lg:grid-cols-7" role="row">
                  <th
                    className="flex items-center justify-center rounded-md border border-brand-100 bg-brand-50 px-3 py-2.5 text-center text-label font-bold text-ink"
                    role="columnheader"
                    scope="col"
                  >
                    HSK
                  </th>
                  {DOMAINS.map((domain) => {
                    const DomainIcon = DOMAIN_ICONS[domain]
                    return (
                      <th
                        className="flex items-center justify-center gap-2 rounded-md border border-brand-100 bg-brand-50 px-3 py-2.5 text-center text-label font-bold text-brand-700"
                        key={domain}
                        role="columnheader"
                        scope="col"
                      >
                        <DomainIcon aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                        <span>{domain}</span>
                      </th>
                    )
                  })}
                </tr>
              </thead>
              <tbody className="block divide-y divide-brand-100" role="rowgroup">
                {LEVELS.map((level) => {
                  const maxSlots = Math.max(
                    1,
                    ...DOMAINS.map(
                      (domain) =>
                        knowledgeNodes.filter(
                          (node) => node.domain === domain && node.hskLevel === level,
                        ).length,
                    ),
                  )
                  return (
                    <tr
                      className="grid min-w-0 gap-3 py-4 first:pt-0 last:pb-0 sm:grid-cols-2 lg:grid-cols-7 lg:items-stretch"
                      key={level}
                      role="row"
                    >
                      <th
                        className="block sm:col-span-2 lg:col-span-1"
                        role="rowheader"
                        scope="row"
                      >
                        <LevelCellArt level={level} />
                      </th>
                      {DOMAINS.map((domain) => {
                        const cellNodes = knowledgeNodes.filter(
                          (node) => node.domain === domain && node.hskLevel === level,
                        )
                        return (
                          <td
                            className="flex min-w-0 flex-col rounded-md bg-canvas p-2.5 lg:rounded-none lg:bg-transparent lg:p-0"
                            key={`${domain}-${level}`}
                            role="cell"
                          >
                            <p
                              aria-hidden="true"
                              className="mb-2 text-label text-brand-700 lg:hidden"
                            >
                              {domain}
                            </p>
                            <ul
                              className="grid flex-1 gap-2.5"
                              style={{
                                gridTemplateRows: `repeat(${maxSlots}, minmax(0, 1fr))`,
                              }}
                            >
                              {cellNodes.map((node) => {
                                const mastery = masteryByKid.get(node.kid)!
                                const isSelected = selectedKid === node.kid
                                const StateIcon = STATE_ICONS[mastery.state]
                                return (
                                  <li className="flex min-w-0" key={node.kid}>
                                    <Button
                                      variant="outline"
                                      aria-pressed={isSelected}
                                      className={`h-full min-h-28 w-full flex-col items-stretch justify-start whitespace-normal rounded-md border p-3 text-left text-body-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus [&>span]:flex [&>span]:h-full [&>span]:w-full [&>span]:flex-col ${STATE_CLASSES[mastery.state]} ${mastery.frontier ? 'border-2' : ''} ${isSelected ? 'ring-2 ring-brand-600 shadow-sm' : ''}`}
                                      data-frontier={String(mastery.frontier)}
                                      data-kid={node.kid}
                                      data-state={mastery.state}
                                      onClick={() => onSelectKid(node.kid)}
                                      type="button"
                                    >
                                      <span className="block font-semibold leading-snug">
                                        {node.nameVi}
                                      </span>
                                      <span className="mt-1 block text-caption opacity-85">
                                        {node.kid}
                                      </span>
                                      <span className="mt-auto flex items-center gap-1.5 pt-2 text-caption font-medium">
                                        <StateIcon
                                          aria-hidden="true"
                                          className="size-3.5 shrink-0"
                                        />
                                        <span>{STATE_LABELS[mastery.state]}</span>
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
            className="flex flex-wrap items-center gap-2 rounded-md border border-line bg-surface p-4"
          >
            <span className="mr-1 inline-flex items-center gap-1.5 text-caption font-semibold text-ink">
              <Sparkles aria-hidden="true" className="size-3.5 text-brand-600" />
              Trạng thái:
            </span>
            {(Object.keys(STATE_LABELS) as MasteryState[]).map((masteryState) => (
              <span
                className={`rounded-pill border px-2.5 py-1 text-caption ${STATE_CLASSES[masteryState]}`}
                data-state={masteryState}
                key={masteryState}
              >
                {STATE_LABELS[masteryState]}
              </span>
            ))}
            <span className="ml-auto text-caption font-semibold text-brand-700">Vững ≥ 80</span>
          </section>

          {selectedKid ? (
            <section aria-label="Chi tiết nút năng lực" aria-live="polite">
              {detailLoading ? <Skeleton variant="card" /> : null}
              {detailError ? <ErrorState message={detailError} onRetry={onRetryDetail} /> : null}
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
