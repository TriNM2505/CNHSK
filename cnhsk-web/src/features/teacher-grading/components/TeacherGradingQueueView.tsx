import {
  ArrowRight,
  Clock,
  FileCheck2,
  FileText,
  Inbox,
  RefreshCw,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'

import { displayTime } from '@/features/grading/types'
import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  PageBanner,
  Button,
  Card,
  EmptyState,
  LevelBadge,
  Pagination,
  Skeleton,
  StatusBadge,
  type HskLevel,
} from '@/shared/ui'
import type { AssignedItem, QueuePage } from '../types'

type Props = {
  state: ApiQueryState
  queue?: QueuePage
  error?: ApiError
  mineState: ApiQueryState
  mine?: AssignedItem[]
  mineError?: ApiError
  claimError?: ApiError
  pending: boolean
  page: number
  onPage: (page: number) => void
  onClaim: (id: string) => void
  onRetry: () => void
  onRetryMine: () => void
}

function toHskLevel(level: number | null): HskLevel | null {
  if (level === null || !Number.isInteger(level) || level < 1 || level > 9) return null
  return level as HskLevel
}

export function TeacherGradingQueueView(props: Props) {
  const mineCount = props.mine?.length ?? 0
  const queueTotal = props.queue?.totalElements ?? 0

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner
        subtitle="Nhận bài viết và theo dõi các bài bạn đang chấm."
        title="Hàng đợi chấm bài"
      />

      <div className="grid min-w-0 gap-4 sm:grid-cols-2">
        <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
          <div className="flex min-w-0 items-start gap-4">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
              <FileCheck2 aria-hidden="true" className="size-6" />
            </div>
            <div className="min-w-0">
              <p className="text-h1 tabular-nums text-ink">{mineCount}</p>
              <p className="mt-0.5 text-label text-ink">Bài tôi đang giữ</p>
              <p className="mt-0.5 text-body-sm text-ink-secondary">
                Ưu tiên chấm đúng hạn đã nhận
              </p>
            </div>
          </div>
          <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
            Đang giữ
          </span>
        </Card>

        <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
          <div className="flex min-w-0 items-start gap-4">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
              <Inbox aria-hidden="true" className="size-6" />
            </div>
            <div className="min-w-0">
              <p className="text-h1 tabular-nums text-ink">{queueTotal}</p>
              <p className="mt-0.5 text-label text-ink">Bài chờ nhận</p>
              <p className="mt-0.5 text-body-sm text-ink-secondary">
                Sẵn sàng nhận chấm trong hàng đợi
              </p>
            </div>
          </div>
          <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
            Hàng đợi
          </span>
        </Card>
      </div>

      <Card className="min-w-0 space-y-4 border-brand-100 bg-brand-50 p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
              <Clock aria-hidden="true" className="size-5" />
            </div>
            <div>
              <h2 className="text-h3 text-ink">Bài tôi đang giữ</h2>
              <p className="text-body-sm text-ink-secondary">
                Các bài viết đã nhận cần hoàn tất trước hạn chấm
              </p>
            </div>
          </div>
        </div>

        {props.mineState === 'loading' ? (
          <div className="space-y-3" role="status">
            <p className="text-body-sm text-ink-secondary">Đang tải bài đang giữ…</p>
            <Skeleton variant="card" />
          </div>
        ) : null}

        {props.mineState === 'error' ? (
          <div
            className="space-y-3 rounded-md border border-danger bg-danger-bg p-4 text-danger"
            role="alert"
          >
            <p className="text-body-sm">
              {props.mineError?.message ?? 'Không tải được bài đang giữ.'}
            </p>
            <Button onClick={props.onRetryMine} size="sm" variant="outline">
              Thử lại bài đang giữ
            </Button>
          </div>
        ) : null}

        {props.mineState === 'empty' ? (
          <div className="rounded-md border border-brand-100 bg-surface p-4 text-body-sm text-ink-secondary">
            <p>Bạn chưa giữ bài nào.</p>
          </div>
        ) : null}

        {props.mineState === 'ready' ? (
          <div className="grid min-w-0 gap-3 sm:grid-cols-2">
            <ul className="contents">
              {props.mine?.map((item) => (
                <li
                  className="flex min-w-0 flex-col justify-between gap-3 rounded-md border border-brand-100 bg-surface p-4"
                  key={item.id}
                >
                  <div className="min-w-0 space-y-1.5 break-words">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="flex items-center gap-2 text-h3 font-semibold text-ink">
                        <FileText aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                        <span>{item.reference}</span>
                      </p>
                      <StatusBadge tone="info">
                        {item.mode === 'SPEAKING' ? 'Bài nói · ' : ''}
                        {item.hanziCount} chữ Hán
                      </StatusBadge>
                    </div>
                    <p className="text-body-sm text-ink-secondary">
                      Nhận lúc: {displayTime(item.assignedAt)}
                    </p>
                    <p className="text-body-sm font-medium text-brand-700">
                      Hạn chấm: {displayTime(item.gradingDeadline)}
                    </p>
                  </div>
                  <div className="border-t border-line pt-3">
                    <Button size="sm" to={`/teacher/grading/${item.id}`} variant="primary">
                      Chấm {item.reference}
                      <ArrowRight
                        aria-hidden="true"
                        className="ml-1 inline-block size-4 align-middle"
                      />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
            {mineCount === 1 ? (
              <div className="flex min-w-0 flex-col justify-between gap-3 rounded-md border border-brand-100 bg-surface p-4">
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <ShieldCheck aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
                    <p className="text-label text-ink">Quy định nhận & chấm bài</p>
                  </div>
                  <p className="text-body-sm text-ink-secondary">
                    Mỗi bài nhận cần hoàn tất chấm theo 4 tiêu chí (Ngữ pháp, Từ vựng, Mạch lạc, Chữ
                    viết) trước thời hạn 24 giờ.
                  </p>
                </div>
                <p className="border-t border-line pt-3 text-caption text-ink-secondary">
                  Nếu chưa thể chấm kịp, bạn có thể mở bài và chọn trả lại hàng đợi để giáo viên
                  khác nhận.
                </p>
              </div>
            ) : null}
          </div>
        ) : null}
      </Card>

      <section aria-label="Bài chờ nhận" className="min-w-0 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-surface p-4 sm:px-5">
          <div className="flex items-center gap-2">
            <Sparkles aria-hidden="true" className="size-5 text-brand-600" />
            <h2 className="text-h3 text-ink">Bài chờ nhận</h2>
          </div>
          <Button onClick={props.onRetry} size="sm" variant="outline">
            <RefreshCw
              aria-hidden="true"
              className="mr-1.5 inline-block size-4 align-middle text-brand-600"
            />
            Làm mới hàng đợi
          </Button>
        </div>

        {props.claimError ? (
          <p
            className="rounded-md border border-danger bg-danger-bg p-4 break-words text-body-sm text-danger"
            role="alert"
          >
            {props.claimError.errorCode === 'GRADING_ALREADY_CLAIMED'
              ? 'Bài đã có người nhận'
              : props.claimError.message}
          </p>
        ) : null}

        {props.state === 'loading' ? (
          <div className="space-y-3" role="status">
            <p className="text-body-sm text-ink-secondary">Đang tải hàng đợi…</p>
            <Skeleton variant="table" />
          </div>
        ) : null}

        {props.state === 'error' ? (
          <div
            className="space-y-3 rounded-md border border-danger bg-danger-bg p-4 text-danger"
            role="alert"
          >
            <p className="text-body-sm">{props.error?.message ?? 'Không tải được hàng đợi.'}</p>
            <Button onClick={props.onRetry} size="sm" variant="outline">
              Thử lại hàng đợi
            </Button>
          </div>
        ) : null}

        {props.state === 'empty' ? (
          <div role="status">
            <EmptyState
              description="Hiện chưa có bài viết mới trong hàng đợi. Bạn có thể bấm làm mới để kiểm tra lại."
              icon={<Inbox aria-hidden="true" className="size-5" />}
              title="Chưa có bài chờ nhận."
            />
          </div>
        ) : null}

        {props.state === 'ready' && props.queue ? (
          <>
            <Card className="min-w-0 border-brand-100 p-0 overflow-hidden">
              <div
                aria-label="Bảng bài chờ nhận"
                className="overflow-x-auto"
                role="region"
                tabIndex={0}
              >
                <table className="block w-full min-w-0 text-left text-body-sm lg:table">
                  <thead className="sr-only lg:not-sr-only lg:table-header-group">
                    <tr className="border-b border-brand-100 bg-brand-50 text-label text-ink">
                      {[
                        'Mã yêu cầu',
                        'Chữ Hán',
                        'HSK',
                        'Gửi lúc',
                        'Hạn nhận',
                        'Điểm trả',
                        'Thao tác',
                      ].map((label) => (
                        <th className="px-4 py-3" key={label} scope="col">
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="block divide-y divide-line lg:table-row-group">
                    {props.queue.items.map((item) => {
                      const badgeLevel = toHskLevel(item.hskLevel)
                      return (
                        <tr
                          className="block space-y-2 p-4 transition-colors hover:bg-surface-subtle lg:table-row lg:space-y-0 lg:p-0"
                          key={item.id}
                        >
                          <th
                            className="flex items-center justify-between gap-2 font-semibold text-ink lg:table-cell lg:px-4 lg:py-3.5"
                            scope="row"
                          >
                            <span className="text-label text-ink-secondary lg:hidden">
                              Mã yêu cầu:
                            </span>
                            <span className="text-ink">{item.reference}</span>
                          </th>
                          <td className="flex items-center justify-between gap-2 tabular-nums lg:table-cell lg:px-4 lg:py-3.5">
                            <span className="text-label text-ink-secondary lg:hidden">
                              Chữ Hán:
                            </span>
                            <span>{item.hanziCount}</span>
                          </td>
                          <td className="flex items-center justify-between gap-2 lg:table-cell lg:px-4 lg:py-3.5">
                            <span className="text-label text-ink-secondary lg:hidden">HSK:</span>
                            <span>
                              {badgeLevel !== null ? (
                                <LevelBadge exact level={badgeLevel} />
                              ) : (
                                (item.hskLevel ?? 'Chưa chọn')
                              )}
                            </span>
                          </td>
                          <td className="flex items-center justify-between gap-2 text-ink-secondary lg:table-cell lg:px-4 lg:py-3.5">
                            <span className="text-label text-ink-secondary lg:hidden">
                              Gửi lúc:
                            </span>
                            <span>{displayTime(item.sentAt)}</span>
                          </td>
                          <td className="flex items-center justify-between gap-2 text-ink-secondary lg:table-cell lg:px-4 lg:py-3.5">
                            <span className="text-label text-ink-secondary lg:hidden">
                              Hạn nhận:
                            </span>
                            <span>{displayTime(item.claimDeadline)}</span>
                          </td>
                          <td className="flex items-center justify-between gap-2 font-semibold text-brand-700 tabular-nums lg:table-cell lg:px-4 lg:py-3.5">
                            <span className="text-label text-ink-secondary lg:hidden">
                              Điểm trả:
                            </span>
                            <span>{item.payoutCredits} điểm</span>
                          </td>
                          <td className="pt-2 lg:table-cell lg:px-4 lg:py-3.5 lg:pt-0">
                            <Button
                              disabled={props.pending}
                              onClick={() => props.onClaim(String(item.id))}
                              size="sm"
                            >
                              Nhận {item.reference}
                            </Button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </Card>
            <Pagination
              onChange={(page) => props.onPage(page - 1)}
              page={props.page + 1}
              size={props.queue.size}
              total={props.queue.totalElements}
            />
          </>
        ) : null}
      </section>
    </main>
  )
}
