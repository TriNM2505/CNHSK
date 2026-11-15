import { displayTime } from '@/features/grading/types'
import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, Card, PageHeader, Pagination } from '@/shared/ui'
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
export function TeacherGradingQueueView(props: Props) {
  return (
    <div className="mx-auto min-w-0 max-w-content space-y-6 p-4 sm:p-6">
      <PageHeader
        title="Hàng đợi chấm bài"
        subtitle="Nhận bài viết và theo dõi các bài bạn đang chấm."
      />
      <Card className="min-w-0 space-y-3">
        <h2 className="text-h3">Bài tôi đang giữ</h2>
        {props.mineState === 'loading' ? <p role="status">Đang tải bài đang giữ…</p> : null}
        {props.mineState === 'error' ? (
          <div role="alert">
            <p>{props.mineError?.message ?? 'Không tải được bài đang giữ.'}</p>
            <Button variant="outline" onClick={props.onRetryMine}>
              Thử lại bài đang giữ
            </Button>
          </div>
        ) : null}
        {props.mineState === 'empty' ? <p>Bạn chưa giữ bài nào.</p> : null}
        {props.mineState === 'ready' ? (
          <ul className="space-y-3">
            {props.mine?.map((item) => (
              <li
                key={item.id}
                className="flex min-w-0 flex-wrap items-center justify-between gap-2 border-b border-line pb-3"
              >
                <div className="min-w-0 break-words">
                  <p className="font-semibold">{item.reference}</p>
                  <p>{item.hanziCount} chữ Hán</p>
                  <p>Nhận lúc: {displayTime(item.assignedAt)}</p>
                  <p>Hạn chấm: {displayTime(item.gradingDeadline)}</p>
                </div>
                <Button to={`/teacher/grading/${item.id}`} variant="outline">
                  Chấm {item.reference}
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
      </Card>
      <section className="min-w-0 space-y-4" aria-label="Bài chờ nhận">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-h3">Bài chờ nhận</h2>
          <Button onClick={props.onRetry} variant="outline">
            Làm mới hàng đợi
          </Button>
        </div>
        {props.claimError ? (
          <p role="alert" className="break-words text-danger">
            {props.claimError.errorCode === 'GRADING_ALREADY_CLAIMED'
              ? 'Bài đã có người nhận'
              : props.claimError.message}
          </p>
        ) : null}
        {props.state === 'loading' ? <p role="status">Đang tải hàng đợi…</p> : null}
        {props.state === 'error' ? (
          <div role="alert">
            <p>{props.error?.message ?? 'Không tải được hàng đợi.'}</p>
            <Button onClick={props.onRetry}>Thử lại hàng đợi</Button>
          </div>
        ) : null}
        {props.state === 'empty' ? <p role="status">Chưa có bài chờ nhận.</p> : null}
        {props.state === 'ready' && props.queue ? (
          <>
            <Card className="min-w-0">
              <div
                className="overflow-x-auto"
                role="region"
                aria-label="Bảng bài chờ nhận"
                tabIndex={0}
              >
                <table className="w-full min-w-[42rem] text-left text-body-sm">
                  <thead>
                    <tr>
                      {[
                        'Mã yêu cầu',
                        'Chữ Hán',
                        'HSK',
                        'Gửi lúc',
                        'Hạn nhận',
                        'Điểm trả',
                        'Thao tác',
                      ].map((label) => (
                        <th className="px-3 py-2" scope="col" key={label}>
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {props.queue.items.map((item) => (
                      <tr className="border-t border-line" key={item.id}>
                        <th className="px-3 py-3" scope="row">
                          {item.reference}
                        </th>
                        <td className="px-3 py-3">{item.hanziCount}</td>
                        <td className="px-3 py-3">{item.hskLevel ?? 'Chưa chọn'}</td>
                        <td className="px-3 py-3">{displayTime(item.sentAt)}</td>
                        <td className="px-3 py-3">{displayTime(item.claimDeadline)}</td>
                        <td className="px-3 py-3">{item.payoutCredits} điểm</td>
                        <td className="px-3 py-3">
                          <Button
                            disabled={props.pending}
                            onClick={() => props.onClaim(String(item.id))}
                          >
                            Nhận {item.reference}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
            <Pagination
              page={props.page + 1}
              size={props.queue.size}
              total={props.queue.totalElements}
              onChange={(page) => props.onPage(page - 1)}
            />
          </>
        ) : null}
      </section>
    </div>
  )
}
