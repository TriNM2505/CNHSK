import {
  ArrowRight,
  BookOpen,
  CalendarDays,
  CheckCircle2,
  Gamepad2,
  Gift,
  LayoutGrid,
  PlayCircle,
  Users,
} from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  PageHeader,
  Pagination,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import { formatVietnamDate } from '../countdown'
import type { ContestPage, ContestSummary, ScheduleState } from '../types'
import { ContestCardArt } from './ContestCardArt'
import { ContestMascot } from './ContestMascot'

const stateLabels: Record<ScheduleState, string> = {
  ONGOING: 'Đang diễn ra',
  UPCOMING: 'Sắp diễn ra',
  ENDED: 'Đã kết thúc',
}
const stateIcons = {
  ONGOING: PlayCircle,
  UPCOMING: CalendarDays,
  ENDED: CheckCircle2,
}

export function ScheduleBadge({ state }: { state: ScheduleState }) {
  return (
    <StatusBadge
      tone={state === 'ONGOING' ? 'success' : state === 'UPCOMING' ? 'warning' : 'neutral'}
    >
      {stateLabels[state]}
    </StatusBadge>
  )
}

/** Game contests are run on their own community page; exam contests on the contest pages here. */
function contestHref(contest: Pick<ContestSummary, 'id' | 'gameCode'>): string {
  return (contest.gameCode ? '/community/contests/' : '/contests/') + contest.id
}

function KindBadge({ gameName }: { gameName?: string }) {
  const Icon = gameName ? Gamepad2 : BookOpen
  return (
    <StatusBadge tone="info">
      <span className="inline-flex items-center gap-1.5">
        <Icon aria-hidden="true" className="size-3.5 shrink-0" />
        {gameName ? 'Trò chơi · ' + gameName : 'Kiến thức'}
      </span>
    </StatusBadge>
  )
}

