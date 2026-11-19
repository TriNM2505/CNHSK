import { useState } from 'react'
import { Button } from '@/shared/ui'
import { ImportReportView, type ImportReportViewProps } from './ImportReportView'

export function ImportReportScreenPreview({
  example,
  fallback,
}: {
  example: ImportReportViewProps
  fallback: ImportReportViewProps
}) {
  const [state, setState] = useState(example.state)
  const [run, setRun] = useState(example.run)
  const [errorState, setErrorState] = useState(example.errors.state)
  const [errorData, setErrorData] = useState(example.errors.data)
  const [downloadError, setDownloadError] = useState(example.downloadError)
  const [notice, setNotice] = useState('')
  const [returning, setReturning] = useState(false)
  const sourceErrors = example.errors.data?.items.length
    ? example.errors.data
    : fallback.errors.data
  const firstPage = () => {
    setErrorData(sourceErrors ? { ...sourceErrors, page: 0 } : undefined)
    setErrorState(sourceErrors?.items.length ? 'ready' : 'empty')
  }
  return (
    <div
      onClickCapture={(event) => {
        if (!(event.target instanceof Element)) return
        const anchor = event.target.closest('a')
        if (/^\/content\/imports(?:\?.*)?$/.test(anchor?.getAttribute('href') ?? '')) {
          event.preventDefault()
          event.stopPropagation()
          setReturning(true)
        }
      }}
    >
      <div className="mx-4 mt-4 space-y-3 rounded-md border border-info bg-info-bg p-4 text-body-sm text-info sm:mx-6 lg:mx-8">
        <p>Bản xem trước · Báo cáo dùng dữ liệu mẫu, không đọc hoặc thay đổi kho dữ liệu thật.</p>
        {notice ? <p role="status">{notice}</p> : null}
        {returning ? (
          <div role="status" className="flex flex-wrap items-center gap-3">
            Đã chọn quay lại màn nhập trong bản mẫu.
            <Button variant="outline" to="/__preview/frame/scr-068-ready/ready">
              Xem mẫu màn nhập dữ liệu
            </Button>
          </div>
        ) : null}
      </div>
      <ImportReportView
        {...example}
        state={state}
        run={run}
        downloadError={downloadError}
        onReload={() => {
          if (state === 'error') {
            setState('ready')
            setRun(example.run ?? fallback.run)
            firstPage()
          }
          setNotice('Đã tải lại bản mô phỏng, không gửi yêu cầu tới máy chủ.')
        }}
        onDownloadErrors={() => {
          if (example.downloadPending) return
          setDownloadError(undefined)
          setNotice('Đã chọn tải danh sách lỗi mẫu. Bản xem trước không tải file từ máy chủ.')
        }}
        errors={{
          ...example.errors,
          state: errorState,
          data: errorData,
          onRetry: firstPage,
          onFirstPage: firstPage,
          onPageChange: (page) => {
            if (!sourceErrors) return
            setErrorData({ ...sourceErrors, page: page - 1 })
            setErrorState(sourceErrors.items.length ? 'ready' : 'empty')
          },
        }}
      />
    </div>
  )
}
