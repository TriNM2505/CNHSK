import {
  ArrowRight,
  BarChart3,
  CheckCircle2,
  Circle,
  CircleDot,
  ClipboardCheck,
  FileCheck2,
  FileMinus2,
  FileX2,
  GraduationCap,
  Info,
  ListChecks,
  MinusCircle,
  RotateCcw,
  Target,
  Trophy,
  XCircle,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button, Card, PageHeader, Progress } from '@/shared/ui'
import type { ExamResult, ResultItem, WeakPoint } from '../types'

const filters = { ALL: 'Tất cả', WRONG: 'Sai', BLANK: 'Bỏ trống' } as const
const labels = { CORRECT: 'Đúng', WRONG: 'Sai', BLANK: 'Bỏ trống' } as const
const resultTone = {
  CORRECT: 'text-success',
  WRONG: 'text-danger',
  BLANK: 'text-warning',
} as const
const resultCardClass = {
  CORRECT: 'border-success bg-success-bg/35',
  WRONG: 'border-danger bg-brand-50/60',
  BLANK: 'border-warning bg-warning-bg/45',
} as const

export type ResultFilter = keyof typeof filters
type WeakPointsState =
  | { state: 'loading' }
  | { state: 'error'; onRetry: () => void }
  | { state: 'ready'; points: WeakPoint[] }

function answerText(item: ResultItem, key: string) {
  const option = item.options.find((entry) => entry.key === key)
  return option ? key + '. ' + option.text : key
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
        <linearGradient id="cnhsk-examlist-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-examlist-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-examlist-hill-front" x1="0" x2="0" y1="0" y2="1">
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
        fill="url(#cnhsk-examlist-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-examlist-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-examlist-hill-front)"
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
        d="M282 132 C326 94, 376 68, 428 70 C474 72, 514 104, 562 100 C598 98, 632 86, 676 92"
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

function CardCornerArt({ result }: { result: ResultItem['result'] }) {
  if (result === 'CORRECT') {
    return (
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute bottom-0 right-0 h-24 w-44 text-success opacity-55"
        fill="none"
        viewBox="0 0 170 96"
      >
        <path d="M154 98 L132 8" stroke="currentColor" strokeWidth="4" />
        <line stroke="white" strokeWidth="1.5" x1="142" x2="150" y1="62" y2="60" />
        <line stroke="white" strokeWidth="1.5" x1="136" x2="144" y1="32" y2="30" />
        <path d="M166 98 L152 24" stroke="currentColor" strokeWidth="3" />
        <path d="M140 36 Q108 30 80 46" stroke="currentColor" strokeWidth="1.6" />
        <path d="M96 38 Q78 50 66 66 Q86 54 96 38 Z" fill="currentColor" />
        <path d="M110 34 Q96 52 88 70 Q106 54 110 34 Z" fill="currentColor" />
        <path d="M124 34 Q118 52 116 68 Q128 52 124 34 Z" fill="currentColor" />
        <path d="M90 40 Q72 40 58 48 Q78 48 90 40 Z" fill="currentColor" opacity="0.8" />
        <path d="M44 96 C82 70, 122 66, 170 84 L170 96 Z" fill="currentColor" opacity="0.25" />
      </svg>
    )
  }

  return (
    <svg
      aria-hidden="true"
      className={`pointer-events-none absolute bottom-0 right-0 h-24 w-48 ${
        result === 'WRONG' ? 'text-brand-600' : 'text-warning'
      }`}
      fill="none"
      preserveAspectRatio="xMaxYMax meet"
      viewBox="0 0 190 92"
    >
      <path
        d="M10 92 C54 60, 92 54, 136 76 C158 56, 176 52, 190 68 L190 92 Z"
        fill="currentColor"
        opacity="0.18"
      />
      <path d="M72 92 C114 62, 152 54, 190 76 L190 92 Z" fill="currentColor" opacity="0.28" />
      {result === 'WRONG' ? (
        <g className="text-brand-700" opacity="0.65" transform="translate(136, 28) scale(0.58)">
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
          <rect fill="currentColor" height="14" width="3" x="12" y="29" />
          <rect fill="currentColor" height="14" width="3" x="21" y="29" />
          <rect fill="currentColor" height="14" width="3" x="32" y="29" />
          <rect fill="currentColor" height="14" width="3" x="41" y="29" />
          <path d="M7 43 L49 43 L52 48 L4 48 Z" fill="currentColor" />
        </g>
      ) : null}
    </svg>
  )
}



function KidScrollCornerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute bottom-0 right-0 h-24 w-48 text-brand-600"
      fill="none"
      preserveAspectRatio="xMaxYMax meet"
      viewBox="0 0 200 96"
    >
      <path
        d="M24 96 C68 66, 112 62, 156 82 C174 68, 188 64, 200 74 L200 96 Z"
        fill="currentColor"
        opacity="0.15"
      />
      {/* Cuộn thư cổ viết chữ 汉 & bút lông */}
      <g transform="translate(128, 14) rotate(12)">
        {/* Thân giấy cuộn */}
        <rect
          className="fill-warning-bg stroke-warning"
          height="46"
          rx="3"
          strokeWidth="1.5"
          width="36"
          x="6"
          y="4"
        />
        {/* Trục gỗ trên & dưới */}
        <rect className="fill-brand-700" height="4" rx="2" width="44" x="2" y="2" />
        <rect className="fill-brand-700" height="4" rx="2" width="44" x="2" y="48" />
        {/* Chữ 汉 trên cuộn thư */}
        <text
          className="fill-brand-700 font-hanzi"
          fontSize="18"
          fontWeight="bold"
          textAnchor="middle"
          x="24"
          y="33"
        >
          汉
        </text>
        {/* Cây bút lông bên phải */}
        <path
          className="stroke-brand-800"
          d="M48 8 L38 42"
          strokeLinecap="round"
          strokeWidth="2.5"
        />
        <path className="fill-brand-700" d="M38 42 L35 50 L41 44 Z" />
      </g>
    </svg>
  )
}

