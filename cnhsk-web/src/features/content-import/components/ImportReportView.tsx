import { Download, FilePenLine, RefreshCw } from 'lucide-react'

import {
  Button,
  DataTable,
  type DataTableColumn,
  EmptyState,
  ErrorState,
  PageHeader,
  Pagination,
  Skeleton,
  StatTile,
  StatusBadge,
  type StatusTone,
} from '@/shared/ui'

import { formatImportDate } from '../formatters'
import { getImportFailureMessage } from '../importFlow'
import { IMPORT_ERROR_PAGE_SIZE } from '../importReportQuery'
import { IMPORT_DATA_TYPE_LABELS, IMPORT_RUN_STATUS_LABELS } from '../labels'
import type { ImportErrorPage, ImportRowError, ImportRun, ImportRunStatus } from '../types'
import { CellText } from './CellText'
import type { ImportSectionState, ImportLoadError } from './LearningDataImportView'

const STATUS_TONES: Record<ImportRunStatus, StatusTone> = {
  COMPLETED: 'success',
  EXPIRED: 'neutral',
  FAILED: 'danger',
  PENDING: 'info',
  RUNNING: 'info',
}

const errorColumns: readonly DataTableColumn<ImportRowError>[] = [
  { header: 'Dòng', id: 'line', numeric: true, render: (error) => error.line },
  { header: 'Cột', id: 'column', render: (error) => <CellText value={error.column} /> },
  { header: 'Giá trị', id: 'value', render: (error) => <CellText value={error.value} /> },
  { header: 'Mã lỗi', id: 'code', render: (error) => <CellText value={error.code} /> },
  { header: 'Thông báo', id: 'message', render: (error) => <CellText value={error.message} /> },
]

export interface ImportReportViewProps {
  downloadError?: ImportLoadError
  downloadPending: boolean
  error?: ImportLoadError
  errors: {
    data?: ImportErrorPage
    error?: ImportLoadError
    onFirstPage: () => void
    onPageChange: (page: number) => void
    onRetry: () => void
    state: ImportSectionState
  }
  onDownloadErrors: () => void
  onReload: () => void
  run?: ImportRun
  state: ImportSectionState
}

function RunOutcome({ run }: { run: ImportRun }) {
  if (run.status === 'COMPLETED' && run.errorCount === 0 && run.result) {
    const imported = run.result.created + run.result.updated + run.result.unchanged
    return (
      <p className="rounded-md border border-success bg-success-bg p-4 text-body-sm text-success">
        Nhập thành công {imported} dòng
      </p>
    )
  }
  if (run.status === 'FAILED') {
    return (
      <p className="rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger">
        {getImportFailureMessage(run.failureReason)}
      </p>
    )
  }

  const descriptions: Partial<Record<ImportRunStatus, string>> = {
    COMPLETED: 'Quá trình nhập đã hoàn tất. Các dòng lỗi có thể được sửa và nhập lại.',
    EXPIRED: 'Kết quả kiểm tra đã hết hạn trước khi được xác nhận.',
    PENDING: 'File đã được kiểm tra và đang chờ xác nhận nhập dữ liệu.',
    RUNNING: 'Hệ thống đang ghi các dòng hợp lệ. Hãy tải lại để xem trạng thái mới nhất.',
  }
  const description = descriptions[run.status]
  return description ? (
    <p className="rounded-md border border-line bg-canvas p-4 text-body-sm text-ink-secondary">
      {description}
    </p>
  ) : null
}

function RunSummary({ run }: { run: ImportRun }) {
  const total = run.result?.total ?? run.totals?.total
  return (
    <>
      <section
        aria-labelledby="import-run-summary-title"
        className="rounded-md border border-line bg-surface p-4 sm:p-6"
      >
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-h2 text-ink" id="import-run-summary-title">
              Tóm tắt lần nhập #{run.runId}
            </h2>
            <p className="mt-1 break-all text-body-sm text-ink-secondary">
              {run.fileName ?? 'Không đọc được tên tệp'}
            </p>
          </div>
          <StatusBadge tone={STATUS_TONES[run.status]}>
            {IMPORT_RUN_STATUS_LABELS[run.status]}
          </StatusBadge>
        </div>

        <dl className="mt-5 grid gap-4 text-body-sm sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <dt className="text-ink-muted">Loại dữ liệu</dt>
            <dd className="mt-1 font-semibold text-ink">
              {run.dataType === null ? '—' : IMPORT_DATA_TYPE_LABELS[run.dataType]}
            </dd>
          </div>
          <div>
            <dt className="text-ink-muted">Người nhập</dt>
            <dd className="mt-1 font-semibold text-ink">{run.createdBy.displayName}</dd>
          </div>
          <div>
            <dt className="text-ink-muted">Thời điểm tạo</dt>
            <dd className="mt-1 font-semibold text-ink">
              <time dateTime={run.createdAt}>{formatImportDate(run.createdAt)}</time>
            </dd>
          </div>
          <div>
            <dt className="text-ink-muted">Thời điểm hoàn thành</dt>
            <dd className="mt-1 font-semibold text-ink">
              {run.completedAt ? (
                <time dateTime={run.completedAt}>{formatImportDate(run.completedAt)}</time>
              ) : (
                '—'
              )}
            </dd>
          </div>
        </dl>

        {total !== undefined ? (
          <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            <StatTile label="Tổng số dòng" value={total} />
            {run.result ? (
              <>
                <StatTile label="Tạo mới" value={run.result.created} />
                <StatTile label="Cập nhật" value={run.result.updated} />
                <StatTile label="Không đổi" value={run.result.unchanged} />
                <StatTile label="Lỗi ghi" value={run.result.failed} />
              </>
            ) : (
              <>
                <StatTile label="Hợp lệ" value={run.totals?.valid ?? 0} />
                <StatTile label="Không hợp lệ" value={run.totals?.invalid ?? 0} />
              </>
            )}
          </div>
        ) : null}
      </section>

      <div className="mt-4">
        <RunOutcome run={run} />
      </div>
    </>
  )
}

