import { useEffect, useRef, useState } from 'react'

import { toApiError, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ErrorState, PageHeader } from '@/shared/ui'

import { createCardBatch, listCardBatches, voidCardBatch } from '../api/cardBatches'
import { CardBatchView } from '../components/CardBatchView'
import type { CreateBatchRequest, CreatedBatch, VoidResult } from '../types'

function CardBatchContent() {
  const [page, setPage] = useState(0)
  const batches = useApiQuery((signal) => listCardBatches({ page, size: 20 }, signal), [page], {
    isEmpty: (data) => data.items.length === 0,
  })
  const [pending, setPending] = useState<'create' | 'void'>()
  const [createError, setCreateError] = useState<ApiError>()
  const [voidError, setVoidError] = useState<ApiError>()
  const [created, setCreated] = useState<CreatedBatch>()
  const [voided, setVoided] = useState<VoidResult>()
  const busy = useRef(false)
  const alive = useRef(true)

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  const create = async (body: CreateBatchRequest) => {
    if (busy.current) return
    busy.current = true
    setPending('create')
    setCreateError(undefined)
    try {
      const result = await createCardBatch(body)
      if (!alive.current) return
      setCreated(result)
      if (page === 0) batches.reload()
      else setPage(0)
    } catch (failure) {
      if (!alive.current) return
      const error = toApiError(failure)
      setCreateError(error)
      // An uncertain write must never be repeated automatically.
      if (error.status === 0 || error.status >= 500) batches.reload()
    } finally {
      busy.current = false
      if (alive.current) setPending(undefined)
    }
  }

  const voidBatch = async (batchId: string) => {
    if (busy.current) return
    busy.current = true
    setPending('void')
    setVoidError(undefined)
    setVoided(undefined)
    try {
      const result = await voidCardBatch(batchId)
      if (!alive.current) return
      setVoided(result)
      batches.reload()
    } catch (failure) {
      if (alive.current) setVoidError(toApiError(failure))
    } finally {
      busy.current = false
      if (alive.current) setPending(undefined)
    }
  }

  return (
    <CardBatchView
      state={batches.state}
      data={batches.data}
      error={batches.error}
      page={page}
      pending={pending}
      created={created}
      createError={createError}
      voided={voided}
      voidError={voidError}
      onPageChange={setPage}
      onRetry={batches.reload}
      onCreate={create}
      onVoid={voidBatch}
    />
  )
}

export default function Scr073CardBatchPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('ADMIN')) {
    return (
      <div className="space-y-4">
        <PageHeader title="Lô mã thẻ" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState message="Cần đăng nhập với quyền quản trị để quản lý lô mã thẻ." />
        )}
      </div>
    )
  }
  return <CardBatchContent key={auth.user.id} />
}
