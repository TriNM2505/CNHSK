import { Clock3, Download, FileUp, History, TriangleAlert } from 'lucide-react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import {
  Button,
  ConfirmPanel,
  DataTable,
  type DataTableColumn,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatTile,
  StatusBadge,
  type StatusTone,
} from '@/shared/ui'

import { formatImportCountdown, formatImportDate } from '../formatters'
import type { ImportMappedError, ImportSelectionErrors } from '../importFlow'
import {
  IMPORT_DATA_TYPE_LABELS,
  IMPORT_ROW_ACTION_LABELS,
  IMPORT_RUN_STATUS_LABELS,
} from '../labels'
import type {
  ImportDataType,
  ImportPreview,
  ImportPreviewRow,
  ImportRowError,
  ImportRun,
  ImportRunPage,
  ImportRunStatus,
  ImportTemplate,
} from '../types'
import { CellText } from './CellText'

export type ImportSectionState = 'loading' | 'empty' | 'error' | 'ready'

export interface ImportLoadError {
  message: string
  requestId?: string
}

export interface ImportHistoryProps {
  data?: ImportRunPage
  error?: ImportLoadError
  onOpenRun: (runId: number) => void
  onPageChange: (page: number) => void
  onRetry: () => void
  state: ImportSectionState
}

export interface LearningDataImportViewProps {
  acknowledgeRerun: boolean
  /** Lỗi khiến lần kiểm tra không dùng được nữa (hết hạn, không còn PENDING…). */
  confirmBlockedMessage?: string
  confirmError?: ImportMappedError
  confirmOpen: boolean
  confirmPending: boolean
  dataType: ImportDataType | ''
  downloadError?: string
  downloadPending: boolean
  fileInputKey: number
  history: ImportHistoryProps
  loadError?: ImportLoadError
  onAcknowledgeChange: (value: boolean) => void
  onCancelConfirm: () => void
  onCancelPreview: () => void
  onConfirm: () => void
  onDataTypeChange: (value: ImportDataType | '') => void
  onDownloadTemplate: () => void
  onFileChange: (file: File | undefined) => void
  onRequestConfirm: () => void
  onRetry: () => void
  onUpload: () => void
  preview?: ImportPreview
  remainingSeconds: number
  selectionErrors: ImportSelectionErrors
  state: ImportSectionState
  templates: readonly ImportTemplate[]
  uploadError?: ImportMappedError
  uploading: boolean
}

const RUN_STATUS_TONES: Record<ImportRunStatus, StatusTone> = {
  COMPLETED: 'success',
  EXPIRED: 'neutral',
  FAILED: 'danger',
  PENDING: 'info',
  RUNNING: 'info',
}

function RequestId({ requestId }: { requestId?: string }) {
  return requestId ? <span className="mt-1 block text-caption">Mã lỗi: {requestId}</span> : null
}

function InlineAlert({ error }: { error: ImportMappedError }) {
  return (
    <div
      className="flex items-start gap-2 rounded-md border border-danger bg-danger-bg p-3 text-body-sm text-danger"
      role="alert"
    >
      <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <p className="min-w-0">
        {error.message}
        <RequestId requestId={error.requestId} />
      </p>
    </div>
  )
}

function TemplateGuide({
  onDownloadTemplate,
  downloadError,
  downloadPending,
  template,
}: {
  downloadError?: string
  downloadPending: boolean
  onDownloadTemplate: () => void
  template: ImportTemplate
}) {
  return (
    <div className="mt-4 rounded-md border border-line bg-canvas p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-h3 text-ink">
            Cột của mẫu {IMPORT_DATA_TYPE_LABELS[template.dataType]}
          </h3>
          <p className="mt-1 text-body-sm text-ink-secondary">
            Khoá nhận diện: <span className="font-semibold">{template.naturalKey.join(', ')}</span>
            {template.dependsOn.length ? (
              <>
                {' · '}Cần nhập trước:{' '}
                {template.dependsOn.map((type) => IMPORT_DATA_TYPE_LABELS[type]).join(', ')}
              </>
            ) : null}
          </p>
        </div>
        <Button loading={downloadPending} onClick={onDownloadTemplate} size="sm" variant="outline">
          <Download aria-hidden="true" className="size-4" />
          Tải mẫu
        </Button>
      </div>
      {downloadError ? (
        <p className="mt-2 text-body-sm text-danger" role="alert">
          {downloadError}
        </p>
      ) : null}
      <DataTable
        caption={`Danh sách cột mẫu ${IMPORT_DATA_TYPE_LABELS[template.dataType]}`}
        className="mt-4"
        columns={[
          {
            header: 'Tên cột',
            id: 'name',
            render: (column) => <code className="break-all text-body-sm">{column.name}</code>,
          },
          {
            header: 'Bắt buộc',
            id: 'required',
            render: (column) => (column.required ? 'Có' : 'Không'),
          },
          {
            header: 'Tối đa',
            hideOnMobile: true,
            id: 'maxLength',
            numeric: true,
            render: (column) => (column.maxLength ? `${column.maxLength} ký tự` : '—'),
          },
          {
            header: 'Mô tả',
            id: 'description',
            render: (column) => <CellText value={column.description} />,
          },
          {
            header: 'Ví dụ',
            hideOnMobile: true,
            id: 'example',
            render: (column) => <CellText value={column.example} />,
          },
        ]}
        rowKey={(column) => column.name}
        rows={template.columns}
      />
    </div>
  )
}

