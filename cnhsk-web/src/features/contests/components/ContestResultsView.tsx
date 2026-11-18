import { CalendarDays, Clock3, FileText, RotateCw, Trophy } from 'lucide-react'

import type { ApiQueryState } from '@/shared/api'
import { Button, Card, EmptyState, PageHeader, Skeleton } from '@/shared/ui'

import { formatCountdown, formatVietnamDate } from '../countdown'
import type { ContestResults } from '../types'
import { ContestRankingBackdrop, ContestRankingMedal } from './ContestResultsArt'
import { ContestAttemptMountains } from './ContestAttemptArt'

type Props = { state: ApiQueryState; result?: ContestResults; error?: string; onRetry: () => void }
const statusLabels = {
  IN_PROGRESS: 'Đang làm bài',
  SUBMITTED: 'Đã nộp bài',
  EXPIRED: 'Lượt thi đã hết hạn',
  ABANDONED: 'Lượt thi đã kết thúc',
}

function ChineseLandscapeBannerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute right-0 top-0 hidden h-24 w-3/5 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMid slice"
      viewBox="0 0 680 132"
    >
      <defs>
        <linearGradient id="cnhsk-contres-sun" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.8" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.08" />
        </linearGradient>
        <linearGradient id="cnhsk-contres-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-contres-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-contres-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <circle cx="518" cy="34" fill="url(#cnhsk-contres-sun)" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-contres-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-contres-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-contres-hill-front)"
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

export function ContestResultsView({ state, result, error, onRetry }: Props) {
  const navigation = result ? (
    <nav aria-label="Điều hướng kết quả" className="flex flex-wrap gap-3">
      <Button
        to={'/contests/' + result.contest.id}
        variant={result.published ? 'primary' : 'outline'}
        className={
          result.published
            ? 'bg-gradient-to-b from-brand-600 to-brand-800 shadow-sm'
            : 'font-bold shadow-sm'
        }
      >
        Thông tin cuộc thi
      </Button>
      <Button
        to="/contests"
        variant={result.published ? 'outline' : 'ghost'}
        className={
          result.published
            ? 'border-brand-100 font-bold text-ink'
            : 'font-bold text-brand-700 underline underline-offset-4'
        }
      >
        Cuộc thi khác
      </Button>
    </nav>
  ) : null
  const mine = result?.mine
  const showPublishedSummary = (state === 'ready' || state === 'empty') && result?.published
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 pb-16 sm:px-6">
      {!showPublishedSummary ? (
        <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
          <PageHeader
            className="relative z-nav mb-0"
            title={
              <span className="inline-flex min-w-0 max-w-full items-center gap-3">
                <span aria-hidden="true" className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600" />
                <span>Kết quả cuộc thi</span>
              </span>
            }
            subtitle={
              result?.contest.name ? (
                <span className="break-words text-h3 font-semibold">{result.contest.name}</span>
              ) : undefined
            }
          />
          <ChineseLandscapeBannerArt />
        </div>
      ) : null}
      {state === 'loading' ? (
        <div>
          <p role="status">Đang tải kết quả…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}
      {state === 'error' ? (
        <Card role="alert" className="space-y-3 border-danger bg-danger-bg">
          <p>{error ?? 'Không tải được kết quả.'}</p>
          <Button variant="outline" onClick={onRetry}>
            Thử lại
          </Button>
        </Card>
      ) : null}
      {state === 'empty' && !result ? (
        <EmptyState
          title="Chưa có kết quả cuộc thi"
          description="Quay lại danh sách để xem các cuộc thi."
        />
      ) : null}
      {(state === 'ready' || state === 'empty') && result ? (
        <>
          {!result.published ? (
            <>
              <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50/70 p-5 sm:p-7">
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute bottom-0 right-0 h-28 w-3/4 sm:w-1/2"
                >
                  <ContestAttemptMountains position="bottom" />
                </div>
                <div className="relative z-nav space-y-4">
                  <h2 className="break-words text-h2 font-bold text-brand-700">
                    Đang chờ công bố kết quả
                  </h2>
                  <p>
                    Ban tổ chức sẽ công bố bảng xếp hạng sau khi cuộc thi kết thúc và hoàn tất việc
                    chấm điểm.
                  </p>
                  <dl className="min-w-0 space-y-2 text-body">
                    <div>
                      <dt className="text-ink-secondary">Cuộc thi:</dt>
                      <dd className="break-words font-bold text-ink">{result.contest.name}</dd>
                    </div>
                    <div>
                      <dt className="text-ink-secondary">Thời gian thi:</dt>
                      <dd className="break-words font-bold text-ink">
                        {formatVietnamDate(result.contest.startsAt)}{' '}
                        <span className="font-normal">(giờ Việt Nam)</span>
                      </dd>
                    </div>
                  </dl>
                  <div className="flex flex-wrap gap-x-3 gap-y-1 text-body-sm text-ink-secondary">
                    <p>{mine ? statusLabels[mine.status] : 'Bạn chưa tham gia cuộc thi này.'}</p>
                    {mine?.submittedAt ? (
                      <p className="text-body-sm">
                        Nộp lúc {formatVietnamDate(mine.submittedAt)} · giờ Việt Nam
                      </p>
                    ) : null}
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <Button to="/contests" variant="outline" className="font-bold shadow-sm">
                      <span className="inline-flex items-center gap-2">
                        <CalendarDays aria-hidden="true" className="size-5 text-brand-700" />
                        Trở về lịch thi
                      </span>
                    </Button>
                    <Button onClick={onRetry} variant="ghost" className="text-ink-secondary">
                      <span className="inline-flex items-center gap-2">
                        <RotateCw aria-hidden="true" className="size-4" />
                        Tải lại kết quả
                      </span>
                    </Button>
                  </div>
                </div>
              </Card>
              {navigation}
            </>
          ) : (
            <>
              <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50/70 p-4 shadow-sm sm:p-5">
                <ChineseLandscapeBannerArt />
                <ContestRankingBackdrop />
                <div className="relative z-nav space-y-3">
                  <PageHeader
                    className="mb-0"
                    title={
                      <span className="inline-flex min-w-0 max-w-full items-center gap-3">
                        <span
                          aria-hidden="true"
                          className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600"
                        />
                        <span className="min-w-0 break-words">Kết quả đã được công bố</span>
                      </span>
                    }
                  />
                  {mine ? (
                    <>
                      <p className="text-body text-ink-secondary">{statusLabels[mine.status]}</p>
                      <div className="grid min-w-0 gap-3 sm:grid-cols-3">
                        <Card className="flex min-w-0 items-start gap-3 border-brand-100 bg-surface/90 p-4">
                          <FileText aria-hidden="true" className="size-7 shrink-0 text-brand-700" />
                          <div className="min-w-0 space-y-1">
                            <p className="text-body-sm text-ink">Điểm của bạn</p>
                            <p className="break-words text-h1 font-bold tabular-nums text-brand-700">
                              {mine.score === null
                                ? 'Chưa có điểm'
                                : mine.score +
                                  (mine.maxScore === null ? '' : ' / ' + mine.maxScore)}
                            </p>
                          </div>
                        </Card>
                        <Card className="flex min-w-0 items-start gap-3 border-brand-100 bg-surface/90 p-4">
                          <Clock3 aria-hidden="true" className="size-7 shrink-0 text-brand-700" />
                          <div className="min-w-0 space-y-1">
                            <p className="text-body-sm text-ink">Thời gian làm bài</p>
                            <p className="break-words text-h1 font-bold tabular-nums text-brand-700">
                              {mine.durationMs === null
                                ? 'Chưa có'
                                : formatCountdown(mine.durationMs)}
                            </p>
                          </div>
                        </Card>
                        <Card className="flex min-w-0 items-start gap-3 border-brand-100 bg-surface/90 p-4">
                          <Trophy aria-hidden="true" className="size-7 shrink-0 text-brand-700" />
                          <div className="min-w-0 space-y-1">
                            <p className="text-body-sm text-ink">Hạng của bạn</p>
                            <p className="break-words text-h1 font-bold tabular-nums text-brand-700">
                              {mine.rank === null ? 'Không có hạng' : '#' + mine.rank}
                            </p>
                          </div>
                        </Card>
                      </div>
                      {mine.excluded ? (
                        <p>Lượt của bạn không được tính vào bảng xếp hạng.</p>
                      ) : null}
                    </>
                  ) : (
                    <p>Bạn chưa tham gia cuộc thi này.</p>
                  )}
                  <p className="text-body-sm">
                    Ban tổ chức sẽ liên hệ người đạt giải qua email đăng ký.
                  </p>
                </div>
              </Card>
              {navigation}
              <section aria-labelledby="contest-ranking-title" className="space-y-3">
                <div className="relative isolate overflow-hidden py-1">
                  <ContestRankingBackdrop />
                  <h2
                    id="contest-ranking-title"
                    className="relative z-nav flex items-center gap-2.5 text-h2 font-bold"
                  >
                    <Trophy aria-hidden="true" className="size-7 shrink-0 text-brand-700" />
                    Bảng xếp hạng
                  </h2>
                </div>
                {result.ranking?.items.length ? (
                  <>
                    <ol className="space-y-3" aria-label="Bảng xếp hạng cuộc thi">
                      {result.ranking.items.map((row) => (
                        <li
                          key={row.userId}
                          className="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-3 rounded-md border border-brand-100 bg-surface px-4 py-2 shadow-sm sm:px-5"
                        >
                          <ContestRankingMedal rank={row.rank} />
                          <p className="min-w-0 flex-1 self-center break-words text-body font-bold sm:text-h3">
                            {row.displayName ?? 'Người học'}
                          </p>
                          <div className="flex w-full flex-wrap items-center gap-x-5 gap-y-2 border-t border-brand-100 pt-2 pl-14 text-body-sm sm:w-auto sm:border-t-0 sm:border-l sm:pt-0 sm:pl-5">
                            <p className="flex min-w-0 items-center gap-2">
                              <FileText
                                aria-hidden="true"
                                className="size-5 shrink-0 text-brand-600"
                              />
                              <span className="break-words text-ink-secondary">
                                Điểm: {row.score} / {row.maxScore}
                              </span>
                            </p>
                            <p className="flex min-w-0 items-center gap-2">
                              <Clock3
                                aria-hidden="true"
                                className="size-5 shrink-0 text-brand-600"
                              />
                              <span className="break-words text-ink-secondary">
                                Thời gian:{' '}
                                {row.durationMs === null
                                  ? 'Chưa có'
                                  : formatCountdown(row.durationMs)}
                              </span>
                            </p>
                          </div>
                        </li>
                      ))}
                    </ol>
                    <p className="text-body-sm text-ink-secondary">
                      Trang {result.ranking.page + 1} · {result.ranking.totalElements} người trong
                      bảng xếp hạng
                    </p>
                  </>
                ) : (
                  <Card role="status">Chưa có lượt thi được tính vào bảng xếp hạng.</Card>
                )}
              </section>
            </>
          )}
        </>
      ) : null}
    </main>
  )
}
