import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import { listReferences } from '../api/references'
import { ReferenceListView } from '../components/ReferenceListView'

export default function Scr054BookCataloguePage() {
  const [params, setParams] = useSearchParams()
  const key = params.toString()
  const level = Number(params.get('hsk'))
  const hsk = SUPPORTED_HSK_LEVELS.some((value) => value === level) ? String(level) : ''
  const rawPage = params.get('page') ?? '0'
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  const tag = params.get('tag') ?? ''
  const [draftTag, setDraftTag] = useState(tag)
  const query = useApiQuery(
    (signal) =>
      listReferences(
        {
          kind: 'BOOK',
          hsk: hsk ? Number(hsk) : undefined,
          tag: params.has('tag') ? tag : undefined,
          page,
        },
        signal,
      ),
    [key],
    { isEmpty: (data) => data.items.length === 0 },
  )

  useEffect(() => {
    setDraftTag(new URLSearchParams(key).get('tag') ?? '')
  }, [key])

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
      state={query.state}
      result={query.data}
      error={query.error}
      hsk={hsk}
      tag={draftTag}
      onHskChange={(value) => change({ hsk: value, page: '0' })}
      onTagChange={setDraftTag}
      onApply={() => {
        if (draftTag === tag && page === 0) query.reload()
        else change({ tag: draftTag, page: '0' })
      }}
      onPageChange={(value) => change({ page: String(value) })}
      onRetry={query.reload}
    />
  )
}
