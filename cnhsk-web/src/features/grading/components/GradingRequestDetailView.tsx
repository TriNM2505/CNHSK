import { Award, CheckCircle2, Clock, FileText, RefreshCw, Sparkles } from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  PageBanner,
  Button,
  Card,
  ConfirmPanel,
  ErrorState,
  LevelBadge,
  Skeleton,
  StatusBadge,
  type HskLevel,
  type StatusTone,
} from '@/shared/ui'
import { displayTime, statusLabels, type GradingRequest, type RequestStatus } from '../types'

type Props = {
  state: ApiQueryState
  request?: GradingRequest
  error?: ApiError
  cancelError?: ApiError
  pending: boolean
  confirming: boolean
  onRetry: () => void
  onReviewCancel: () => void
  onCloseCancel: () => void
  onCancel: () => void
}

const statusTones: Readonly<Record<RequestStatus, StatusTone>> = {
  PENDING: 'warning',
  ASSIGNED: 'info',
  COMPLETED: 'success',
  FAILED: 'danger',
  EXPIRED: 'neutral',
}

function toHskLevel(level: number | null): HskLevel | null {
  if (level === null || !Number.isInteger(level) || level < 1 || level > 9) return null
  return level as HskLevel
}

export function GradingRequestDetailView(props: Props) {
  const row = props.request
  const result = row?.status === 'COMPLETED' ? row.result : null
  const badgeLevel = row ? toHskLevel(row.hskLevel) : null

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6 lg:px-8">
      <PageBanner
        subtitle="Xem toàn bộ nội dung bài viết và nhận xét chi tiết từ giáo viên."
        title="Chi tiết yêu cầu chấm bài"
      />

      {props.state === 'loading' ? (
        <div className="space-y-3" role="status">
          <p className="text-body-sm text-ink-secondary">Đang tải bài viết…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}

      {props.state === 'error' ? (
        <div role="alert">
          <ErrorState
            message={props.error?.message ?? 'Không tải được yêu cầu.'}
            onRetry={props.onRetry}
            requestId={props.error?.requestId}
            title="Chưa tải được yêu cầu chấm bài"
          />
        </div>
      ) : null}

      {props.state === 'ready' && row ? (
        <>
          {props.cancelError ? (
            <p
              className="rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
              role="alert"
            >
              {props.cancelError.message}
            </p>
          ) : null}

          <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
            <div className="min-w-0 space-y-5 lg:col-span-5">
              <Card className="min-w-0 space-y-4 border-brand-100 bg-brand-50 p-5 break-words sm:p-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                      <FileText aria-hidden="true" className="size-5" />
                    </div>
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h2 className="text-h3 text-ink">{row.reference}</h2>
                        {badgeLevel !== null ? <LevelBadge exact level={badgeLevel} /> : null}
                      </div>
                      <p className="text-body-sm text-ink-secondary">
                        Gửi lúc: {displayTime(row.createdAt)}
                      </p>
                    </div>
                  </div>
                  <StatusBadge tone={statusTones[row.status]}>
                    {statusLabels[row.status]}
                  </StatusBadge>
                </div>

                <div className="space-y-2 border-t border-brand-100 pt-3 text-body-sm text-ink">
                  {row.deadline ? (
                    <p className="flex items-center gap-2">
                      <Clock aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
                      <span>
                        {row.status === 'PENDING' ? 'Hạn nhận bài' : 'Hạn chấm bài'}:{' '}
                        {displayTime(row.deadline)}
                      </span>
                    </p>
                  ) : null}
                  {row.completedAt ? (
                    <p className="flex items-center gap-2">
                      <CheckCircle2 aria-hidden="true" className="size-4 shrink-0 text-success" />
                      <span>Hoàn thành lúc: {displayTime(row.completedAt)}</span>
                    </p>
                  ) : null}
                  <p className="text-ink-secondary">
                    Hoàn lượt: {row.refunded ? 'Đã hoàn' : 'Chưa hoàn'}
                  </p>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-brand-100 pt-3">
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" to="/grading" variant="outline">
                      Yêu cầu của tôi
                    </Button>
                    <Button onClick={props.onRetry} size="sm" variant="outline">
                      <RefreshCw
                        aria-hidden="true"
                        className="mr-1.5 inline-block size-4 align-middle text-brand-600"
                      />
                      Làm mới
                    </Button>
                  </div>
                  {row.status === 'PENDING' ? (
                    <Button
                      disabled={props.pending}
                      onClick={props.onReviewCancel}
                      size="sm"
                      variant="danger"
                    >
                      Huỷ yêu cầu
                    </Button>
                  ) : null}
                </div>
              </Card>

              {row.prompt !== null ? (
                <Card className="min-w-0 space-y-3 p-5 sm:p-6">
                  <h2 className="text-h3 text-ink">Đề bài</h2>
                  <p className="whitespace-pre-wrap break-words text-body text-ink-secondary [overflow-wrap:anywhere]">
                    {row.prompt}
                  </p>
                </Card>
              ) : null}

              <Card className="min-w-0 space-y-3 p-5 sm:p-6">
                <div className="flex items-center justify-between gap-2 border-b border-line pb-3">
                  <h2 className="text-h3 text-ink">Bài viết</h2>
                  <span className="inline-flex items-center gap-1.5 rounded-pill bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
                    <Sparkles aria-hidden="true" className="size-3.5" />
                    Nội dung gốc
                  </span>
                </div>
                <p className="rounded-md border border-brand-100 bg-brand-50 p-4 whitespace-pre-wrap break-words font-hanzi text-body text-ink [overflow-wrap:anywhere]">
                  {row.essay}
                </p>
              </Card>
            </div>

            <div className="min-w-0 space-y-5 lg:col-span-7">
              {result ? (
                <Card className="min-w-0 space-y-5 border-brand-100 bg-surface p-6">
                  <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-4">
                    <div className="flex items-center gap-2.5">
                      <div className="flex size-10 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-brand-50 text-brand-600">
                        <Award aria-hidden="true" className="size-5" />
                      </div>
                      <h2 className="text-h3 text-ink">Kết quả chấm bài</h2>
                    </div>
                    <p className="rounded-pill border border-brand-100 bg-brand-50 px-4 py-1 text-h2 tabular-nums text-brand-700">
                      {result.score}/100
                    </p>
                  </div>

                  <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {(
                      [
                        ['Ngữ pháp', result.criteria.grammar],
                        ['Từ vựng', result.criteria.vocabulary],
                        ['Mạch lạc', result.criteria.coherence],
                        ['Chữ viết', result.criteria.writing],
                      ] as const
                    ).map(([label, score]) => (
                      <div
                        className="rounded-md border border-brand-100 bg-brand-50 p-3.5"
                        key={label}
                      >
                        <dt className="text-caption text-ink-secondary">{label}</dt>
                        <dd className="mt-1 text-h3 font-semibold tabular-nums text-ink">
                          {score}/25
                        </dd>
                      </div>
                    ))}
                  </dl>

                  <div className="space-y-2">
                    <h3 className="text-h3 text-ink">Nhận xét</h3>
                    <p className="rounded-md border border-line bg-canvas p-4 whitespace-pre-wrap break-words text-body text-ink [overflow-wrap:anywhere]">
                      {result.feedback}
                    </p>
                  </div>

                  <div className="space-y-3">
                    <h3 className="text-h3 text-ink">Sửa lỗi</h3>
                    {result.corrections.length === 0 ? (
                      <p className="text-body-sm text-ink-secondary">Không có mục sửa lỗi.</p>
                    ) : (
                      <div className="space-y-3">
                        {result.corrections.map((item, index) => (
                          <div
                            className="min-w-0 rounded-md border border-brand-100 bg-brand-50 p-4 [overflow-wrap:anywhere]"
                            key={index}
                          >
                            <p className="whitespace-pre-wrap font-hanzi text-body text-ink">
                              <strong className="font-sans text-label text-ink-secondary">
                                Nguyên văn:{' '}
                              </strong>
                              {item.original}
                            </p>
                            <p className="mt-2 whitespace-pre-wrap font-hanzi text-body text-brand-800">
                              <strong className="font-sans text-label text-brand-700">
                                Đề xuất:{' '}
                              </strong>
                              {item.corrected}
                            </p>
                            {item.note ? (
                              <p className="mt-2 whitespace-pre-wrap text-body-sm text-ink-secondary">
                                <strong className="text-label text-ink">Ghi chú: </strong>
                                {item.note}
                              </p>
                            ) : null}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </Card>
              ) : (
                <Card className="space-y-4 border-brand-100 bg-brand-50 p-6">
                  <div className="flex items-center gap-3">
                    <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                      <Clock aria-hidden="true" className="size-5" />
                    </div>
                    <div>
                      <h2 className="text-h3 text-ink">Tiến trình chấm bài</h2>
                      <p className="text-body-sm text-ink-secondary" role="status">
                        Kết quả chỉ hiển thị sau khi giáo viên chấm xong.
                      </p>
                    </div>
                  </div>
                  <div className="grid gap-3 border-t border-brand-100 pt-4 sm:grid-cols-2">
                    <div className="rounded-md border border-brand-100 bg-surface p-3.5">
                      <p className="text-label text-ink">4 tiêu chí đánh giá</p>
                      <p className="mt-1 text-body-sm text-ink-secondary">
                        Ngữ pháp · Từ vựng · Mạch lạc · Chữ viết (mỗi mục 25 điểm)
                      </p>
                    </div>
                    <div className="rounded-md border border-brand-100 bg-surface p-3.5">
                      <p className="text-label text-ink">Nhận xét & Sửa lỗi</p>
                      <p className="mt-1 text-body-sm text-ink-secondary">
                        Phản hồi chi tiết từ giáo viên ngay khi hoàn tất chấm
                      </p>
                    </div>
                  </div>
                </Card>
              )}
            </div>
          </div>

          <ConfirmPanel
            confirmLabel="Xác nhận huỷ"
            danger
            message="Chỉ huỷ được khi chưa có giáo viên nhận. Lượt chấm hoặc điểm đã dùng sẽ được hoàn theo điều kiện dịch vụ."
            onCancel={props.onCloseCancel}
            onConfirm={props.onCancel}
            open={props.confirming}
            pending={props.pending}
            title="Xác nhận huỷ yêu cầu"
          />
        </>
      ) : null}

      {props.state !== 'ready' ? (
        <div className="flex flex-wrap gap-2">
          <Button to="/grading" variant="outline">
            Yêu cầu của tôi
          </Button>
          <Button onClick={props.onRetry} variant="outline">
            Làm mới
          </Button>
        </div>
      ) : null}
    </main>
  )
}