function previewColumns(template?: ImportTemplate): DataTableColumn<ImportPreviewRow>[] {
  const valueColumns: DataTableColumn<ImportPreviewRow>[] = (template?.columns ?? []).map(
    (column) => ({
      header: column.name,
      id: `value-${column.name}`,
      render: (row) => <CellText value={row.values[column.name]} />,
    }),
  )
  return [
    { header: 'Dòng', id: 'line', numeric: true, render: (row) => row.line },
    ...valueColumns,
    {
      header: 'Hành động',
      id: 'action',
      render: (row) => (
        <StatusBadge tone={row.action === 'UNCHANGED' ? 'neutral' : 'info'}>
          {IMPORT_ROW_ACTION_LABELS[row.action]}
        </StatusBadge>
      ),
    },
  ]
}

const errorColumns: DataTableColumn<ImportRowError>[] = [
  { header: 'Dòng', id: 'line', numeric: true, render: (error) => error.line },
  { header: 'Cột', id: 'column', render: (error) => <CellText value={error.column} /> },
  { header: 'Giá trị', id: 'value', render: (error) => <CellText value={error.value} /> },
  { header: 'Lỗi', id: 'message', render: (error) => error.message },
]

function PreviewResult({
  acknowledgeRerun,
  confirmBlockedMessage,
  confirmError,
  onAcknowledgeChange,
  onCancelPreview,
  onRequestConfirm,
  preview,
  remainingSeconds,
  template,
}: Pick<
  LearningDataImportViewProps,
  | 'acknowledgeRerun'
  | 'confirmBlockedMessage'
  | 'confirmError'
  | 'onAcknowledgeChange'
  | 'onCancelPreview'
  | 'onRequestConfirm'
  | 'remainingSeconds'
> & { preview: ImportPreview; template?: ImportTemplate }) {
  const expired = remainingSeconds <= 0
  const blockedMessage =
    confirmBlockedMessage ??
    (expired ? 'Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.' : undefined)
  const confirmDisabled =
    Boolean(blockedMessage) ||
    preview.totals.valid === 0 ||
    (preview.alreadyImported && !acknowledgeRerun)
  const totals = [
    ['Tổng số dòng', preview.totals.total],
    ['Hợp lệ', preview.totals.valid],
    ['Không hợp lệ', preview.totals.invalid],
    ['Tạo mới', preview.totals.create],
    ['Cập nhật', preview.totals.update],
    ['Không đổi', preview.totals.unchanged],
  ] as const

  return (
    <section
      aria-labelledby="import-preview-title"
      className="mt-6 rounded-md border border-line bg-surface p-4 sm:p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-h2 text-ink" id="import-preview-title">
            Kết quả kiểm tra
          </h2>
          <p className="mt-1 break-all text-body-sm text-ink-secondary">
            {IMPORT_DATA_TYPE_LABELS[preview.dataType]} · {preview.fileName}
          </p>
        </div>
        {blockedMessage ? null : (
          <p className="flex items-center gap-2 text-body-sm text-ink-secondary">
            <Clock3 aria-hidden="true" className="size-4" />
            Còn hiệu lực{' '}
            <span className="font-semibold tabular-nums text-ink">
              {formatImportCountdown(remainingSeconds)}
            </span>
          </p>
        )}
      </div>

      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {totals.map(([label, value]) => (
          <StatTile key={label} label={label} value={value} />
        ))}
      </div>

      {blockedMessage ? (
        <div
          className="mt-4 flex items-start gap-2 rounded-md border border-danger bg-danger-bg p-3 text-body-sm text-danger"
          role="alert"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <p>{blockedMessage}</p>
        </div>
      ) : null}

      <h3 className="mt-6 text-h3 text-ink">Xem trước dòng hợp lệ</h3>
      {preview.preview.length ? (
        <DataTable
          caption="Xem trước các dòng hợp lệ đầu tiên"
          className="mt-3"
          columns={previewColumns(template)}
          rowKey={(row) => row.line}
          rows={preview.preview}
        />
      ) : (
        <p className="mt-2 text-body-sm text-ink-secondary">File không có dòng hợp lệ nào.</p>
      )}

      {preview.errorCount > 0 ? (
        <>
          <div className="mt-6 flex flex-wrap items-baseline justify-between gap-2">
            <h3 className="text-h3 text-ink">Dòng lỗi ({preview.errorCount})</h3>
            <Link
              className="text-body-sm font-semibold text-brand-700 underline underline-offset-2"
              to={`/content/imports/${preview.runId}`}
            >
              Xem tất cả {preview.errorCount} lỗi
            </Link>
          </div>
          <DataTable
            caption="Các lỗi đầu tiên trong file"
            className="mt-3"
            columns={errorColumns}
            rowKey={(error) => `${error.line}-${error.column ?? ''}-${error.code}`}
            rows={preview.errors}
          />
        </>
      ) : null}

      {preview.alreadyImported ? (
        <div className="mt-6 rounded-md border border-warning bg-warning-bg p-4 text-body-sm text-warning">
          <p className="flex items-start gap-2 font-semibold">
            <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            File này đã được nhập thành công trước đó.
          </p>
          <label className="mt-2 flex min-h-touch cursor-pointer items-center gap-3 text-ink">
            <input
              checked={acknowledgeRerun}
              className="size-5 shrink-0 accent-brand-700"
              disabled={Boolean(blockedMessage)}
              onChange={(event) => onAcknowledgeChange(event.target.checked)}
              type="checkbox"
            />
            Tôi hiểu file này đã nhập trước đó
          </label>
        </div>
      ) : null}

      {confirmError ? (
        <div className="mt-4">
          <InlineAlert error={confirmError} />
        </div>
      ) : null}

      <div className="mt-6 flex flex-wrap gap-3">
        <Button disabled={confirmDisabled} onClick={onRequestConfirm}>
          Xác nhận nhập dữ liệu
        </Button>
        <Button onClick={onCancelPreview} variant="ghost">
          Huỷ
        </Button>
      </div>
    </section>
  )
}

