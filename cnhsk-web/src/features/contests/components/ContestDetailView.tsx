import { Clock3, Trophy, Users } from 'lucide-react'

import type { ApiQueryState } from '@/shared/api'
import { Button, Card, EmptyState, PageHeader, Skeleton } from '@/shared/ui'

import { formatCountdown, formatVietnamDate } from '../countdown'
import type { ContestDetail, MyResult } from '../types'
import { ScheduleBadge } from './ContestListView'

type Props = {
  state: ApiQueryState
  contest?: ContestDetail
  mine?: MyResult | null
  eligibility: 'loading' | 'guest' | 'unverified' | 'learner' | 'other'
  now: number
  error?: string
  confirming: boolean
  onConfirmChange: (open: boolean) => void
  onEnter: () => void
  onRetry: () => void
}

export function ContestDetailView(props: Props) {
  const { contest, mine } = props
  const previousAttempt = mine && mine.status !== 'IN_PROGRESS'
  const canEnter = contest?.state === 'ONGOING' && !previousAttempt
  const endRemaining = contest ? Math.max(0, Date.parse(contest.endsAt) - props.now) : 0
  const allowedTime = contest?.durationMinutes
    ? Math.min(endRemaining, contest.durationMinutes * 60_000)
    : endRemaining
  const nextBoundary = contest?.state === 'UPCOMING' ? contest.startsAt : contest?.endsAt
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 pb-16 sm:px-6">
      <PageHeader
        title={<span className="break-words">{contest?.name ?? 'Chi tiết cuộc thi'}</span>}
        actions={
          <div className="flex flex-wrap gap-3">
            <Button to="/contests" variant="ghost">
              ← Danh sách cuộc thi
            </Button>
            {props.state === 'ready' && contest ? (
              <>
                {' '}
                {canEnter && props.eligibility === 'guest' ? (
                  <Button
                    to={'/login?redirect=' + encodeURIComponent('/contests/' + contest.id)}
                    className="w-full"
                  >
                    Đăng nhập để vào thi
                  </Button>
                ) : canEnter && props.eligibility === 'unverified' ? (
                  <div className="space-y-2">
                    <p role="status">Bạn cần xác thực email trước khi vào thi.</p>
                    <Button to="/verify-email" variant="outline">
                      Xác thực email
                    </Button>
                  </div>
                ) : canEnter && props.eligibility === 'learner' ? (
                  <Button className="w-full" onClick={() => props.onConfirmChange(true)}>
                    Vào thi
                  </Button>
                ) : canEnter && props.eligibility === 'loading' ? (
                  <p role="status">Đang kiểm tra phiên đăng nhập…</p>
                ) : canEnter && props.eligibility === 'other' ? (
                  <p role="status">Tính năng làm bài dành cho người học.</p>
                ) : null}
              </>
            ) : null}
          </div>
        }
      />
      {props.state === 'loading' ? (
        <div>
          <p role="status">Đang tải cuộc thi…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <Card role="alert" className="space-y-3 p-6">
          <p>{props.error ?? 'Cuộc thi không khả dụng.'}</p>
          <Button onClick={props.onRetry} variant="outline">
            Thử lại
          </Button>
        </Card>
      ) : null}
      {props.state === 'empty' ? (
        <EmptyState
          title="Cuộc thi không khả dụng."
          description="Quay lại danh sách để xem các cuộc thi khác."
        />
      ) : null}
      {props.state === 'ready' && contest ? (
        <>
          {' '}
          {props.confirming && canEnter && props.eligibility === 'learner' ? (
            <section
              aria-label="Xác nhận vào thi"
              className="space-y-3 rounded-md border border-brand-100 p-3"
            >
              {mine?.status === 'IN_PROGRESS' ? (
                <p>Mỗi người chỉ thi một lần. Bạn sẽ tiếp tục lượt đang mở với hạn nộp đã có.</p>
              ) : (
                <p>
                  Mỗi người chỉ thi một lần; thời gian còn lại: {Math.ceil(allowedTime / 60_000)}{' '}
                  phút.
                </p>
              )}
              <div className="flex flex-wrap gap-2">
                <Button onClick={props.onEnter}>Bắt đầu làm bài</Button>
                <Button onClick={() => props.onConfirmChange(false)} variant="outline">
                  Quay lại
                </Button>
              </div>
            </section>
          ) : null}
        </>
      ) : null}
      {props.state === 'ready' && contest ? (
        <div className="grid min-w-0 gap-5 lg:grid-cols-3">
          <div className="min-w-0 space-y-5 lg:col-span-2">
            <Card className="min-w-0 space-y-4 break-words p-6">
              <ScheduleBadge state={contest.state} />
              {contest.description ? (
                <p className="whitespace-pre-wrap text-body">{contest.description}</p>
              ) : null}
              <dl className="space-y-3 text-body-sm">
                <div>
                  <dt className="text-ink-secondary">Bắt đầu · giờ Việt Nam</dt>
                  <dd>{formatVietnamDate(contest.startsAt)}</dd>
                </div>
                <div>
                  <dt className="text-ink-secondary">Kết thúc · giờ Việt Nam</dt>
                  <dd>{formatVietnamDate(contest.endsAt)}</dd>
                </div>
                <div>
                  <dt className="text-ink-secondary">Đề thi</dt>
                  <dd>{contest.examTitle}</dd>
                </div>
                <div>
                  <dt className="text-ink-secondary">Số câu</dt>
                  <dd>{contest.questionCount} câu</dd>
                </div>
                <div>
                  <dt className="text-ink-secondary">Thời lượng</dt>
                  <dd>
                    {contest.durationMinutes > 0
                      ? contest.durationMinutes + ' phút, tối đa đến giờ kết thúc'
                      : 'Đến giờ kết thúc cuộc thi'}
                  </dd>
                </div>
              </dl>
            </Card>
            <Card className="space-y-3">
              <h2 className="flex items-center gap-2 text-h3">
                <Trophy aria-hidden="true" className="size-5 text-brand-700" />
                Giải thưởng
              </h2>
              {contest.prizes.length ? (
                <ul className="space-y-3">
                  {contest.prizes.map((prize, index) => (
                    <li key={index} className="break-words">
                      <p className="font-semibold">
                        Hạng {prize.fromRank}
                        {prize.toRank !== prize.fromRank ? '–' + prize.toRank : ''}
                      </p>
                      <p className="text-body-sm text-ink-secondary">{prize.description}</p>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-body-sm text-ink-secondary">Chưa có thông tin giải thưởng.</p>
              )}
            </Card>
          </div>
          <Card className="min-w-0 space-y-4 self-start p-6">
            {contest.state !== 'ENDED' && nextBoundary ? (
              <div className="rounded-md bg-brand-50 p-4 text-brand-700">
                <p className="flex items-center gap-2 text-body-sm">
                  <Clock3 aria-hidden="true" className="size-4" />
                  {contest.state === 'UPCOMING' ? 'Bắt đầu sau' : 'Cuộc thi kết thúc sau'}
                </p>
                <p className="mt-2 text-h1-sm tabular-nums">
                  {formatCountdown(Date.parse(nextBoundary) - props.now)}
                </p>
              </div>
            ) : (
              <p className="font-semibold">Cuộc thi đã kết thúc</p>
            )}
            <p className="flex flex-wrap items-center gap-2 text-body-sm">
              <Users aria-hidden="true" className="size-4" />
              {contest.participantCount} người tham gia
              {contest.maxParticipants !== null ? ' / ' + contest.maxParticipants : ''}
            </p>
            <p className="text-body-sm text-ink-secondary">
              Mỗi người chỉ thi một lần. Vào muộn sẽ có ít thời gian làm bài hơn.
            </p>
            {mine?.status === 'IN_PROGRESS' ? (
              <p role="status" className="text-body-sm">
                Bạn có lượt thi đang mở. Vào thi để tiếp tục.
              </p>
            ) : null}
            {previousAttempt ? (
              <p role="status" className="text-body-sm">
                Bạn đã sử dụng lượt thi của cuộc thi này.
              </p>
            ) : null}
            {contest.rankingPublished || mine?.status === 'SUBMITTED' ? (
              <Button
                to={'/contests/' + contest.id + '/results'}
                variant="outline"
                className="w-full"
              >
                Xem kết quả
              </Button>
            ) : null}
          </Card>
        </div>
      ) : null}
    </main>
  )
}
