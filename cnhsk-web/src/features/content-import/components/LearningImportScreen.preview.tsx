import { useState } from 'react'
import { Button } from '@/shared/ui'
import type { ImportDataType, ImportPreview } from '../types'
import { LearningDataImportView, type LearningDataImportViewProps } from './LearningDataImportView'

export function LearningImportScreenPreview({
  example,
  samplePreview,
  fallbackHistory,
}: {
  example: LearningDataImportViewProps
  samplePreview: ImportPreview
  fallbackHistory: LearningDataImportViewProps['history']
}) {
  const [state, setState] = useState(example.state)
  const [dataType, setDataType] = useState<ImportDataType | ''>(example.dataType)
  const [file, setFile] = useState<File>()
  const [fileInputKey, setFileInputKey] = useState(0)
  const [preview, setPreview] = useState(example.preview)
  const [acknowledge, setAcknowledge] = useState(example.acknowledgeRerun)
  const [confirmOpen, setConfirmOpen] = useState(example.confirmOpen)
  const [selectionErrors, setSelectionErrors] = useState(example.selectionErrors)
  const [historyState, setHistoryState] = useState(example.history.state)
  const [historyPage, setHistoryPage] = useState(example.history.data?.page ?? 0)
  const [notice, setNotice] = useState('')
  const [openedReport, setOpenedReport] = useState<number>()
  const busy = example.uploading || example.confirmPending
  const clearPreview = () => {
    setPreview(undefined)
    setConfirmOpen(false)
    setAcknowledge(false)
    setSelectionErrors({})
  }
  const showSample = () => {
    if (busy) return
    setDataType(samplePreview.dataType)
    setPreview(samplePreview)
    setConfirmOpen(false)
    setAcknowledge(false)
    setSelectionErrors({})
    setNotice('Đang hiển thị kết quả kiểm tra mẫu, không đọc hoặc gửi file thật.')
  }
  const historyData = example.history.data ?? fallbackHistory.data
  return (
    <div
      onClickCapture={(event) => {
        if (!(event.target instanceof Element)) return
        const anchor = event.target.closest('a')
        const match = anchor?.getAttribute('href')?.match(/^\/content\/imports\/(\d+)(?:[?#].*)?$/)
        if (match) {
          event.preventDefault()
          event.stopPropagation()
          if (!busy) setOpenedReport(Number(match[1]))
        }
      }}
    >
      <div className="mx-4 mt-4 space-y-3 rounded-md border border-info bg-info-bg p-4 text-body-sm text-info sm:mx-6 lg:mx-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p>Bản xem trước · Chỉ mô phỏng dữ liệu mẫu, không nhập vào kho thật.</p>
          <Button disabled={busy} variant="outline" onClick={showSample}>
            Xem kết quả mẫu
          </Button>
        </div>
        {notice ? <p role="status">{notice}</p> : null}
        {openedReport !== undefined ? (
          <div className="flex flex-wrap items-center gap-3" role="status">
            Đã chọn báo cáo mẫu #{openedReport}.
            <Button variant="outline" to="/__preview/frame/scr-069-ready/ready">
              Xem mẫu báo cáo nhập
            </Button>
          </div>
        ) : null}
      </div>
      <LearningDataImportView
        {...example}
        state={state}
        dataType={dataType}
        preview={preview}
        acknowledgeRerun={acknowledge}
        confirmOpen={confirmOpen}
        fileInputKey={fileInputKey}
        selectionErrors={selectionErrors}
        templateUnavailable={
          Boolean(dataType) && !example.templates.some((template) => template.dataType === dataType)
        }
        history={{
          ...example.history,
          state: historyState,
          data: historyData ? { ...historyData, page: historyPage } : undefined,
          onRetry: () => setHistoryState(historyData?.items.length ? 'ready' : 'empty'),
          onPageChange: (page) => {
            if (!busy) setHistoryPage(page)
          },
          onOpenRun: (id) => {
            if (!busy) setOpenedReport(id)
          },
        }}
        onRetry={() => setState('ready')}
        onAcknowledgeChange={(value) => {
          setAcknowledge(value)
          if (!value) setConfirmOpen(false)
        }}
        onDataTypeChange={(value) => {
          if (!busy) {
            setDataType(value)
            clearPreview()
          }
        }}
        onFileChange={(value) => {
          if (!busy) {
            setFile(value)
            clearPreview()
          }
        }}
        onDownloadTemplate={() => {
          if (!busy) setNotice('Đã chọn tải mẫu. Bản xem trước không tải file từ máy chủ.')
        }}
        onCancelPreview={() => {
          if (!busy) {
            clearPreview()
            setFile(undefined)
            setFileInputKey((key) => key + 1)
          }
        }}
        onRequestConfirm={() => {
          if (
            !busy &&
            preview &&
            preview.totals.valid > 0 &&
            example.remainingSeconds > 0 &&
            !example.confirmBlockedMessage &&
            (!preview.alreadyImported || acknowledge)
          )
            setConfirmOpen(true)
        }}
        onCancelConfirm={() => {
          if (!example.confirmPending) setConfirmOpen(false)
        }}
        onConfirm={() => {
          if (
            busy ||
            !preview ||
            example.remainingSeconds <= 0 ||
            example.confirmBlockedMessage ||
            preview.totals.valid === 0 ||
            (preview.alreadyImported && !acknowledge)
          )
            return
          setConfirmOpen(false)
          setNotice('Đã xác nhận trong bản mô phỏng. Không có dữ liệu nào được ghi vào kho thật.')
        }}
        onUpload={() => {
          if (busy) return
          if (!dataType || !file) {
            setSelectionErrors({
              dataType: dataType ? undefined : 'Hãy chọn loại dữ liệu.',
              file: file ? undefined : 'Hãy chọn file CSV cần kiểm tra.',
            })
            return
          }
          const template = example.templates.find((item) => item.dataType === dataType)
          if (!template) return
          const values = Object.fromEntries(
            template.columns.map((column) => [column.name, column.example ?? '']),
          )
          setPreview({
            ...samplePreview,
            dataType,
            fileName: file.name,
            alreadyImported: false,
            preview: [{ line: 2, action: 'CREATE', values }],
            errors: [],
            errorCount: 0,
            totals: { total: 1, valid: 1, invalid: 0, create: 1, update: 0, unchanged: 0 },
          })
          setSelectionErrors({})
          setNotice(
            'Kết quả mô phỏng lấy từ ví dụ của mẫu; bản xem trước không đọc nội dung hoặc gửi file đã chọn.',
          )
        }}
      />
    </div>
  )
}