function ScoreRing({ percent }: { percent: number }) {
  const value = Math.min(Math.max(percent, 0), 100)
  return (
    <div className="relative mx-auto size-44">
      {/* Áng mây đỏ cổ phong vắt ngang bên trái vòng tròn */}
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute -left-4 bottom-3 h-10 w-24 text-brand-600 opacity-35"
        fill="currentColor"
        viewBox="0 0 96 40"
      >
        <path d="M18 26 C22 18, 34 18, 38 24 C44 18, 56 20, 58 28 H10 C10 24, 14 24, 18 26 Z" />
        <rect height="3.5" rx="1.75" width="48" x="4" y="30" />
      </svg>
      <svg aria-hidden="true" viewBox="0 0 100 100" className="size-full fill-none">
        <circle cx="50" cy="50" r="42" strokeWidth="8" className="stroke-brand-100" />
        <circle
          cx="50"
          cy="50"
          r="42"
          pathLength="100"
          strokeWidth="8"
          strokeDasharray={`${value} 100`}
          strokeLinecap="round"
          transform="rotate(-90 50 50)"
          className="stroke-brand-600"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-0.5">
        <span className="text-h1 font-bold tabular-nums text-brand-700">{percent}%</span>
        <span className="text-caption text-ink-secondary">Tổng điểm</span>
      </div>
    </div>
  )
}

