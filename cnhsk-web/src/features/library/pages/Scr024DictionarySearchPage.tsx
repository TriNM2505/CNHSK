import { useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { listRadicals, searchByRadical, searchDictionary } from '../api/dictionary'
import { DictionarySearchView } from '../components/DictionarySearchView'
import {
  dictionarySearchParams,
  readDictionarySearch,
  validateDictionarySearch,
} from '../dictionarySearch'

export default function Scr024DictionarySearchPage() {
  const auth = useCurrentUser()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const paramsKey = params.toString()
  const search = readDictionarySearch(params)
  const [draft, setDraft] = useState(search)
  const [formError, setFormError] = useState<string>()
  const submitted = params.has('q') || params.has('radical') || params.has('strokes')
  const searchError = submitted ? validateDictionarySearch(search) : undefined
  const searched = submitted && !searchError

  useEffect(() => {
    setDraft(readDictionarySearch(new URLSearchParams(paramsKey)))
    setFormError(undefined)
  }, [paramsKey])

  const query = useApiQuery(
    (signal) => {
      if (!searched) return Promise.resolve({ items: [], total: 0 })
      if (search.type === 'RADICAL') {
        return searchByRadical(
          {
            radical: search.radical || undefined,
            strokes: search.strokes ? Number(search.strokes) : undefined,
            page: search.page,
          },
          signal,
        )
      }
      return searchDictionary({ type: search.type, q: search.q, page: search.page }, signal)
    },
    [paramsKey],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const radicals = useApiQuery(
    (signal) => (draft.type === 'RADICAL' ? listRadicals(signal) : Promise.resolve([])),
    [draft.type],
  )

  const submit = () => {
    const error = validateDictionarySearch(draft)
    setFormError(error)
    if (error) return
    const next = dictionarySearchParams({
      ...draft,
      q: draft.q.trim(),
      radical: draft.radical.trim(),
      page: 0,
    })
    if (next.toString() === paramsKey) query.reload()
    else setParams(next)
  }

  return (
    <DictionarySearchView
      draft={draft}
      onChange={(next) => {
        setDraft(next)
        setFormError(undefined)
      }}
      onSubmit={submit}
      onPageChange={(page) => setParams(dictionarySearchParams({ ...search, page }))}
      onRetry={query.reload}
      onRadicalsRetry={radicals.reload}
      state={query.state}
      result={query.data}
      error={query.error}
      formError={formError ?? searchError}
      radicals={radicals.data ?? []}
      radicalsError={radicals.error}
      searched={searched}
      page={search.page}
      returnTo={`${location.pathname}${location.search}`}
      loginTo={`/login?${new URLSearchParams({ redirect: `${location.pathname}${location.search}${location.hash}` })}`}
      canTranslate={auth.status === 'authenticated' && !!auth.user?.roles.includes('LEARNER')}
      guest={auth.status === 'guest'}
    />
  )
}
