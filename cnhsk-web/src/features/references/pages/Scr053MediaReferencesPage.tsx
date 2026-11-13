import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import { listReferences, reportReference } from '../api/references'
import { ReferenceListView } from '../components/ReferenceListView'
import { ReferenceReportForm } from '../components/ReferenceReportForm'
import type { ReferenceKind, ReportRequest } from '../types'

function ReportPanel({ id, onCancel }: { id: number; onCancel: () => void }) {
  const [sent, setSent] = useState(false)
  const mutation = useApiMutation((body: ReportRequest) => reportReference(id, body))
  return (
    <ReferenceReportForm
      pending={mutation.pending}
      error={mutation.error}
      sent={sent}
      onCancel={onCancel}
      onSubmit={(body) => {
        void mutation
          .run(body)
          .then(() => setSent(true))
          .catch(() => undefined)
      }}
    />
  )
}

export function ReferenceCataloguePage({ books = false }: { books?: boolean }) {
  const [params, setParams] = useSearchParams()
  const key = params.toString()
  const kind: ReferenceKind = books
    ? 'BOOK'
    : params.get('kind') === 'PODCAST'
      ? 'PODCAST'
      : 'YOUTUBE_CHANNEL'
  const level = Number(params.get('hsk'))
  const hsk = SUPPORTED_HSK_LEVELS.some((value) => value === level) ? String(level) : ''
  const rawPage = params.get('page') ?? '0'
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  const tag = params.get('tag') ?? ''
  const [draftTag, setDraftTag] = useState(tag)
  const [reportId, setReportId] = useState<number>()
  const auth = useCurrentUser()
  const canReport = !!auth.user?.roles.includes('LEARNER')
  const query = useApiQuery(
    (signal) =>
      listReferences(
        {
          kind,
          hsk: hsk ? Number(hsk) : undefined,
          tag: params.has('tag') ? tag : undefined,
          page,
        },
        signal,
      ),
    [kind, key],
    { isEmpty: (data) => data.items.length === 0 },
  )

  useEffect(() => {
    setDraftTag(new URLSearchParams(key).get('tag') ?? '')
    setReportId(undefined)
  }, [key, books, auth.user?.id])

  function change(next: Record<string, string | undefined>) {
    const updated = new URLSearchParams(params)
    Object.entries(next).forEach(([name, value]) => {
      if (value === undefined || value === '') updated.delete(name)
      else updated.set(name, value)
    })
    setParams(updated)
  }

  return (
    <ReferenceListView
      kind={kind}
      state={query.state}
      result={query.data}
      error={query.error}
      hsk={hsk}
      tag={draftTag}
      canReport={canReport}
      onKindChange={(value) => change({ kind: value, page: '0' })}
      onHskChange={(value) => change({ hsk: value, page: '0' })}
      onTagChange={setDraftTag}
      onApply={() => {
        if (draftTag === tag && page === 0) query.reload()
        else change({ tag: draftTag, page: '0' })
      }}
      onPageChange={(value) => change({ page: String(value) })}
      onRetry={query.reload}
      onReport={(id) => {
        setReportId(id)
      }}
      reportId={reportId}
      report={
        reportId === undefined ? undefined : (
          <ReportPanel
            key={`${auth.user?.id}-${reportId}`}
            id={reportId}
            onCancel={() => setReportId(undefined)}
          />
        )
      }
    />
  )
}

export default function Scr053MediaReferencesPage() {
  return <ReferenceCataloguePage />
}
