import { ArrowRight, CalendarDays, Trophy, Users } from 'lucide-react'
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
import type { ContestPage, ScheduleState } from '../types'

const stateLabels: Record<ScheduleState, string> = {
  ONGOING: 'Đang diễn ra',
  UPCOMING: 'Sắp diễn ra',
  ENDED: 'Đã kết thúc',
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

type Props = {
  state: ApiQueryState
  result?: ContestPage
  error?: string
  filter: ScheduleState | ''
  onFilter: (filter: ScheduleState | '') => void
  onPage: (page: number) => void
  onRetry: () => void
}

export function ContestListView(props: Props) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <PageHeader
        title="Cuộc thi"
        subtitle="Cùng thử sức, cùng tiến bộ với cộng đồng học tiếng Trung."
      />
      <div role="group" aria-label="Giai đoạn cuộc thi" className="flex flex-wrap gap-2">
        {(['', 'ONGOING', 'UPCOMING', 'ENDED'] as const).map((state) => (
          <Button
            key={state}
            variant={props.filter === state ? 'secondary' : 'outline'}
            aria-pressed={props.filter === state}
            onClick={() => props.onFilter(state)}
            size="sm"
          >
            {state ? stateLabels[state] : 'Tất cả'}
          </Button>
        ))}
      </div>
      {props.state === 'loading' ? (
        <div>
          <p role="status">Đang tải cuộc thi…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <Card role="alert" className="space-y-3">
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
            return (
              <section key={state} aria-label={stateLabels[state]} className="space-y-3">
                <h2 className="text-h2 text-ink">{stateLabels[state]}</h2>
                <div className="grid min-w-0 gap-4 md:grid-cols-2 xl:grid-cols-3">
                  {items.map((contest) => (
                    <Card
                      role="article"
                      aria-label={contest.name}
                      key={contest.id}
                      className="flex min-w-0 flex-col gap-4 break-words p-6"
                    >
                      <ScheduleBadge state={contest.state} />
                      <h3 className="text-h3 text-ink">
                        <Link
                          className="inline-flex min-h-touch items-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                          to={'/contests/' + contest.id}
                        >
                          {contest.name}
                        </Link>
                      </h3>
                      <div className="space-y-2 text-body-sm text-ink-secondary">
                        <p className="flex items-start gap-2">
                          <CalendarDays aria-hidden="true" className="mt-1 size-4 shrink-0" />
                          <span>
                            {formatVietnamDate(contest.startsAt)}
                            <br />
                            đến {formatVietnamDate(contest.endsAt)} · giờ Việt Nam
                          </span>
                        </p>
                        <p className="flex items-center gap-2">
                          <Users aria-hidden="true" className="size-4 shrink-0" />
                          {contest.participantCount} người tham gia
                          {contest.maxParticipants !== null ? ' / ' + contest.maxParticipants : ''}
                        </p>
                        {contest.prizes[0] ? (
                          <p className="flex items-start gap-2">
                            <Trophy
                              aria-hidden="true"
                              className="mt-1 size-4 shrink-0 text-brand-700"
                            />
                            <span>{contest.prizes[0].description}</span>
                          </p>
                        ) : null}
                      </div>
                      <Button
                        to={'/contests/' + contest.id}
                        variant="outline"
                        className="mt-auto self-start"
                      >
                        Xem cuộc thi{' '}
                        <ArrowRight
                          aria-hidden="true"
                          className="ml-2 inline size-4 align-middle"
                        />
                      </Button>
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