type Props = {
  state: ApiQueryState
  /** Game names by code, so a game contest says which game it is played in. */
  gameNames?: Readonly<Record<string, string>>
  result?: ContestPage
  error?: string
  filter: ScheduleState | ''
  onFilter: (filter: ScheduleState | '') => void
  onPage: (page: number) => void
  onRetry: () => void
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
        <linearGradient id="cnhsk-contlist-sun" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.9" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
        <linearGradient id="cnhsk-contlist-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-contlist-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-contlist-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <circle cx="518" cy="34" fill="url(#cnhsk-contlist-sun)" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-contlist-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-contlist-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-contlist-hill-front)"
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

export function ContestListView(props: Props) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-3 px-4 py-4 pb-10 sm:px-6">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-4 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>Cuộc thi</span>
            </span>
          }
          subtitle="Thi kiến thức theo đề hoặc thi trò chơi: cùng thử sức, cùng tiến bộ với cộng đồng học tiếng Trung."
        />
        <ChineseLandscapeBannerArt />
      </div>
      <div
        role="group"
        aria-label="Giai đoạn cuộc thi"
        className="grid grid-cols-2 gap-2 rounded-md border border-brand-100 bg-brand-50 p-2 sm:flex sm:flex-wrap sm:gap-4"
      >
        {(['', 'ONGOING', 'UPCOMING', 'ENDED'] as const).map((state) => {
          const Icon = state ? stateIcons[state] : LayoutGrid
          return (
            <Button
              key={state}
              variant={props.filter === state ? 'primary' : 'outline'}
              aria-pressed={props.filter === state}
              onClick={() => props.onFilter(state)}
              size="sm"
              className={`min-w-0 shadow-sm sm:min-w-36 ${props.filter === state ? '' : 'border-brand-100 text-ink'}`}
            >
              <span className="inline-flex items-center gap-2">
                <Icon aria-hidden="true" className="size-4 shrink-0" />
                {state ? stateLabels[state] : 'Tất cả'}
              </span>
            </Button>
          )
        })}
      </div>
      {props.state === 'loading' ? (
        <div>
          <p role="status">Đang tải cuộc thi…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <Card role="alert" className="space-y-3 border-danger bg-danger-bg">
          <p>{props.error ?? 'Không tải được danh sách cuộc thi.'}</p>
          <Button variant="outline" onClick={props.onRetry}>
            Thử lại
          </Button>
        </Card>
      ) : null}
      {props.state === 'empty' ? (
        <EmptyState
          title="Chưa có cuộc thi trong giai đoạn này."
          description={
            props.filter
              ? 'Thử chọn giai đoạn khác để xem cuộc thi.'
              : 'Danh sách cuộc thi hiện đang trống.'
          }
        />
      ) : null}
      {props.state === 'ready' && props.result
        ? (['ONGOING', 'UPCOMING', 'ENDED'] as const).map((state) => {
            const items = props.result!.items.filter((item) => item.state === state)
            if (!items.length) return null
            const StateIcon = stateIcons[state]
            return (
              <section key={state} aria-label={stateLabels[state]} className="space-y-2">
                <h2 className="flex items-center gap-2.5 text-h3 font-bold text-brand-700">
                  <StateIcon aria-hidden="true" className="size-6 shrink-0" />
                  {stateLabels[state]}
                </h2>
                <div className="grid min-w-0 gap-3">
                  {items.map((contest) => (
                    <Card
                      role="article"
                      aria-label={contest.name}
                      key={contest.id}
                      className="relative isolate min-w-0 overflow-hidden border-brand-600/30 bg-gradient-to-br from-surface via-brand-50/50 to-surface p-0 shadow-sm"
                    >
                      <ContestCardArt />
                      <div className="relative z-nav grid min-w-0 grid-cols-4 items-center gap-x-3 gap-y-2 px-3 py-2 sm:grid-cols-12 sm:gap-x-4 sm:px-4 lg:gap-x-5">
                        <div className="col-span-1 min-w-0 self-end sm:col-span-3 sm:self-center lg:col-span-2">
                          <ContestMascot state={contest.state} />
                        </div>
                        <div className="col-span-3 min-w-0 space-y-1 sm:col-span-6 lg:col-span-7">
                          <div className="flex flex-wrap gap-2">
                            <ScheduleBadge state={contest.state} />
                            <KindBadge
                              gameName={
                                contest.gameCode
                                  ? (props.gameNames?.[contest.gameCode] ?? contest.gameCode)
                                  : undefined
                              }
                            />
                          </div>
                          <h3 className="min-w-0 text-body font-bold text-ink sm:text-h3">
                            <Link
                              className="-my-2 block min-h-touch max-w-full break-words py-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                              to={contestHref(contest)}
                            >
                              {contest.name}
                            </Link>
                          </h3>
                          <div className="space-y-1 text-caption text-ink">
                            <p className="flex items-start gap-2">
                              <CalendarDays
                                aria-hidden="true"
                                className="mt-0.5 size-4 shrink-0 text-brand-600"
                              />
                              <span className="min-w-0 break-words">
                                {formatVietnamDate(contest.startsAt)}
                                <span aria-hidden="true"> → </span>
                                <span className="sr-only"> đến </span>
                                {formatVietnamDate(contest.endsAt)} (giờ Việt Nam)
                              </span>
                            </p>
                            <p className="flex items-start gap-2">
                              <Users
                                aria-hidden="true"
                                className="size-4 shrink-0 text-brand-600"
                              />
                              <span className="min-w-0 break-words">
                                {contest.participantCount} người tham gia
                                {contest.maxParticipants !== null
                                  ? ' / ' + contest.maxParticipants
                                  : ''}
                              </span>
                            </p>
                            {contest.prizes[0] ? (
                              <p className="flex items-start gap-2">
                                <Gift
                                  aria-hidden="true"
                                  className="mt-0.5 size-4 shrink-0 text-brand-600"
                                />
                                <span className="min-w-0 break-words">
                                  {contest.prizes[0].description}
                                </span>
                              </p>
                            ) : null}
                          </div>
                        </div>
                        <div className="col-span-4 flex min-w-0 items-center border-t border-brand-100 pt-3 sm:col-span-3 sm:self-center sm:border-l sm:border-t-0 sm:py-2 sm:pl-4 lg:pl-5">
                          <Button
                            to={contestHref(contest)}
                            className="w-full !rounded-pill bg-gradient-to-b from-brand-600 to-brand-800 shadow-sm"
                          >
                            Xem cuộc thi{' '}
                            <ArrowRight
                              aria-hidden="true"
                              className="ml-2 inline size-4 align-middle"
                            />
                          </Button>
                        </div>
                      </div>
                    </Card>
                  ))}
                </div>
              </section>
            )
          })
        : null}
      {(props.state === 'ready' || props.state === 'empty') && props.result ? (
        <Pagination
          page={props.result.page + 1}
          size={props.result.size}
          total={props.result.totalElements}
          onChange={(page) => props.onPage(page - 1)}
        />
      ) : null}
    </main>
  )
}
