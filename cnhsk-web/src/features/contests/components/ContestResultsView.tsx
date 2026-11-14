import { Medal, Trophy } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiQueryState } from '@/shared/api'
import { Button, Card, PageHeader } from '@/shared/ui'

import { formatCountdown, formatVietnamDate } from '../countdown'
import type { ContestResults } from '../types'

type Props = { state: ApiQueryState; result?: ContestResults; error?: string; onRetry: () => void }
const statusLabels = {
  IN_PROGRESS: 'Đang làm bài',
  SUBMITTED: 'Đã nộp bài',
  EXPIRED: 'Lượt thi đã hết hạn',
  ABANDONED: 'Lượt thi đã kết thúc',
}

export function ContestResultsView({ state, result, error, onRetry }: Props) {
  const mine = result?.mine
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 sm:px-6">
      <PageHeader title="Kết quả cuộc thi" subtitle={result?.contest.name} />
      {state === 'loading' ? <p role="status">Đang tải kết quả…</p> : null}
      {state === 'error' ? (
        <Card role="alert" className="space-y-3">
          <p>{error ?? 'Không tải được kết quả.'}</p>
          <Button variant="outline" onClick={onRetry}>
            Thử lại
          </Button>
        </Card>
      ) : null}
      {(state === 'ready' || state === 'empty') && result ? (
        <>
          {!result.published ? (
            <Card className="space-y-3 border-brand-100 bg-brand-50">
              <h2 className="text-h2 text-brand-700">Đang chờ công bố kết quả</h2>
              <p>
                Ban tổ chức sẽ công bố bảng xếp hạng sau khi cuộc thi kết thúc và hoàn tất xét kết
                quả.
              </p>
              <p className="text-body-sm">
                {mine ? statusLabels[mine.status] : 'Bạn chưa tham gia cuộc thi này.'}
              </p>
              {mine?.submittedAt ? (
                <p className="text-body-sm">
                  Nộp lúc {formatVietnamDate(mine.submittedAt)} · giờ Việt Nam
                </p>
              ) : null}
              <Button onClick={onRetry} variant="outline">
                Tải lại kết quả
              </Button>
            </Card>
          ) : (
            <>
              <Card className="space-y-4 border-brand-100 bg-brand-50">
                <p className="flex items-center gap-2 font-semibold text-brand-700">
                  <Trophy aria-hidden="true" className="size-5" />
                  Kết quả đã được công bố
                </p>
                {mine ? (
                  <>
                    <p>{statusLabels[mine.status]}</p>
                    <dl className="grid grid-cols-1 gap-3 min-[640px]:grid-cols-3">
                      <div className="flex items-end justify-between gap-3 min-[640px]:block">
                        <dt className="text-body-sm text-ink-secondary">Điểm của bạn</dt>
                        <dd className="whitespace-nowrap text-h2">
                          {mine.score === null
                            ? 'Chưa có điểm'
                            : mine.score + (mine.maxScore === null ? '' : ' / ' + mine.maxScore)}
                        </dd>
                      </div>
                      <div className="flex items-end justify-between gap-3 min-[640px]:block">
                        <dt className="text-body-sm text-ink-secondary">Thời gian làm bài</dt>
                        <dd className="whitespace-nowrap text-h2">
                          {mine.durationMs === null ? 'Chưa có' : formatCountdown(mine.durationMs)}
                        </dd>
                      </div>
                      <div className="flex items-end justify-between gap-3 min-[640px]:block">
                        <dt className="text-body-sm text-ink-secondary">Hạng của bạn</dt>
                        <dd className="whitespace-nowrap text-h2">
                          {mine.rank === null ? 'Không có hạng' : '#' + mine.rank}
                        </dd>
                      </div>
                    </dl>
                    {mine.excluded ? <p>Lượt của bạn không được tính vào bảng xếp hạng.</p> : null}
                  </>
                ) : (
                  <p>Bạn chưa tham gia cuộc thi này.</p>
                )}
                <p className="text-body-sm">
                  Ban tổ chức sẽ liên hệ người đạt giải qua email đăng ký.
                </p>
              </Card>
              <section aria-labelledby="contest-ranking-title" className="space-y-3">
                <h2 id="contest-ranking-title" className="flex items-center gap-2 text-h2">
                  <Medal aria-hidden="true" className="size-5 text-brand-700" />
                  Bảng xếp hạng
                </h2>
                {result.ranking?.items.length ? (
                  <>
                    <ol className="space-y-3" aria-label="Bảng xếp hạng cuộc thi">
                      {result.ranking.items.map((row) => (
                        <li
                          key={row.userId}
                          className="grid min-w-0 grid-cols-[2.5rem_minmax(0,1fr)] items-start gap-3 rounded-md border border-line bg-surface p-4 min-[641px]:grid-cols-[2.5rem_minmax(0,1fr)_6rem_6rem]"
                        >
                          <span className="flex size-10 items-center justify-center rounded-pill bg-brand-50 font-semibold text-brand-700">
                            {row.rank}
                          </span>
                          <p className="min-w-0 self-center break-words font-semibold [overflow-wrap:anywhere]">
                            {row.displayName ?? 'Người học'}
                          </p>
                          <p className="col-start-2 text-body-sm min-[641px]:col-start-auto">
                            <span className="text-ink-secondary">Điểm: </span>
                            {row.score} / {row.maxScore}
                          </p>
                          <p className="col-start-2 text-body-sm min-[641px]:col-start-auto">
                            <span className="text-ink-secondary">Thời gian: </span>
                            {row.durationMs === null ? 'Chưa có' : formatCountdown(row.durationMs)}
                          </p>
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
          <nav aria-label="Điều hướng kết quả" className="flex flex-wrap gap-3">
            <Button to={'/contests/' + result.contest.id} variant="outline">
              Thông tin cuộc thi
            </Button>
            <Link to="/contests" className="inline-flex min-h-touch items-center">
              Cuộc thi khác
            </Link>
          </nav>
        </>
      ) : null}
    </main>
  )
}
