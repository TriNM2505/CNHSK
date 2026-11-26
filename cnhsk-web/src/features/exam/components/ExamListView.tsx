import {
  ClipboardCheck,
  FileText,
  GraduationCap,
  LayoutGrid,
  Play,
  RotateCcw,
  Trophy,
} from 'lucide-react'
import { Button, Card, EmptyState, PageHeader, Skeleton } from '@/shared/ui'
import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui/LevelBadge'
import { PageState, type PageStateValue } from '@/shared/ui/PageState'
import type { ExamSummary } from '../types'

export type ExamListViewProps = {
  exams: ExamSummary[]
  level: HskLevel
  state: PageStateValue
  error?: string
  actionError?: string
  pending?: boolean
  onLevelChange: (level: HskLevel) => void
  onRetry: () => void
  onStartExam: (id: number) => void
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

function BestScoreTileArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-56 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMax slice"
      viewBox="0 0 240 100"
    >
      <defs>
        <linearGradient id="cnhsk-tile-mtn1" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-tile-mtn2" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.3" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.06" />
        </linearGradient>
      </defs>

      {/* Chim hạc bay xa */}
      <path
        d="M96 28 Q101 23 106 27 Q111 23 116 28"
        className="text-brand-700"
        opacity="0.65"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.8"
      />
      <path
        d="M118 20 Q122 16 126 19 Q130 16 134 20"
        className="text-brand-700"
        opacity="0.5"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.5"
      />
      <path
        d="M84 36 Q87 33 90 35 Q93 33 96 36"
        className="text-brand-700"
        opacity="0.45"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.4"
      />

      {/* Dãy núi đỏ mờ */}
      <path
        d="M12 100 C48 64, 82 58, 124 84 C156 54, 192 48, 240 82 L240 100 Z"
        fill="url(#cnhsk-tile-mtn1)"
      />
      <path
        d="M68 100 C112 68, 152 62, 204 88 C218 78, 230 76, 240 84 L240 100 Z"
        fill="url(#cnhsk-tile-mtn2)"
      />

      {/* Khóm trúc đỏ đậm góc phải */}
      <g className="text-brand-700" opacity="0.88">
        <path d="M214 102 L192 -4" stroke="currentColor" strokeWidth="4.5" />
        <line stroke="white" strokeWidth="1.6" x1="205" x2="212" y1="68" y2="66" />
        <line stroke="white" strokeWidth="1.6" x1="198" x2="205" y1="34" y2="32" />

        <path d="M230 102 L218 -4" stroke="currentColor" strokeWidth="6" />
        <line stroke="white" strokeWidth="1.8" x1="222" x2="230" y1="74" y2="73" />
        <line stroke="white" strokeWidth="1.8" x1="218" x2="226" y1="38" y2="37" />

        <path d="M216 14 Q178 12 146 28" stroke="currentColor" strokeWidth="1.6" />
        <path d="M198 38 Q170 44 148 60" stroke="currentColor" strokeWidth="1.4" />

        <path d="M158 20 Q144 32 136 46 Q150 36 158 20 Z" fill="currentColor" />
        <path d="M170 16 Q160 34 154 52 Q168 38 170 16 Z" fill="currentColor" />
        <path d="M182 14 Q176 32 174 48 Q186 34 182 14 Z" fill="currentColor" />
        <path d="M154 22 Q138 22 126 28 Q144 28 154 22 Z" fill="currentColor" opacity="0.8" />

        <path d="M164 48 Q152 60 144 74 Q158 64 164 48 Z" fill="currentColor" />
        <path d="M176 44 Q168 60 164 76 Q176 62 176 44 Z" fill="currentColor" />
      </g>
    </svg>
  )
}

