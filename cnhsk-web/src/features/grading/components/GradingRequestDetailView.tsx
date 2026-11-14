import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, Card, ConfirmPanel, PageHeader } from '@/shared/ui'
import { displayTime, statusLabels, type GradingRequest } from '../types'

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

export function GradingRequestDetailView(props: Props) {
  const row = props.request
  const result = row?.status === 'COMPLETED' ? row.result : null
  return (
    <div className="mx-auto min-w-0 max-w-reading space-y-5 p-4 sm:p-6">
      <PageHeader title="Chi tiết yêu cầu chấm bài" />
      <div className="flex flex-wrap gap-2">
        <Button to="/grading" variant="outline">
          Yêu cầu của tôi
        </Button>
        <Button variant="outline" onClick={props.onRetry}>
          Làm mới
        </Button>
      </div>
      {props.state === 'loading' ? <p role="status">Đang tải bài viết…</p> : null}
      {props.state === 'error' ? (
        <div role="alert">
          <p>{props.error?.message ?? 'Không tải được yêu cầu.'}</p>
          <Button onClick={props.onRetry}>Thử lại</Button>
        </div>
      ) : null}
      {props.state === 'ready' && row ? (
        <>
          <Card className="min-w-0 space-y-2 break-words">
            <h2 className="text-h3">{row.reference}</h2>
            <p>{statusLabels[row.status]}</p>
            <p>Gửi lúc: {displayTime(row.createdAt)}</p>
            {row.hskLevel !== null ? <p>HSK {row.hskLevel}</p> : null}
            {row.deadline ? (
              <p>
                {row.status === 'PENDING' ? 'Hạn nhận bài' : 'Hạn chấm bài'}:{' '}
                {displayTime(row.deadline)}
              </p>
            ) : null}
            {row.completedAt ? <p>Hoàn thành lúc: {displayTime(row.completedAt)}</p> : null}
            <p>Hoàn lượt: {row.refunded ? 'Đã hoàn' : 'Chưa hoàn'}</p>
          </Card>
          {row.prompt !== null ? (
            <Card className="min-w-0">
              <h2 className="text-h3">Đề bài</h2>
              <p className="mt-2 whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
                {row.prompt}
              </p>
            </Card>
          ) : null}
          <Card className="min-w-0">
            <h2 className="text-h3">Bài viết</h2>
            <p className="mt-2 whitespace-pre-wrap break-words font-hanzi [overflow-wrap:anywhere]">
              {row.essay}
            </p>
          </Card>
          {result ? (
            <Card className="min-w-0 space-y-4">
              <h2 className="text-h3">Kết quả chấm bài</h2>
              <p className="text-h2">{result.score}/100</p>
              <dl className="grid grid-cols-2 gap-3">
                {(
                  [
                    ['Ngữ pháp', result.criteria.grammar],
                    ['Từ vựng', result.criteria.vocabulary],
                    ['Mạch lạc', result.criteria.coherence],
                    ['Chữ viết', result.criteria.writing],
                  ] as const
                ).map(([label, score]) => (
                  <div key={label}>
                    <dt className="text-body-sm text-ink-secondary">{label}</dt>
                    <dd className="font-semibold">{score}/25</dd>
                  </div>
                ))}
              </dl>
              <h3 className="text-h3">Nhận xét</h3>
              <p className="whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
                {result.feedback}
              </p>
              <h3 className="text-h3">Sửa lỗi</h3>
              {result.corrections.length === 0 ? (
                <p>Không có mục sửa lỗi.</p>
              ) : (
                <div className="space-y-3">
                  {result.corrections.map((item, index) => (
                    <div
                      key={index}
                      className="min-w-0 rounded-sm border border-line p-3 [overflow-wrap:anywhere]"
                    >
                      <p className="whitespace-pre-wrap">
                        <strong>Nguyên văn: </strong>
                        {item.original}
                      </p>
                      <p className="mt-2 whitespace-pre-wrap">
                        <strong>Đề xuất: </strong>
                        {item.corrected}
                      </p>
                      {item.note ? (
                        <p className="mt-2 whitespace-pre-wrap">
                          <strong>Ghi chú: </strong>
                          {item.note}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              )}
            </Card>
          ) : (
            <p role="status">Kết quả chỉ hiển thị sau khi giáo viên chấm xong.</p>
          )}
          {props.cancelError ? (
            <p role="alert" className="text-danger">
              {props.cancelError.message}
            </p>
          ) : null}
          {row.status === 'PENDING' ? (
            <Button variant="danger" disabled={props.pending} onClick={props.onReviewCancel}>
              Huỷ yêu cầu
            </Button>
          ) : null}
          <ConfirmPanel
            open={props.confirming}
            pending={props.pending}
            danger
            title="Xác nhận huỷ yêu cầu"
            confirmLabel="Xác nhận huỷ"
            message="Chỉ huỷ được khi chưa có giáo viên nhận. Lượt chấm hoặc điểm đã dùng sẽ được hoàn theo điều kiện dịch vụ."
            onCancel={props.onCloseCancel}
            onConfirm={props.onCancel}
          />
        </>
      ) : null}
    </div>
  )
}