function HistorySection({
  data,
  error,
  onOpenRun,
  onPageChange,
  onRetry,
  state,
}: ImportHistoryProps) {
  const columns: DataTableColumn<ImportRun>[] = [
    { header: 'Lần nhập', id: 'runId', render: (run) => `#${run.runId}` },
    {
      header: 'Loại dữ liệu',
      id: 'dataType',
      render: (run) => (run.dataType === null ? '—' : IMPORT_DATA_TYPE_LABELS[run.dataType]),
    },
    {
      header: 'Tên file',
      id: 'fileName',
      render: (run) => <span className="break-all">{run.fileName ?? 'Không đọc được tên tệp'}</span>,
    },
    {
      header: 'Người nhập',
      hideOnMobile: true,
      id: 'createdBy',
      render: (run) => run.createdBy.displayName,
    },
    {
      header: 'Thời gian',
      id: 'createdAt',
      render: (run) => <time dateTime={run.createdAt}>{formatImportDate(run.createdAt)}</time>,
    },
    {
      header: 'Trạng thái',
      id: 'status',
      render: (run) => (
        <StatusBadge tone={RUN_STATUS_TONES[run.status]}>
          {IMPORT_RUN_STATUS_LABELS[run.status]}
        </StatusBadge>
      ),
    },
    { header: 'Số lỗi', id: 'errorCount', numeric: true, render: (run) => run.errorCount },
  ]

  return (
    <section aria-labelledby="import-history-title" className="mt-8">
      <h2 className="mb-3 flex items-center gap-2 text-h2 text-ink" id="import-history-title">
        <History aria-hidden="true" className="size-5" />
        Lịch sử nhập
      </h2>
      {state === 'loading' ? <Skeleton variant="table" /> : null}
      {state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không tải được lịch sử nhập. Vui lòng thử lại.'}
          onRetry={onRetry}
          requestId={error?.requestId}
        />
      ) : null}
      {state === 'empty' ? (
        <EmptyState
          description="Các lần tải file lên sẽ xuất hiện tại đây."
          title="Chưa có lần nhập dữ liệu"
        />
      ) : null}
      {state === 'ready' && data ? (
        <>
          <DataTable
            caption="Lịch sử các lần nhập dữ liệu"
            columns={columns}
            getRowLabel={(run) => `Mở báo cáo lần nhập #${run.runId}`}
            onRowClick={(run) => onOpenRun(run.runId)}
            rowKey={(run) => run.runId}
            rows={data.items}
          />
          <div className="mt-6">
            <Pagination
              label="Phân trang lịch sử nhập"
              onChange={(page) => onPageChange(page - 1)}
              page={data.page + 1}
              size={data.size || 10}
              total={data.totalElements}
            />
          </div>
        </>
      ) : null}
    </section>
  )
}

