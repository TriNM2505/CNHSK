import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, Card, FormField, PageHeader, Pagination, Select } from '@/shared/ui'
import { displayTime, statusLabels, type GradingRequestPage, type RequestStatus } from '../types'

type Props = {
  state: ApiQueryState
  result?: GradingRequestPage
  error?: ApiError
  status: RequestStatus | ''
  page: number
  onStatus: (value: RequestStatus | '') => void
  onPage: (page: number) => void
  onRetry: () => void
}

export function MyGradingRequestsView(props: Props) {
  return (
    <div className="mx-auto min-w-0 max-w-content space-y-5 p-4 sm:p-6">
      <PageHeader
        title="Yêu cầu chấm bài của tôi"
        subtitle="Theo dõi bài đã gửi và đọc góp ý của giáo viên."
      />
      <div className="flex flex-wrap gap-2">
        <Button to="/grading/new">Gửi bài mới</Button>
        <Button variant="outline" onClick={props.onRetry}>
          Làm mới
        </Button>
      </div>
      <div className="max-w-xs">
        <FormField htmlFor="grading-status" label="Trạng thái">
          <Select
            value={props.status}
            onChange={(event) => props.onStatus(event.target.value as RequestStatus | '')}
          >
            <option value="">Tất cả</option>
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
      </div>
      {props.state === 'loading' ? <p role="status">Đang tải yêu cầu…</p> : null}
      {props.state === 'error' ? (
        <div role="alert">
          <p>{props.error?.message ?? 'Không tải được yêu cầu.'}</p>
          <Button onClick={props.onRetry}>Thử lại</Button>
        </div>
      ) : null}
      {props.state === 'empty' ? <p role="status">Chưa có yêu cầu phù hợp.</p> : null}
      {props.state === 'ready' && props.result ? (
        <>
          <div className="grid min-w-0 gap-4 lg:grid-cols-2">
            {props.result.items.map((item) => (
              <Card
                key={item.id}
                className="min-w-0 space-y-2 break-words [overflow-wrap:anywhere]"
              >
                <h2 className="text-h3">
                  <Button to={`/grading/${item.id}`} variant="ghost">
                    {item.reference}
                  </Button>
                </h2>
                <p>{statusLabels[item.status]}</p>
                <p className="whitespace-pre-wrap">{item.essayPreview}</p>
                <p>Gửi lúc: {displayTime(item.createdAt)}</p>
                {item.deadline ? (
                  <p>
                    {item.status === 'PENDING' ? 'Hạn nhận bài' : 'Hạn chấm bài'}:{' '}
                    {displayTime(item.deadline)}
                  </p>
                ) : null}
                {item.score !== null ? <p>Điểm: {item.score}/100</p> : null}
                {item.completedAt ? <p>Hoàn thành lúc: {displayTime(item.completedAt)}</p> : null}
              </Card>
            ))}
          </div>
          <Pagination
            page={props.page + 1}
            size={props.result.size}
            total={props.result.totalElements}
            onChange={(page) => props.onPage(page - 1)}
          />
        </>
      ) : null}
    </div>
  )
}
