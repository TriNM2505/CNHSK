import { BookOpenText } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ErrorState, HanziText, Skeleton } from '@/shared/ui'

import { getGuestTrial, getLexeme, listRadicals, searchByRadical, searchDictionary } from '../api/dictionary'
import { addCard, listDecks } from '../api/flashcards'
import { createNote } from '../api/notes'
import { translate } from '../api/translation'
import { LexemeEntryBody } from '../components/LexemeEntryBody'
import { LexemeSaveActions, type LexemeSaveActionsProps } from '../components/LexemeSaveActions'
import { TranslateWorkspaceView } from '../components/TranslateWorkspaceView'
import {
  SentenceWords,
  TranslationOutput,
  type TranslationPanelState,
} from '../components/TranslationResultPanel'
import { dictionarySearchParams, readDictionarySearch, validateDictionarySearch } from '../dictionarySearch'
import { foundInDictionary, lookupKind, translationSource } from '../lookupKind'
import type { LexemeSummary } from '../types'

function Headword({ entry }: { entry: LexemeSummary }) {
  return (
    <div className="space-y-2">
      <HanziText className="block break-words text-brand-800" size="lg">
        {entry.text}
      </HanziText>
      {entry.pinyin ? <p className="text-h3 text-brand-700">{entry.pinyin}</p> : null}
      <p className="break-words text-h3 text-ink">{entry.meaningVi}</p>
      {entry.sinoViet ? <p className="text-body-sm text-ink-secondary">Hán Việt: {entry.sinoViet}</p> : null}
    </div>
  )
}

/**
 * The merged "Dịch" screen: one word shows its full dictionary entry right below (no AI call); a phrase,
 * or a word the dictionary lacks, is translated by AI and its words open in place.
 */