export function ExamListView({
  exams,
  level,
  state,
  error,
  actionError,
  pending,
  onLevelChange,
  onRetry,
  onStartExam,
}: ExamListViewProps) {
  const attemptedCount = exams.filter((exam) => exam.myAttempts > 0).length
  const bestPercents = exams
    .map((exam) => exam.myBest?.percent)
    .filter((percent): percent is number => typeof percent === 'number')
  const maxBestPercent = bestPercents.length > 0 ? Math.max(...bestPercents) : null

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>Đề thi thử</span>
            </span>
          }
          subtitle="Đề theo format thi HSK thật để luyện tập. Muốn biết mình ở cấp nào, làm bài Kiểm tra đầu vào."
        />
        <ChineseLandscapeBannerArt />
      </div>
      {actionError ? (
        <p role="alert" className="text-danger">
          {actionError}
        </p>
      ) : null}
      <div aria-label="Chọn cấp HSK" role="group" className="flex flex-wrap gap-2.5">
        {SUPPORTED_HSK_LEVELS.map((value) => (
          <Button
            key={value}
            aria-pressed={value === level}
            onClick={() => onLevelChange(value)}
            variant={value === level ? 'primary' : 'secondary'}
            className={`!rounded-pill px-6 font-bold ${value === level ? '' : 'border-brand-100 bg-brand-50 text-ink hover:bg-brand-100'}`}
          >
            HSK {value}
          </Button>
        ))}
      </div>

      <PageState
        state={state}
        empty={
          <EmptyState
            title={`Đề HSK ${level} đang được cập nhật`}
            description="Bạn có thể chọn cấp HSK khác."
          />
        }
        loading={
          <div role="status" aria-label="Đang tải đề thi">
            <Skeleton variant="card" />
          </div>
        }
        error={
          <section role="alert">
            <p>{error ?? 'Không thể tải đề thi.'}</p>
            <Button onClick={onRetry}>Thử lại</Button>
          </section>
        }
      >
        <div className="min-w-0">
          {/* Thẻ thống kê + danh sách đề; bài kiểm tra đầu vào có trang riêng /learn/placement */}
          <div className="min-w-0 space-y-5">
            <div className="grid min-w-0 gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)]">
              <Card className="flex items-center gap-4 border-brand-100 bg-brand-50 p-5">
                <span className="flex size-12 shrink-0 items-center justify-center rounded-md bg-brand-100 text-brand-700">
                  <ClipboardCheck aria-hidden="true" className="size-6" />
                </span>
                <div className="min-w-0">
                  <p className="text-h1 font-bold tabular-nums leading-none text-brand-700">
                    {attemptedCount}
                  </p>
                  <p className="mt-1.5 text-body-sm text-ink-secondary">Đề đã làm</p>
                </div>
              </Card>

              <Card className="relative flex items-center gap-4 overflow-hidden border-brand-100 bg-brand-50 p-5">
                <span className="relative z-nav flex size-12 shrink-0 items-center justify-center rounded-md bg-brand-100 text-brand-700">
                  <Trophy aria-hidden="true" className="size-6" />
                </span>
                <div className="relative z-nav min-w-0">
                  <p className="text-h1 font-bold tabular-nums leading-none text-brand-700">
                    {maxBestPercent === null ? 'Chưa có' : `${maxBestPercent}%`}
                  </p>
                  <p className="mt-1.5 text-body-sm text-ink-secondary">Điểm cao nhất</p>
                </div>
                <BestScoreTileArt />
              </Card>
            </div>

            <section
              aria-label={`Đề HSK ${level}`}
              className="min-w-0 space-y-4 rounded-lg border border-line bg-surface p-5 sm:p-6"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="inline-flex items-center gap-2.5 text-h2 text-ink">
                  <span aria-hidden="true" className="h-6 w-1.5 rounded-pill bg-brand-600" />
                  <span>Danh sách đề thi HSK {level}</span>
                </h2>
                <span className="text-body-sm font-medium text-ink-secondary">
                  Tổng {exams.length} đề
                </span>
              </div>

              {exams.length === 0 ? (
                <p>Đề HSK {level} đang được cập nhật</p>
              ) : (
                <div className="space-y-3.5">
                  {exams.map((exam) => {
                    const isHighlighted = exam.activeSessionId !== null || exam.myAttempts > 0
                    return (
                      <Card
                        role="article"
                        key={exam.id}
                        aria-label={exam.title}
                        className={`flex min-w-0 flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5 ${
                          isHighlighted
                            ? 'border-brand-100 bg-brand-50'
                            : 'border-line bg-surface hover:border-brand-100'
                        }`}
                      >
                        <div className="flex min-w-0 flex-1 items-start gap-3.5 sm:items-center">
                          <span className="flex size-12 shrink-0 items-center justify-center rounded-md bg-brand-100 text-brand-700">
                            <FileText aria-hidden="true" className="size-6" />
                          </span>
                          <div className="min-w-0 flex-1 space-y-1.5">
                            <h3 className="text-h3 text-ink [overflow-wrap:anywhere]">
                              {exam.title}
                            </h3>
                            <dl className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body-sm text-ink-secondary">
                              <div className="inline-flex items-center gap-1.5">
                                <GraduationCap
                                  aria-hidden="true"
                                  className="size-4 text-ink-secondary"
                                />
                                <dt className="sr-only">Số câu</dt>
                                <dd className="font-medium text-ink">{exam.questionCount}</dd>
                                <span aria-hidden="true">câu</span>
                              </div>
                              <span aria-hidden="true" className="text-line">
                                |
                              </span>
                              <div className="inline-flex items-center gap-1.5">
                                <LayoutGrid
                                  aria-hidden="true"
                                  className="size-4 text-ink-secondary"
                                />
                                <dt className="sr-only">Số phần</dt>
                                <dd className="font-medium text-ink">{exam.sections.length}</dd>
                                <span aria-hidden="true">phần</span>
                              </div>
                              <span aria-hidden="true" className="text-line">
                                |
                              </span>
                              <div className="inline-flex items-center gap-1.5">
                                <RotateCcw
                                  aria-hidden="true"
                                  className={`size-4 ${exam.myAttempts > 0 ? 'text-brand-600' : 'text-ink-secondary'}`}
                                />
                                <dt className="sr-only">Số lần làm</dt>
                                <dd className="sr-only">{exam.myAttempts}</dd>
                                <span aria-hidden="true">
                                  {exam.myAttempts > 0
                                    ? `Đã làm ${exam.myAttempts} lần`
                                    : 'Chưa làm'}
                                </span>
                              </div>
                            </dl>
                          </div>
                        </div>

                        <div className="flex flex-wrap items-center justify-between gap-4 sm:justify-end sm:gap-6">
                          <div className="text-left sm:text-center">
                            <p className="text-caption text-ink-secondary">Điểm cao nhất</p>
                            {exam.myBest === null ? (
                              <span className="mt-1 inline-block rounded-pill bg-canvas px-3.5 py-1 text-body-sm font-semibold text-ink-secondary">
                                Chưa có điểm
                              </span>
                            ) : (
                              <span className="mt-1 inline-block rounded-pill bg-brand-100 px-3.5 py-1 text-h3 font-bold tabular-nums text-brand-700">
                                {exam.myBest.percent}%
                              </span>
                            )}
                          </div>

                          {exam.activeSessionId !== null ? (
                            <Button
                              className="min-w-36 !rounded-pill px-6"
                              to={`/learn/exams/sessions/${exam.activeSessionId}`}
                            >
                              <Play
                                aria-hidden="true"
                                className="mr-1.5 inline-block size-4 align-middle"
                              />
                              Tiếp tục
                            </Button>
                          ) : (
                            <Button
                              variant={isHighlighted ? 'primary' : 'outline'}
                              className={`min-w-36 !rounded-pill px-6 ${
                                isHighlighted
                                  ? ''
                                  : 'border-brand-600 text-brand-700 hover:bg-brand-50'
                              }`}
                              disabled={pending}
                              onClick={() => onStartExam(exam.id)}
                            >
                              <Play
                                aria-hidden="true"
                                className="mr-1.5 inline-block size-4 align-middle"
                              />
                              Bắt đầu
                            </Button>
                          )}
                        </div>
                      </Card>
                    )
                  })}
                </div>
              )}
            </section>
          </div>
        </div>
      </PageState>
    </main>
  )
}