export function ExamResultView({
  result,
  filter,
  onFilter,
  weakPoints,
}: {
  result: ExamResult
  filter: ResultFilter
  onFilter: (filter: ResultFilter) => void
  weakPoints?: WeakPointsState
}) {
  const items = result.items.filter((item) => filter === 'ALL' || item.result === filter)
  const wrongCount = result.items.filter((item) => item.result === 'WRONG').length
  const blankCount = result.items.filter((item) => item.result === 'BLANK').length
  const counts: Record<ResultFilter, number> = {
    ALL: result.items.length,
    WRONG: wrongCount,
    BLANK: blankCount,
  }

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:max-w-[1072px] [overflow-wrap:anywhere]">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          subtitle="Tổng hợp điểm số, đáp án chi tiết và gợi ý luyện tập."
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>{`Kết quả · ${result.exam.title}`}</span>
            </span>
          }
        />
        <ChineseLandscapeBannerArt />
      </div>

      <div className="grid min-w-0 items-start gap-6 lg:grid-cols-[minmax(0,1fr)_310px]">
        <aside
          aria-label="Tổng kết bài thi"
          className="min-w-0 space-y-4 lg:col-start-2 lg:row-start-1"
        >
          <Card className="space-y-4 rounded-lg border-brand-100 bg-brand-50 p-6">
            <h2 className="flex items-center gap-2 text-h3 text-ink">
              <span className="flex size-8 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                <Trophy aria-hidden="true" className="size-4" />
              </span>
              <span>Điểm bài làm</span>
            </h2>
            <ScoreRing percent={result.percent} />
            <p className="text-center text-body-sm font-medium text-ink-secondary">
              Điểm trọng số: {result.score}/{result.maxScore} ({result.percent}%)
            </p>
            <div className="flex items-center justify-between gap-3 rounded-md border border-brand-100 bg-surface px-4 py-3">
              <span className="inline-flex items-center gap-2 text-body-sm font-semibold text-ink">
                <BarChart3 aria-hidden="true" className="size-4 text-brand-600" />
                <span>Số câu đúng</span>
              </span>
              <span className="text-h3 font-bold tabular-nums text-brand-700">
                {result.correctCount}/{result.totalCount}
              </span>
              <span className="sr-only">
                Số câu đúng: {result.correctCount}/{result.totalCount}
              </span>
            </div>
            <nav aria-label="Học tiếp" className="grid gap-3 pt-1">
              <Button className="w-full !rounded-pill" to="/learn/exams">
                <RotateCcw aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
                Chọn đề tiếp theo
              </Button>
              <Button
                className="w-full !rounded-pill bg-surface"
                to="/learn/progress"
                variant="outline"
              >
                <ListChecks
                  aria-hidden="true"
                  className="mr-1.5 inline-block size-4 align-middle text-brand-600"
                />
                Xem tiến độ
              </Button>
            </nav>
          </Card>

          <section
            aria-label="Điểm theo phần"
            className="relative overflow-hidden space-y-4 rounded-lg border border-brand-100 bg-brand-50 p-6 pb-16"
          >
            <CardCornerArt result="WRONG" />
            <div className="relative z-nav space-y-4">
              <h2 className="flex items-center gap-2 text-h3 text-ink">
                <BarChart3 aria-hidden="true" className="size-5 text-brand-700" />
                <span>Điểm theo phần</span>
              </h2>
              <ul className="space-y-4">
                {result.bySection.map((part) => (
                  <li key={part.index} className="space-y-2 text-body-sm">
                    <p className="font-semibold text-ink">
                      {part.name}: {part.score}/{part.maxScore}
                    </p>
                    {part.maxScore > 0 && (
                      <Progress
                        value={part.score}
                        max={part.maxScore}
                        label={`${Math.round((part.score / part.maxScore) * 100)}%`}
                        aria-label={`Điểm ${part.name}`}
                      />
                    )}
                  </li>
                ))}
              </ul>
            </div>
          </section>

          {result.previous && (
            <p className="rounded-lg border border-brand-100 bg-brand-50 p-4 text-body-sm text-ink-secondary">
              Lần trước: {result.previous.score}/{result.previous.maxScore} (
              {result.previous.percent}%)
            </p>
          )}
        </aside>

        <div className="min-w-0 space-y-5 lg:col-start-1 lg:row-start-1">
          <section aria-label="Danh sách câu hỏi" className="space-y-4">
            <h2 className="flex items-center gap-2.5 text-h2 text-ink">
              <span className="flex size-8 items-center justify-center rounded-sm bg-brand-50 text-brand-700">
                <ClipboardCheck aria-hidden="true" className="size-5" />
              </span>
              <span>Xem lại câu hỏi</span>
            </h2>

            <div
              className="flex flex-wrap items-center gap-2"
              role="group"
              aria-label="Lọc câu hỏi"
            >
              {/* Nút 1: Tất cả */}
              <Button
                size="sm"
                variant={filter === 'ALL' ? 'primary' : 'outline'}
                className={`!rounded-pill px-4 ${
                  filter === 'ALL'
                    ? 'bg-brand-600 text-surface'
                    : 'border-line bg-surface text-ink hover:border-brand-600'
                }`}
                aria-pressed={filter === 'ALL'}
                onClick={() => onFilter('ALL')}
              >
                <span>{filters.ALL}</span>
                <span aria-hidden="true" className="ml-1 opacity-85">
                  ({counts.ALL})
                </span>
              </Button>

              {/* Pill 2: Đúng (nằm ở vị trí thứ 2 ngay sau Tất cả như trong ảnh) */}
              <span
                aria-hidden="true"
                className="inline-flex min-h-touch items-center gap-1.5 rounded-pill border border-line bg-surface px-4 text-caption font-semibold text-ink"
              >
                <CheckCircle2 className="size-4 text-success" />
                <span>Đúng ({result.correctCount})</span>
              </span>

              {/* Nút 3 & 4: Sai, Bỏ trống */}
              {(['WRONG', 'BLANK'] as const).map((value) => {
                const isSelected = value === filter
                return (
                  <Button
                    key={value}
                    size="sm"
                    variant={isSelected ? 'primary' : 'outline'}
                    className={`!rounded-pill px-4 ${
                      isSelected
                        ? 'bg-brand-600 text-surface'
                        : 'border-line bg-surface text-ink hover:border-brand-600'
                    }`}
                    aria-pressed={isSelected}
                    onClick={() => onFilter(value)}
                  >
                    {value === 'WRONG' ? (
                      <XCircle
                        aria-hidden="true"
                        className={`mr-1.5 inline-block size-4 align-middle ${
                          isSelected ? 'text-surface' : 'text-danger'
                        }`}
                      />
                    ) : (
                      <MinusCircle
                        aria-hidden="true"
                        className={`mr-1.5 inline-block size-4 align-middle ${
                          isSelected ? 'text-surface' : 'text-warning'
                        }`}
                      />
                    )}
                    <span>{filters[value]}</span>
                    <span aria-hidden="true" className="ml-1 opacity-80">
                      ({counts[value]})
                    </span>
                  </Button>
                )
              })}
            </div>

            {items.length === 0 && <p>Không có câu trong bộ lọc này.</p>}

            {items.map((item) => {
              const HeadingIcon =
                item.result === 'CORRECT'
                  ? FileCheck2
                  : item.result === 'WRONG'
                    ? FileX2
                    : FileMinus2
              return (
                <article
                  key={item.no}
                  aria-label={'Câu ' + item.no}
                  className={`relative min-w-0 overflow-hidden space-y-3.5 rounded-lg border p-5 sm:p-6 ${resultCardClass[item.result]}`}
                >
                  <CardCornerArt result={item.result} />
                  <div className="relative z-nav space-y-3.5">
                    <div className="flex items-center justify-between gap-3">
                      <h3 className={`flex items-center gap-2 text-h3 ${resultTone[item.result]}`}>
                        <HeadingIcon aria-hidden="true" className="size-5 shrink-0" />
                        <span>
                          Câu {item.no} · {labels[item.result]}
                        </span>
                      </h3>
                      {item.result === 'CORRECT' ? (
                        <CheckCircle2 aria-hidden="true" className="size-6 shrink-0 text-success" />
                      ) : item.result === 'WRONG' ? (
                        <XCircle aria-hidden="true" className="size-6 shrink-0 text-danger" />
                      ) : (
                        <MinusCircle aria-hidden="true" className="size-6 shrink-0 text-warning" />
                      )}
                    </div>

                    <p className="break-words font-hanzi text-h3 text-ink" lang="zh-CN">
                      {item.stem}
                    </p>

                    <div className="space-y-2">
                      <p className="flex items-center gap-2.5 rounded-md bg-surface/90 px-4 py-3 text-body-sm text-ink shadow-sm">
                        <Circle aria-hidden="true" className="size-4 shrink-0 text-ink-muted" />
                        <span>
                          Câu trả lời của bạn:{' '}
                          {item.yourAnswer ? answerText(item, item.yourAnswer) : 'Bỏ trống'}
                        </span>
                      </p>
                      <p className="flex items-center gap-2.5 rounded-md bg-success-bg px-4 py-3 text-body-sm font-semibold text-success">
                        <CircleDot aria-hidden="true" className="size-4 shrink-0 text-success" />
                        <span>Đáp án đúng: {answerText(item, item.correctKey)}</span>
                      </p>
                    </div>

                    <p className="flex items-start gap-2 text-body-sm text-ink-secondary">
                      <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-ink-muted" />
                      <span>{item.explanation?.trim() || 'Lời giải đang được cập nhật'}</span>
                    </p>

                    {item.result !== 'CORRECT' && item.primaryKid && (
                      <div>
                        <Link
                          className="inline-flex min-h-touch items-center gap-1.5 text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                          to={'/learn/practice/targeted?kid=' + encodeURIComponent(item.primaryKid)}
                        >
                          <RotateCcw aria-hidden="true" className="size-4" />
                          <span>Luyện nút này</span>
                          <ArrowRight aria-hidden="true" className="size-4" />
                        </Link>
                      </div>
                    )}
                  </div>
                </article>
              )
            })}
          </section>

          {result.wrongByKid.length > 0 && (
            <section
              aria-label="Câu sai theo KID"
              className="relative overflow-hidden rounded-lg border border-brand-100 bg-brand-50 p-5 sm:p-6"
            >
              <KidScrollCornerArt />
              <div className="relative z-nav flex items-start gap-3.5 pr-24">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <GraduationCap aria-hidden="true" className="size-5" />
                </span>
                <div className="min-w-0 flex-1 space-y-1.5">
                  <h2 className="text-h3 text-ink">Câu sai theo KID</h2>
                  <ul className="space-y-1.5 text-body-sm text-ink-secondary">
                    {result.wrongByKid.map(({ kid, nameVi, wrong, total }) => (
                      <li key={kid} className="space-y-1">
                        <p>
                          {nameVi} ({kid}) · {wrong}/{total} câu sai hoặc bỏ trống
                        </p>
                        <Link
                          className="inline-flex min-h-touch items-center gap-1 text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                          to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                        >
                          <span>Luyện nút này</span>
                          <ArrowRight aria-hidden="true" className="size-4" />
                        </Link>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </section>
          )}

          {weakPoints && (weakPoints.state !== 'ready' || weakPoints.points.length > 0) && (
            <section
              aria-label="Điểm yếu cần luyện"
              className="relative overflow-hidden rounded-lg border border-brand-100 bg-brand-50 p-5 sm:p-6"
            >
              <CardCornerArt result="WRONG" />
              <div className="relative z-nav flex items-start gap-3.5">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <Target aria-hidden="true" className="size-5" />
                </span>
                <div className="min-w-0 flex-1 space-y-2">
                  <h2 className="text-h3 text-ink">Điểm yếu cần luyện</h2>
                  {weakPoints.state === 'loading' && <p role="status">Đang tải điểm yếu…</p>}
                  {weakPoints.state === 'error' && (
                    <div className="space-y-2">
                      <p role="alert">Không thể tải điểm yếu.</p>
                      <Button size="sm" onClick={weakPoints.onRetry}>
                        Thử lại
                      </Button>
                    </div>
                  )}
                  {weakPoints.state === 'ready' && (
                    <ul className="space-y-1.5 text-body-sm text-ink-secondary">
                      {weakPoints.points.map(({ kid, nameVi, mastery }) => (
                        <li key={kid} className="flex flex-wrap items-center gap-x-2">
                          <span>
                            {nameVi} ({kid}) · {mastery}%
                          </span>
                          <Link
                            className="inline-flex min-h-touch items-center gap-1 text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                            to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                          >
                            <span>Luyện nút này</span>
                            <ArrowRight aria-hidden="true" className="size-4" />
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            </section>
          )}
        </div>
      </div>
    </main>
  )
}