function ErrorList({ errors }: Pick<ImportReportViewProps, 'errors'>) {
  return (
    <section aria-labelledby="import-errors-title" className="mt-8">
      <h2 className="mb-3 text-h2 text-ink" id="import-errors-title">
        Chi tiết dòng lỗi
      </h2>

      {errors.state === 'loading' ? <Skeleton variant="table" /> : null}
      {errors.state === 'error' ? (
        <ErrorState
          message={errors.error?.message ?? 'Không tải được danh sách lỗi. Vui lòng thử lại.'}
          onRetry={errors.onRetry}
          requestId={errors.error?.requestId}
        />
      ) : null}
      {errors.state === 'empty' && (errors.data?.totalElements ?? 0) > 0 ? (
        <EmptyState
          action={<Button onClick={errors.onFirstPage}>Về trang đầu</Button>}
          description="Trang đã chọn nằm ngoài phạm vi dữ liệu hiện có."
          title="Trang này không có dữ liệu"
        />
      ) : null}
      {errors.state === 'empty' && (errors.data?.totalElements ?? 0) === 0 ? (
        <EmptyState description="Lần nhập này không có lỗi cần sửa." title="Không có dòng lỗi" />
      ) : null}
      {errors.state === 'ready' && errors.data ? (
        <>
          <p className="mb-3 text-body-sm text-ink-secondary">Có {errors.data.totalElements} lỗi</p>
          <DataTable
            caption="Danh sách lỗi của lần nhập"
            columns={errorColumns}
            rowKey={(row) => `${row.line}-${row.column ?? ''}-${row.code}-${row.value ?? ''}`}
            rows={errors.data.items}
          />
          <div className="mt-6">
            <Pagination
              label="Phân trang danh sách lỗi nhập"
              onChange={errors.onPageChange}
              page={errors.data.page + 1}
              size={errors.data.size || IMPORT_ERROR_PAGE_SIZE}
              total={errors.data.totalElements}
            />
          </div>
        </>
      ) : null}
    </section>
  )
}

export function ImportReportView({
  downloadError,
  downloadPending,
  error,
  errors,
  onDownloadErrors,
  onReload,
  run,
  state,
}: ImportReportViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content p-4 sm:p-6">
      <PageHeader
        subtitle="Xem kết quả đã ghi và các dòng cần sửa trong file nguồn."
        title="Báo cáo nhập dữ liệu"
      />

      {state === 'loading' ? <Skeleton variant="card" /> : null}
      {state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không tải được báo cáo lần nhập. Vui lòng thử lại.'}
          onRetry={onReload}
          requestId={error?.requestId}
        />
      ) : null}
      {state === 'empty' ? (
        <EmptyState
          action={
            <Button to="/content/imports" variant="outline">
              Về trang nhập dữ liệu
            </Button>
          }
          description="Lần nhập này không tồn tại hoặc không còn khả dụng."
          title="Không tìm thấy lần nhập"
        />
      ) : null}

      {state === 'ready' && run ? (
        <>
          <RunSummary run={run} />

          <div className="mt-6 flex flex-wrap gap-3">
            {run.errorCount > 0 ? (
              <Button loading={downloadPending} onClick={onDownloadErrors}>
                <Download aria-hidden="true" className="size-4" />
                {downloadPending ? 'Đang tải' : 'Tải danh sách lỗi'}
              </Button>
            ) : null}
            <Button
              to={run.dataType === null ? '/content/imports' : `/content/imports?dataType=${run.dataType}`}
              variant="outline"
            >
              <FilePenLine aria-hidden="true" className="size-4" />
              Sửa file và nhập lại
            </Button>
            {run.status === 'RUNNING' ? (
              <Button onClick={onReload} variant="outline">
                <RefreshCw aria-hidden="true" className="size-4" />
                Tải lại
              </Button>
            ) : null}
          </div>

          {downloadError ? (
            <p className="mt-3 text-body-sm text-danger" role="alert">
              {downloadError.message}
              {downloadError.requestId ? ` Mã lỗi: ${downloadError.requestId}` : ''}
            </p>
          ) : null}

          <ErrorList errors={errors} />
        </>
      ) : null}
    </main>
  )
}