export default function Scr026TranslationPage() {
  const auth = useCurrentUser()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const paramsKey = params.toString()
  const search = readDictionarySearch(params)
  const [draft, setDraft] = useState(search)
  const [formError, setFormError] = useState<string>()
  const [lookups, setLookups] = useState(0)
  const [picked, setPicked] = useState<number>()
  const guest = auth.status === 'guest'
  const learner = auth.status === 'authenticated' && !!auth.user?.roles.includes('LEARNER')
  const submitted = params.has('q') || params.has('radical') || params.has('strokes')
  const searchError = submitted ? validateDictionarySearch(search) : undefined
  const kind = lookupKind(search)
  const searched = submitted && !searchError && kind !== 'phrase'

  useEffect(() => {
    setDraft(readDictionarySearch(new URLSearchParams(paramsKey)))
    setFormError(undefined)
    setPicked(undefined)
  }, [paramsKey])

  const query = useApiQuery(
    (signal) => {
      if (!searched) return Promise.resolve({ items: [], total: 0 })
      const request =
        search.type === 'RADICAL'
          ? searchByRadical(
              {
                radical: search.radical || undefined,
                strokes: search.strokes ? Number(search.strokes) : undefined,
                page: search.page,
              },
              signal,
            )
          : searchDictionary({ type: search.type, q: search.q.trim(), page: search.page }, signal)
      return request.finally(() => {
        if (!signal.aborted) setLookups((count) => count + 1)
      })
    },
    [paramsKey, searched],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const radicals = useApiQuery(
    (signal) => (draft.type === 'RADICAL' ? listRadicals(signal) : Promise.resolve([])),
    [draft.type],
  )
  // The server owns the guest count: ask again after each finished lookup.
  const trial = useApiQuery(
    (signal) => (guest ? getGuestTrial(signal) : Promise.resolve(undefined)),
    [guest, lookups],
  )

  const dictionaryDone = searched && (query.state === 'ready' || query.state === 'empty') && !!query.data
  const found = kind === 'word' && dictionaryDone && foundInDictionary(search, query.data!)
  const headword = found
    ? search.type === 'HANZI'
      ? query.data!.items.find((item) => item.text === search.q.trim())
      : query.data!.items[0]
    : undefined
  const wordMissing = kind === 'word' && dictionaryDone && !found
  const needsTranslation = submitted && !searchError && (kind === 'phrase' || wordMissing)
  const from = translationSource(search.type)
  const translation = useApiQuery(
    (signal) =>
      needsTranslation && learner
        ? translate({ text: search.q.trim(), from, to: from === 'ZH' ? 'VI' : 'ZH' }, signal)
        : Promise.resolve(undefined),
    [paramsKey, needsTranslation, learner],
  )

  // The entry shown below: one the learner picked, else the word that was looked up.
  const selectedId = picked ?? headword?.id
  const entry = useApiQuery(
    (signal) => (selectedId ? getLexeme(selectedId, signal) : Promise.resolve(undefined)),
    [selectedId],
  )

  const returnQuery = new URLSearchParams({ redirect: `${location.pathname}${location.search}${location.hash}` })
  const loginTo = `/login?${returnQuery}`
  const registerTo = `/register?${returnQuery}`
  let access: LexemeSaveActionsProps['access'] = auth.status === 'authenticated' ? 'other' : auth.status
  if (learner) access = 'learner'

  const panel: TranslationPanelState | undefined = !needsTranslation
    ? undefined
    : guest
      ? { state: 'login', loginTo, registerTo }
      : !learner
        ? { state: 'learnerOnly' }
        : translation.state === 'error' && translation.error
          ? { state: 'error', error: translation.error, onRetry: translation.reload }
          : translation.state === 'ready' && translation.data
            ? { state: 'ready', result: translation.data }
            : { state: 'pending' }

  const output = panel ? (
    <TranslationOutput panel={panel} to={from === 'ZH' ? 'VI' : 'ZH'} />
  ) : headword ? (
    <Headword entry={headword} />
  ) : searched && query.state === 'loading' ? (
    <div aria-hidden="true" className="space-y-3">
      <Skeleton className="h-12 w-24" />
      <Skeleton className="w-3/5" />
    </div>
  ) : (
    <p className="text-h3 text-ink-muted">Bản dịch</p>
  )

  const submitSearch = (next: typeof draft) => {
    const error = validateDictionarySearch(next)
    setFormError(error)
    if (error) return
    const nextParams = dictionarySearchParams({ ...next, q: next.q.trim(), radical: next.radical.trim(), page: 0 })
    if (nextParams.toString() === paramsKey) {
      if (searched) query.reload()
      else translation.reload()
    } else setParams(nextParams)
  }

  const otherMatches =
    found && search.type === 'MEANING' ? query.data!.items.filter((item) => item.id !== selectedId) : []
  const details = (
    <>
      {panel?.state === 'ready' ? (
        <SentenceWords onOpen={setPicked} segments={panel.result.segments} selectedId={selectedId} />
      ) : null}
      {otherMatches.length ? (
        <section aria-label="Các mục khớp khác" className="flex flex-wrap items-center gap-2">
          <span className="text-body-sm text-ink-secondary">Mục khác:</span>
          {otherMatches.map((item) => (
            <button
              className="inline-flex min-h-touch items-center gap-2 rounded-pill border border-line bg-surface px-4 transition-colors hover:border-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              key={item.id}
              onClick={() => setPicked(item.id)}
              type="button"
            >
              <span className="font-hanzi text-h3" lang="zh-CN">
                {item.text}
              </span>
              <span className="text-body-sm text-ink-secondary">{item.meaningVi}</span>
            </button>
          ))}
        </section>
      ) : null}
      {selectedId ? (
        entry.state === 'loading' ? (
          <div aria-label="Đang tải mục từ" className="grid gap-6 lg:grid-cols-3">
            <Skeleton className="h-40 lg:col-span-2" />
            <Skeleton className="h-40" />
          </div>
        ) : entry.state === 'error' ? (
          <ErrorState
            message={entry.error?.message ?? 'Vui lòng thử lại sau.'}
            onRetry={entry.reload}
            requestId={entry.error?.requestId}
            title="Không thể tải mục từ"
          />
        ) : entry.data ? (
          <LexemeEntryBody
            entry={entry.data}
            related={{ onOpen: setPicked }}
            saveActions={
              <LexemeSaveActions
                access={access}
                entry={entry.data}
                key={`${entry.data.id}:${auth.user?.id ?? ''}:${access}`}
                loginTo={loginTo}
                onAddCard={addCard}
                onCreateNote={(body) =>
                  createNote({ ...body, sourceType: needsTranslation ? 'TRANSLATION' : 'DICTIONARY' })
                }
                onLoadDecks={listDecks}
                onReloadAuth={() => {
                  void auth.reloadCurrentUser()
                }}
              />
            }
          />
        ) : null
      ) : !submitted ? (
        <p className="flex items-center gap-2 text-body-sm text-ink-secondary">
          <BookOpenText aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
          Nhập một từ hoặc một câu rồi bấm Dịch.
        </p>
      ) : null}
    </>
  )

  return (
    <TranslateWorkspaceView
      details={details}
      draft={draft}
      error={query.error}
      formError={formError ?? searchError}
      guest={guest}
      guestTrial={trial.data}
      listed={search.type === 'PINYIN' || search.type === 'RADICAL'}
      loginTo={loginTo}
      onChange={(next) => {
        setDraft(next)
        setFormError(undefined)
      }}
      onPageChange={(page) => setParams(dictionarySearchParams({ ...search, page }))}
      onRadicalsRetry={radicals.reload}
      onRetry={query.reload}
      onSelect={setPicked}
      onSubmit={() => submitSearch(draft)}
      onSwap={() => {
        const type = draft.type === 'MEANING' ? 'HANZI' : 'MEANING'
        const carried = translation.state === 'ready' && translation.data ? translation.data.translated : draft.q
        const next = { ...draft, type, q: carried } as const
        setDraft(next)
        if (carried.trim()) submitSearch(next)
      }}
      output={output}
      page={search.page}
      radicals={radicals.data ?? []}
      radicalsError={radicals.error}
      registerTo={registerTo}
      result={query.data}
      searched={searched}
      selectedId={selectedId}
      state={query.state}
    />
  )
}
