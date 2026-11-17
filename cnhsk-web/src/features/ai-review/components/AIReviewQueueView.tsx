import { useEffect, useRef, useState } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  ConfirmPanel,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
  type PageStateValue,
} from '@/shared/ui'

import { validReviewDate, type ReviewQueueQuery } from '../reviewQueueQuery'
import type { AIQuestionPage, BulkOutcome, BulkResult } from '../types'

const bulkOutcomeLabels: Record<BulkOutcome, string> = {
  APPROVED: 'Đã duyệt',
  NOT_VIEWED: 'Chưa xem hoặc lượt xem đã hết hạn',
  NOT_PENDING: 'Không còn chờ duyệt',
  MALFORMED: 'Nội dung chưa hợp lệ',
}

function validationMessages(error?: ApiError): string[] {
  if (!Array.isArray(error?.details)) return []
  return error.details.flatMap((detail: unknown) => {
    if (!detail || typeof detail !== 'object' || !('field' in detail) || !('message' in detail))
      return []
    return typeof detail.field === 'string' && typeof detail.message === 'string'
      ? [`${detail.field}: ${detail.message}`]
      : []
  })
}

export type AIReviewQueueViewProps = {
  query: ReviewQueueQuery
  data?: AIQuestionPage
  state: PageStateValue
  error?: ApiError
  selected: readonly number[]
  pending: boolean
  bulkError?: ApiError
  result?: BulkResult
  onQueryChange: (query: ReviewQueueQuery) => void
  onSelect: (id: number, checked: boolean) => void
  onOpen: (id: number) => void
  onRetry: () => void
  onBulkApprove: () => void
}

