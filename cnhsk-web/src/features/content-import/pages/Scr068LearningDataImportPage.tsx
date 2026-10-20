import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { toApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { downloadBlob } from '@/shared/lib/downloadBlob'

import {
  confirmImportRun,
  getImportRuns,
  getImportTemplateFile,
  getImportTemplates,
  uploadImportFile,
} from '../api/imports'
import { LearningDataImportView } from '../components/LearningDataImportView'
import { getImportRemainingSeconds } from '../formatters'
import {
  type ImportMappedError,
  type ImportSelectionErrors,
  mapConfirmImportError,
  mapImportUploadError,
  validateImportSelection,
} from '../importFlow'
import { isImportDataType } from '../labels'
import type { ImportDataType, ImportPreview } from '../types'

function toLoadError(error: unknown) {
  const apiError = toApiError(error)
  return { message: apiError.message, requestId: apiError.requestId }
}

export default function Scr068LearningDataImportPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const templatesQuery = useApiQuery(getImportTemplates, [], { isEmpty: (data) => !data.length })
  const [historyPage, setHistoryPage] = useState(0)
  const historyQuery = useApiQuery((signal) => getImportRuns(historyPage, signal), [historyPage], {
    isEmpty: (data) => data.totalElements === 0,
  })
  const upload = useApiMutation((input: { dataType: ImportDataType; file: File }) =>
    uploadImportFile(input.dataType, input.file),
  )
  const confirm = useApiMutation((input: { runId: number; acknowledgeRerun: boolean }) =>
    confirmImportRun(input.runId, { acknowledgeRerun: input.acknowledgeRerun }),
  )

  // FR-039: SCR-069 "Sửa file và nhập lại" quay về đây với ?dataType= chọn sẵn.
  const [dataType, setDataType] = useState<ImportDataType | ''>(() => {
    const requested = searchParams.get('dataType')
    return isImportDataType(requested) ? requested : ''
  })
  const [file, setFile] = useState<File>()
  const [fileInputKey, setFileInputKey] = useState(0)
  const [selectionErrors, setSelectionErrors] = useState<ImportSelectionErrors>({})
  const [uploadError, setUploadError] = useState<ImportMappedError>()
  const [downloadPending, setDownloadPending] = useState(false)
  const [downloadError, setDownloadError] = useState<string>()
  const [preview, setPreview] = useState<ImportPreview>()
  const [acknowledgeRerun, setAcknowledgeRerun] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirmError, setConfirmError] = useState<ImportMappedError>()
  const [confirmBlockedMessage, setConfirmBlockedMessage] = useState<string>()
  const [now, setNow] = useState(() => Date.now())

  const templates = templatesQuery.data ?? []
  const remainingSeconds = preview ? getImportRemainingSeconds(preview.expiresAt, now) : 0
  const previewActive = Boolean(preview && !confirmBlockedMessage && remainingSeconds > 0)

  // Loại dữ liệu trên URL nhưng server không có mẫu đó thì bỏ chọn.
  useEffect(() => {
    if (templatesQuery.state !== 'ready' || !dataType) return
    if (!templatesQuery.data?.some((template) => template.dataType === dataType)) setDataType('')
  }, [dataType, templatesQuery.data, templatesQuery.state])

  // Đồng hồ hết hạn chỉ chạy khi còn kết quả kiểm tra dùng được.
  useEffect(() => {
    if (!previewActive) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [previewActive])

  // Hết giờ khi đang mở bước xác nhận thì đóng lại, trừ khi request đã gửi.
  useEffect(() => {
    if (preview && remainingSeconds <= 0 && !confirm.pending) setConfirmOpen(false)
  }, [confirm.pending, preview, remainingSeconds])

  const clearPreview = () => {
    setPreview(undefined)
    setAcknowledgeRerun(false)
    setConfirmOpen(false)
    setConfirmError(undefined)
    setConfirmBlockedMessage(undefined)
  }

  const handleUpload = async () => {
    if (upload.pending) return
    const errors = validateImportSelection(dataType, file)
    setSelectionErrors(errors)
    setUploadError(undefined)
    if (!dataType || !file || Object.keys(errors).length) return

    clearPreview()
    try {
      const result = await upload.run({ dataType, file })
      setNow(Date.now())
      setPreview(result)
      historyQuery.reload()
    } catch (error) {
      setUploadError(mapImportUploadError(error))
      // FR-008: lỗi cấp file vẫn tạo một lần nhập FAILED, lịch sử cần thấy lần thử đó.
      if (toApiError(error).extra.runId !== undefined) historyQuery.reload()
    }
  }

  const handleDownloadTemplate = async () => {
    if (!dataType || downloadPending) return
    setDownloadPending(true)
    setDownloadError(undefined)
    try {
      const response = await getImportTemplateFile(dataType)
      if (response) downloadBlob(response.blob, response.fileName ?? `${dataType}.csv`)
    } catch (error) {
      const apiError = toApiError(error)
      setDownloadError(
        apiError.status === 0 ? apiError.message : 'Chưa tải được file mẫu. Vui lòng thử lại.',
      )
    } finally {
      setDownloadPending(false)
    }
  }

  const handleConfirm = async () => {
    if (!preview || confirm.pending) return
    setConfirmError(undefined)
    try {
      const run = await confirm.run({ acknowledgeRerun, runId: preview.runId })
      navigate(`/content/imports/${run.runId}`)
    } catch (error) {
      const mapped = mapConfirmImportError(error)
      setConfirmOpen(false)
      if (mapped.kind === 'terminal') {
        setConfirmBlockedMessage(mapped.message)
      } else {
        if (mapped.kind === 'rerun') setAcknowledgeRerun(false)
        setConfirmError({ message: mapped.message, requestId: mapped.requestId })
      }
    }
  }

  return (
    <LearningDataImportView
      acknowledgeRerun={acknowledgeRerun}
      confirmBlockedMessage={confirmBlockedMessage}
      confirmError={confirmError}
      confirmOpen={confirmOpen}
      confirmPending={confirm.pending}
      dataType={dataType}
      downloadError={downloadError}
      downloadPending={downloadPending}
      fileInputKey={fileInputKey}
      history={{
        data: historyQuery.data,
        error: historyQuery.error ? toLoadError(historyQuery.error) : undefined,
        onOpenRun: (runId) => navigate(`/content/imports/${runId}`),
        onPageChange: setHistoryPage,
        onRetry: historyQuery.reload,
        state: historyQuery.state,
      }}
      loadError={templatesQuery.error ? toLoadError(templatesQuery.error) : undefined}
      onAcknowledgeChange={(value) => {
        setAcknowledgeRerun(value)
        setConfirmError(undefined)
      }}
      onCancelConfirm={() => {
        if (!confirm.pending) setConfirmOpen(false)
      }}
      onCancelPreview={() => {
        clearPreview()
        setFile(undefined)
        setFileInputKey((key) => key + 1)
        setSelectionErrors({})
        setUploadError(undefined)
      }}
      onConfirm={() => void handleConfirm()}
      onDataTypeChange={(value) => {
        setDataType(value)
        setDownloadError(undefined)
        setSelectionErrors((current) => ({ ...current, dataType: undefined }))
      }}
      onDownloadTemplate={() => void handleDownloadTemplate()}
      onFileChange={(nextFile) => {
        setFile(nextFile)
        setSelectionErrors((current) => ({ ...current, file: undefined }))
        setUploadError(undefined)
      }}
      onRequestConfirm={() => {
        setConfirmError(undefined)
        setConfirmOpen(true)
      }}
      onRetry={templatesQuery.reload}
      onUpload={() => void handleUpload()}
      preview={preview}
      remainingSeconds={remainingSeconds}
      selectionErrors={selectionErrors}
      state={templatesQuery.state}
      templates={templates}
      uploadError={uploadError}
      uploading={upload.pending}
    />
  )
}
