import { useState } from 'react'
import { useParams } from 'react-router-dom'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { getReference, reportReference } from '../api/references'
import { BookDetailView } from '../components/BookDetailView'
import { ReferenceReportForm } from '../components/ReferenceReportForm'
import type { ReportRequest } from '../types'

function BookDetail({ id }: { id: string }) {
  const valid = /^\d+$/.test(id) && Number.isSafeInteger(Number(id)) && Number(id) > 0
  const auth = useCurrentUser()
  const canReport = !!auth.user?.roles.includes('LEARNER')
  const [reporting, setReporting] = useState(false)
  const [sent, setSent] = useState(false)
  const mutation = useApiMutation((body: ReportRequest) => reportReference(Number(id), body))
  const query = useApiQuery(
    (signal) => (valid ? getReference(Number(id), signal) : Promise.resolve(null)),
    [id],
    { isEmpty: (book) => book === null || book.kind !== 'BOOK' },
  )
  const state = query.error?.status === 404 ? 'empty' : query.state
  return (
    <BookDetailView
      state={state}
      book={query.data ?? undefined}
      error={query.error}
      onRetry={query.reload}
      canReport={canReport}
      onReport={() => {
        setReporting(true)
        mutation.reset()
      }}
      report={
        reporting ? (
          <ReferenceReportForm
            pending={mutation.pending}
            error={mutation.error}
            sent={sent}
            onCancel={() => setReporting(false)}
            onSubmit={(body) => {
              void mutation
                .run(body)
                .then(() => setSent(true))
                .catch(() => undefined)
            }}
          />
        ) : undefined
      }
    />
  )
}

export default function Scr055BookDetailPage() {
  const { id = '' } = useParams()
  const auth = useCurrentUser()
  return <BookDetail key={`${id}-${auth.user?.id ?? 'guest'}`} id={id} />
}