export function LearningDataImportView({
  acknowledgeRerun,
  confirmBlockedMessage,
  confirmError,
  confirmOpen,
  confirmPending,
  dataType,
  downloadError,
  downloadPending,
  fileInputKey,
  history,
  loadError,
  onAcknowledgeChange,
  onCancelConfirm,
  onCancelPreview,
  onConfirm,
  onDataTypeChange,
  onDownloadTemplate,
  onFileChange,
  onRequestConfirm,
  onRetry,
  onUpload,
  preview,
  remainingSeconds,
  selectionErrors,
  state,
  templates,
  uploadError,
  uploading,
}: LearningDataImportViewProps) {
  const selectedTemplate = templates.find((template) => template.dataType === dataType)
  const previewTemplate = preview
    ? templates.find((template) => template.dataType === preview.dataType)
    : undefined

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onUpload()
  }

  return (
    <main className="mx-auto w-full min-w-0 max-w-content p-4 sm:p-6">
      <PageHeader
        subtitle="Tải file CSV theo mẫu, kiểm tra trước rồi mới ghi vào kho dữ liệu học."
        title="Nhập dữ liệu học tập"
      />

      {state === 'loading' ? <Skeleton variant="card" /> : null}
      {state === 'error' ? (
        <ErrorState
          message={loadError?.message ?? 'Không tải được danh sách mẫu. Vui lòng thử lại.'}
          onRetry={onRetry}
          requestId={loadError?.requestId}
        />
      ) : null}
      {state === 'empty' ? (
        <EmptyState
          description="Hệ thống chưa trả về mẫu nhập nào. Vui lòng liên hệ quản trị viên."
          title="Chưa có mẫu nhập dữ liệu"
        />
      ) : null}

      {state === 'ready' ? (
        <section
          aria-label="Tải file lên"
          className="rounded-md border border-line bg-surface p-4 sm:p-6"
        >
          <form noValidate onSubmit={handleSubmit}>
            <div className="grid gap-x-4 sm:grid-cols-2">
              <FormField
                error={selectionErrors.dataType}
                htmlFor="import-data-type"
                label="Loại dữ liệu"
                required
              >
                <Select
                  disabled={uploading}
                  onChange={(event) => onDataTypeChange(event.target.value as ImportDataType | '')}
                  value={dataType}
                >
                  <option value="">Chọn loại dữ liệu</option>
                  {templates.map((template) => (
                    <option key={template.dataType} value={template.dataType}>
                      {IMPORT_DATA_TYPE_LABELS[template.dataType]}
                    </option>
                  ))}
                </Select>
              </FormField>

              <FormField
                error={selectionErrors.file}
                hint="Chỉ nhận file .csv mã hoá UTF-8, tối đa 2 MB và 5.000 dòng."
                htmlFor="import-file"
                label="File CSV"
                required
              >
                <Input
                  accept=".csv"
                  disabled={uploading}
                  key={fileInputKey}
                  onChange={(event) => onFileChange(event.target.files?.[0])}
                  type="file"
                />
              </FormField>
            </div>

            {selectedTemplate ? (
              <TemplateGuide
                downloadError={downloadError}
                downloadPending={downloadPending}
                onDownloadTemplate={onDownloadTemplate}
                template={selectedTemplate}
              />
            ) : null}

            <p className="mt-4 text-caption text-ink-muted">
              Chỉ tải lên dữ liệu bạn có quyền sử dụng. Hệ thống không lưu lại file gốc.
            </p>

            {uploadError ? (
              <div className="mt-4">
                <InlineAlert error={uploadError} />
              </div>
            ) : null}

            <div className="mt-4">
              <Button loading={uploading} type="submit">
                <FileUp aria-hidden="true" className="size-4" />
                {uploading ? 'Đang kiểm tra' : 'Tải lên và kiểm tra'}
              </Button>
            </div>
          </form>
        </section>
      ) : null}

      {state === 'ready' && preview ? (
        <PreviewResult
          acknowledgeRerun={acknowledgeRerun}
          confirmBlockedMessage={confirmBlockedMessage}
          confirmError={confirmError}
          onAcknowledgeChange={onAcknowledgeChange}
          onCancelPreview={onCancelPreview}
          onRequestConfirm={onRequestConfirm}
          preview={preview}
          remainingSeconds={remainingSeconds}
          template={previewTemplate}
        />
      ) : null}

      <HistorySection {...history} />

      <ConfirmPanel
        confirmLabel="Ghi vào kho"
        message={`Ghi ${preview?.totals.valid ?? 0} dòng vào kho? Không hoàn tác được.`}
        onCancel={onCancelConfirm}
        onConfirm={onConfirm}
        open={confirmOpen && Boolean(preview)}
        pending={confirmPending}
        title="Xác nhận nhập dữ liệu"
      />
    </main>
  )
}
