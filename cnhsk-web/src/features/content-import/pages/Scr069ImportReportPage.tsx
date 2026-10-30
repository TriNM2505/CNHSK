import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'

import { toApiError, useApiQuery } from '@/shared/api'
import { downloadBlob } from '@/shared/lib/downloadBlob'

import { getImportErrors, getImportErrorsFile, getImportRun } from '../api/imports'
import { ImportReportView } from '../components/ImportReportView'
import { parseImportReportSearchParams, toImportReportSearchParams } from '../importReportQuery'

function parseRunId(value: string | undefined): number | undefined {
  if (!value || !/^[1-9]\d*$/.test(value)) return undefined
  const runId = Number(value)
  return Number.isSafeInteger(runId) ? runId : undefined
}

function toLoadError(error: unknown) {
  const apiError = toApiError(error)
  return { message: apiError.message, requestId: apiError.requestId }
}

function ValidImportReport({ runId }: { runId: number }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const parsed = useMemo(() => parseImportReportSearchParams(searchParams), [searchParams])
  const [downloadPending, setDownloadPending] = useState(false)
  const [downloadError, setDownloadError] = useState<ReturnType<typeof toLoadError>>()
  const downloadController = useRef<AbortController>()

  useEffect(() => () => downloadController.current?.abort(), [])

  useEffect(() => {
    if (parsed.needsCleanup) setSearchParams(parsed.canonical, { replace: true })
  }, [parsed.canonical, parsed.needsCleanup, setSearchParams])

  const runQuery = useApiQuery((signal) => getImportRun(runId, signal), [runId])
  const errorsQuery = useApiQuery(
    (signal) => getImportErrors(runId, parsed.page, signal),
    [runId, parsed.page],
    { isEmpty: (data) => data.items.length === 0 },
  )

  const reloadAll = () => {
    setDownloadError(undefined)
    runQuery.reload()
    errorsQuery.reload()
  }

  const handleDownload = async () => {
    if (downloadController.current) return
    const controller = new AbortController()
    downloadController.current = controller
    setDownloadPending(true)
    setDownloadError(undefined)
    try {
      const response = await getImportErrorsFile(runId, controller.signal)
      if (!controller.signal.aborted && response) {
        downloadBlob(response.blob, response.fileName ?? `import-${runId}-errors.csv`)
      }
    } catch (error) {
      if (!controller.signal.aborted) setDownloadError(toLoadError(error))
    } finally {
      downloadController.current = undefined
      if (!controller.signal.aborted) setDownloadPending(false)
    }
  }

  const runNotFound =
    runQuery.error?.status === 404 && runQuery.error.errorCode === 'IMPORT_RUN_NOT_FOUND'

  return (
    <ImportReportView
      downloadError={downloadError}
      downloadPending={downloadPending}
      error={runQuery.error ? toLoadError(runQuery.error) : undefined}
      errors={{
        data: errorsQuery.data,
        error: errorsQuery.error ? toLoadError(errorsQuery.error) : undefined,
        onFirstPage: () => setSearchParams(toImportReportSearchParams(0, searchParams)),
        onPageChange: (page) => setSearchParams(toImportReportSearchParams(page - 1, searchParams)),
        onRetry: errorsQuery.reload,
        state: errorsQuery.state,
      }}
      onDownloadErrors={() => void handleDownload()}
      onReload={reloadAll}
      run={runQuery.data}
      state={runNotFound ? 'empty' : runQuery.state}
    />
  )
}

export default function Scr069ImportReportPage() {
  const { id } = useParams()
  const runId = parseRunId(id)

  if (!runId) {
    return (
      <ImportReportView
        downloadPending={false}
        errors={{
          onFirstPage: () => undefined,
          onPageChange: () => undefined,
          onRetry: () => undefined,
          state: 'empty',
        }}
        onDownloadErrors={() => undefined}
        onReload={() => undefined}
        state="empty"
      />
    )
  }

  return <ValidImportReport key={runId} runId={runId} />
}