export function AIReviewQueueView(props: AIReviewQueueViewProps) {
  const {
    query,
    data,
    state,
    error,
    selected,
    pending,
    bulkError,
    result,
    onQueryChange,
    onSelect,
    onOpen,
    onRetry,
    onBulkApprove,
  } = props
  const [draft, setDraft] = useState(query)
  const [filterError, setFilterError] = useState<string>()
  const [confirm, setConfirm] = useState(false)
  const reportRef = useRef<HTMLElement>(null)
  const formRef = useRef<HTMLFormElement>(null)
  useEffect(() => {
    setDraft(query)
  }, [query])
  useEffect(() => {
    if (result || bulkError) reportRef.current?.focus()
  }, [result, bulkError])
  const busy = pending || state === 'loading'
  const change = (field: keyof ReviewQueueQuery, value: string) => {
    setDraft((current) => ({ ...current, [field]: value }))
    setFilterError(undefined)
  }
  return (
    <div className="mx-auto max-w-content space-y-6">
      <PageHeader
        title="Duyệt câu hỏi AI"
        subtitle="Câu hỏi chờ duyệt, theo thứ tự tạo cũ nhất. Mở chi tiết trước khi chọn duyệt hàng loạt."
      />
      <form
        aria-label="Bộ lọc hàng đợi"
        className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
        noValidate
        ref={formRef}
        onSubmit={(event) => {
          event.preventDefault()
          if (busy) return
          const invalid =
            Array.from(draft.kid.trim()).length > 20 ||
            (draft.from !== '' && !validReviewDate(draft.from)) ||
            (draft.to !== '' && !validReviewDate(draft.to)) ||
            (draft.from !== '' && draft.to !== '' && draft.from > draft.to)
          if (invalid) {
            setFilterError(
              'KID tối đa 20 ký tự; ngày phải hợp lệ và ngày kết thúc không trước ngày bắt đầu.',
            )
            requestAnimationFrame(() =>
              formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
            )
            return
          }
          onQueryChange({ ...draft, kid: draft.kid.trim(), page: 0 })
        }}
      >
        <fieldset className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" disabled={busy}>
          <legend className="sr-only">Lọc theo năng lực, HSK và ngày tạo tại Việt Nam</legend>
          <FormField htmlFor="review-kid" label="Mã năng lực (KID)" error={filterError}>
            <Input value={draft.kid} onChange={(event) => change('kid', event.target.value)} />
          </FormField>
          <FormField htmlFor="review-hsk" label="Cấp HSK">
            <Select value={draft.hsk} onChange={(event) => change('hsk', event.target.value)}>
              <option value="">Tất cả</option>
              {SUPPORTED_HSK_LEVELS.map((level) => (
                <option key={level} value={level}>
                  HSK {level}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField htmlFor="review-from" label="Từ ngày (giờ Việt Nam)">
            <Input
              type="date"
              value={draft.from}
              onChange={(event) => change('from', event.target.value)}
            />
          </FormField>
          <FormField htmlFor="review-to" label="Đến ngày (giờ Việt Nam)">
            <Input
              type="date"
              value={draft.to}
              onChange={(event) => change('to', event.target.value)}
            />
          </FormField>
        </fieldset>
        <div className="flex flex-wrap gap-3">
          <Button disabled={busy} type="submit">
            Áp dụng bộ lọc
          </Button>
          <Button
            disabled={busy}
            variant="outline"
            onClick={() => {
              setFilterError(undefined)
              onQueryChange({ kid: '', hsk: '', from: '', to: '', page: 0, size: 20 })
            }}
          >
            Xoá bộ lọc
          </Button>
        </div>
      </form>
      {state === 'loading' ? (
        <div aria-label="Đang tải hàng đợi" role="status" className="space-y-3">
          <Skeleton className="h-20" />
          <Skeleton className="h-64" />
        </div>
      ) : null}
      {state === 'error' ? (
        <ErrorState
          title="Không thể tải hàng đợi"
          message={error?.message ?? 'Vui lòng thử lại.'}
          requestId={error?.requestId}
          onRetry={onRetry}
        />
      ) : null}
      {state === 'error'
        ? validationMessages(error).map((message) => (
            <p key={message} role="alert">
              {message}
            </p>
          ))
        : null}
      {state === 'empty' ? (
        <EmptyState
          title="Không có câu hỏi chờ duyệt"
          description="Chưa có câu hỏi phù hợp với bộ lọc hiện tại."
        />
      ) : null}
      {state === 'ready' && data ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p role="status">
              {data.totalElements} câu hỏi chờ duyệt · Đã chọn {selected.length}/50
            </p>
            <div className="flex flex-wrap gap-3">
              <Button disabled={busy} onClick={onRetry} variant="outline">
                Làm mới hàng đợi
              </Button>
              <Button
                disabled={busy || selected.length === 0}
                loading={pending}
                onClick={() => setConfirm(true)}
              >
                Duyệt các câu đã chọn
              </Button>
            </div>
          </div>
          <DataTable
            caption="Câu hỏi AI chờ duyệt"
            rowKey={(row) => row.id}
            rows={data.items}
            columns={[
              {
                id: 'selection',
                header: 'Chọn',
                render: (row) => (
                  <Input
                    aria-label={`Chọn câu #${row.id}`}
                    type="checkbox"
                    checked={selected.includes(row.id)}
                    disabled={
                      busy ||
                      !row.viewedByMe ||
                      (!selected.includes(row.id) && selected.length >= 50)
                    }
                    onChange={(event) => onSelect(row.id, event.target.checked)}
                  />
                ),
              },
              {
                id: 'question',
                header: 'Câu hỏi',
                render: (row) => (
                  <div className="space-y-2 break-words">
                    <p className="font-hanzi">{row.stemSummary}</p>
                    <Button disabled={busy} onClick={() => onOpen(row.id)} variant="ghost">
                      Mở câu #{row.id}
                    </Button>
                  </div>
                ),
              },
              {
                id: 'level',
                header: 'HSK / Năng lực',
                render: (row) => (
                  <div className="space-y-2 break-words">
                    <p>HSK {row.hskLevel}</p>
                    {row.labels.map((label) => (
                      <p key={`${label.kid}:${label.role}`}>
                        {label.role === 'PRIMARY' ? 'Chính' : 'Phụ'}: {label.kid}
                      </p>
                    ))}
                  </div>
                ),
              },
              {
                id: 'created',
                header: 'Ngày tạo (Việt Nam)',
                render: (row) => (
                  <div className="space-y-2">
                    <time dateTime={row.generatedAt}>
                      {new Intl.DateTimeFormat('vi-VN', {
                        timeZone: 'Asia/Ho_Chi_Minh',
                        dateStyle: 'short',
                        timeStyle: 'short',
                      }).format(new Date(row.generatedAt))}
                    </time>
                    <p>
                      Đã chờ{' '}
                      {Math.max(
                        0,
                        Math.floor((Date.now() - Date.parse(row.generatedAt)) / 3600000),
                      )}{' '}
                      giờ
                    </p>
                  </div>
                ),
              },
              {
                id: 'status',
                header: 'Trạng thái',
                render: (row) => (
                  <div className="space-y-2">
                    <p>AI · Chờ duyệt</p>
                    <p>{row.viewedByMe ? 'Đã xem' : 'Cần mở chi tiết'}</p>
                  </div>
                ),
              },
            ]}
          />
          <Pagination
            page={query.page + 1}
            size={data.size}
            total={data.totalElements}
            onChange={(page) => {
              if (!busy) onQueryChange({ ...query, page: page - 1 })
            }}
          />
        </>
      ) : null}
      {bulkError || result ? (
        <section
          aria-label="Kết quả duyệt hàng loạt"
          aria-live="polite"
          className="space-y-3 rounded-md border border-line bg-surface p-4 sm:p-6 focus:outline-none focus:ring-2 focus:ring-focus"
          ref={reportRef}
          tabIndex={-1}
        >
          <h2 className="text-h3">Kết quả duyệt hàng loạt</h2>
          {bulkError ? (
            <div role="alert">
              <p>{bulkError.message}</p>
              {bulkError.status >= 500 || bulkError.status === 0 ? (
                <p>
                  Một số câu trước đó có thể đã được duyệt. Hệ thống không tự gửi lại. Làm mới thành
                  công rồi chọn lại các câu còn đủ điều kiện.
                </p>
              ) : null}
              {bulkError.requestId ? <p>Mã yêu cầu: {bulkError.requestId}</p> : null}
              <Button disabled={busy} onClick={onRetry} variant="outline">
                Tải lại hàng đợi
              </Button>
            </div>
          ) : null}
          {result ? (
            <ol className="space-y-2">
              {result.results.map(({ id, outcome }) => (
                <li key={id}>
                  #{id}: {bulkOutcomeLabels[outcome]}
                </li>
              ))}
            </ol>
          ) : null}
          {validationMessages(bulkError).map((message) => (
            <p key={message}>{message}</p>
          ))}
        </section>
      ) : null}
      <ConfirmPanel
        open={confirm}
        pending={pending}
        title="Duyệt các câu đã chọn?"
        message={`Duyệt ${selected.length} câu đã mở chi tiết. Máy chủ sẽ kiểm tra lại từng câu.`}
        confirmLabel="Xác nhận duyệt hàng loạt"
        onCancel={() => setConfirm(false)}
        onConfirm={() => {
          setConfirm(false)
          onBulkApprove()
        }}
      />
    </div>
  )
}
