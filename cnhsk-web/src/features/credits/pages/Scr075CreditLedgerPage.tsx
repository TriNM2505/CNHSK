import { useEffect, useRef, useState } from 'react'

import { invalidResponseError, toApiError, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { downloadBlob } from '@/shared/lib/downloadBlob'
import { ErrorState, PageHeader } from '@/shared/ui'

import { exportLedger, listLedger, reconcileLedger } from '../api/ledger'
import { CreditLedgerView } from '../components/CreditLedgerView'
import type { LedgerFilter, Reconciliation } from '../types'

function CreditLedgerContent() {
  const [filter, setFilter] = useState<LedgerFilter>()
  const [page, setPage] = useState(0)
  const ledger = useApiQuery(
    (signal) =>
      filter ? listLedger({ ...filter, page, size: 20 }, signal) : Promise.resolve(undefined),
    [filter, page],
    { isEmpty: (data) => Boolean(data && data.items.length === 0) },
  )
  const [exportPending, setExportPending] = useState(false)
  const [exportError, setExportError] = useState<ApiError>()
  const [reconcilePending, setReconcilePending] = useState(false)
  const [reconcileError, setReconcileError] = useState<ApiError>()
  const [reconciliation, setReconciliation] = useState<Reconciliation>()
  const alive = useRef(true)
  const exportController = useRef<AbortController>()
  const reconcileController = useRef<AbortController>()
  const exportGeneration = useRef(0)
  const reconcileGeneration = useRef(0)
  const exportBusy = useRef(false)
  const reconcileBusy = useRef(false)

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      exportController.current?.abort()
      reconcileController.current?.abort()
    }
  }, [])

  const cancelExport = () => {
    exportController.current?.abort()
    exportGeneration.current++
    exportBusy.current = false
    setExportPending(false)
    setExportError(undefined)
  }

  const exportCsv = async () => {
    if (!filter || exportBusy.current) return
    exportBusy.current = true
    setExportPending(true)
    setExportError(undefined)
    const generation = ++exportGeneration.current
    const controller = new AbortController()
    exportController.current = controller
    try {
      const result = await exportLedger(filter, controller.signal)
      if (!alive.current || controller.signal.aborted || generation !== exportGeneration.current)
        return
      if (!result) throw invalidResponseError()
      downloadBlob(result.blob, result.fileName ?? 'credit-ledger.csv')
    } catch (error) {
      if (alive.current && !controller.signal.aborted && generation === exportGeneration.current)
        setExportError(toApiError(error))
    } finally {
      if (alive.current && generation === exportGeneration.current) {
        exportBusy.current = false
        setExportPending(false)
      }
    }
  }

  const reconcile = async () => {
    if (reconcileBusy.current) return
    reconcileBusy.current = true
    setReconcilePending(true)
    setReconcileError(undefined)
    setReconciliation(undefined)
    const generation = ++reconcileGeneration.current
    const controller = new AbortController()
    reconcileController.current = controller
    try {
      const result = await reconcileLedger(controller.signal)
      if (alive.current && !controller.signal.aborted && generation === reconcileGeneration.current)
        setReconciliation(result)
    } catch (error) {
      if (alive.current && !controller.signal.aborted && generation === reconcileGeneration.current)
        setReconcileError(toApiError(error))
    } finally {
      if (alive.current && generation === reconcileGeneration.current) {
        reconcileBusy.current = false
        setReconcilePending(false)
      }
    }
  }

  return (
    <CreditLedgerView
      state={filter ? ledger.state : 'idle'}
      data={filter ? ledger.data : undefined}
      error={ledger.error}
      filter={filter}
      page={page}
      exportPending={exportPending}
      exportError={exportError}
      reconcilePending={reconcilePending}
      reconcileError={reconcileError}
      reconciliation={reconciliation}
      onApply={(next) => {
        cancelExport()
        setPage(0)
        setFilter(next)
      }}
      onFilterEdit={cancelExport}
      onPageChange={setPage}
      onRetry={ledger.reload}
      onExport={exportCsv}
      onReconcile={reconcile}
    />
  )
}

export default function Scr075CreditLedgerPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('ADMIN'))
    return (
      <div className="space-y-4">
        <PageHeader title="Sổ cái điểm" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState message="Cần đăng nhập với quyền quản trị để xem sổ cái điểm." />
        )}
      </div>
    )
  return <CreditLedgerContent key={auth.user.id} />
}
