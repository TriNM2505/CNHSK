import { useEffect, useRef, useState } from 'react'

import { toApiError, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ErrorState, PageHeader } from '@/shared/ui'

import { getLexeme } from '../api/dictionary'
import { addCard, listDecks } from '../api/flashcards'
import { createNote } from '../api/notes'
import { translate } from '../api/translation'
import { LexemeSaveActions } from '../components/LexemeSaveActions'
import { TranslationLexemePopup } from '../components/TranslationLexemePopup'
import { TranslationView } from '../components/TranslationView'
import type { LexemeDetail, TranslateRequest, TranslateResult } from '../types'

type Lookup = {
  id: number
  state: 'loading' | 'ready' | 'error'
  entry?: LexemeDetail
  error?: ApiError
}

function TranslationContent() {
  const [draft, setDraft] = useState<TranslateRequest>({ text: '', from: 'ZH', to: 'VI' })
  const [pending, setPending] = useState(false)
  const [result, setResult] = useState<TranslateResult>()
  const [error, setError] = useState<ApiError>()
  const [validation, setValidation] = useState<string>()
  const [lookup, setLookup] = useState<Lookup>()
  const busy = useRef(false)
  const lifecycle = useRef({ alive: true, generation: 0, lookupGeneration: 0 })
  const translationController = useRef<AbortController>()
  const lookupController = useRef<AbortController>()
  useEffect(() => {
    const current = lifecycle.current
    current.alive = true
    return () => {
      current.alive = false
      current.generation++
      current.lookupGeneration++
      translationController.current?.abort()
      lookupController.current?.abort()
    }
  }, [])

  const closeLookup = () => {
    lifecycle.current.lookupGeneration++
    lookupController.current?.abort()
    setLookup(undefined)
  }
  const change = (next: TranslateRequest) => {
    if (busy.current) return
    setDraft(next)
    setResult(undefined)
    setError(undefined)
    setValidation(
      !next.text.trim() || Array.from(next.text).length > 2000
        ? 'Nhập nội dung cần dịch, tối đa 2.000 ký tự.'
        : undefined,
    )
    closeLookup()
  }
  const submit = async () => {
    if (busy.current) return
    if (!draft.text.trim() || Array.from(draft.text).length > 2000) {
      setValidation('Nhập nội dung cần dịch, tối đa 2.000 ký tự.')
      return
    }
    busy.current = true
    const current = ++lifecycle.current.generation
    const controller = new AbortController()
    translationController.current = controller
    setPending(true)
    setResult(undefined)
    setError(undefined)
    setValidation(undefined)
    closeLookup()
    try {
      const response = await translate(draft, controller.signal)
      if (
        lifecycle.current.alive &&
        !controller.signal.aborted &&
        lifecycle.current.generation === current
      )
        setResult(response)
    } catch (requestError) {
      if (
        lifecycle.current.alive &&
        !controller.signal.aborted &&
        lifecycle.current.generation === current
      )
        setError(toApiError(requestError))
    } finally {
      if (lifecycle.current.alive && lifecycle.current.generation === current) {
        busy.current = false
        setPending(false)
      }
    }
  }
  const openLexeme = async (id: number) => {
    if (busy.current || !result) return
    lookupController.current?.abort()
    const controller = new AbortController()
    lookupController.current = controller
    const current = ++lifecycle.current.lookupGeneration
    setLookup({ id, state: 'loading' })
    try {
      const entry = await getLexeme(id, controller.signal)
      if (
        lifecycle.current.alive &&
        !controller.signal.aborted &&
        lifecycle.current.lookupGeneration === current
      )
        setLookup({ id, state: 'ready', entry })
    } catch (requestError) {
      if (
        lifecycle.current.alive &&
        !controller.signal.aborted &&
        lifecycle.current.lookupGeneration === current
      )
        setLookup({ id, state: 'error', error: toApiError(requestError) })
    }
  }
  return (
    <TranslationView
      draft={draft}
      pending={pending}
      result={result}
      error={error}
      validation={validation}
      onTextChange={(text) => change({ ...draft, text })}
      onDirectionChange={(from) => change({ ...draft, from, to: from === 'ZH' ? 'VI' : 'ZH' })}
      onSubmit={() => {
        void submit()
      }}
      onOpenLexeme={(id) => {
        void openLexeme(id)
      }}
      popup={
        lookup ? (
          <TranslationLexemePopup
            state={lookup.state}
            entry={lookup.entry}
            error={lookup.error}
            onClose={closeLookup}
            onRetry={() => {
              void openLexeme(lookup.id)
            }}
            saveActions={
              lookup.entry ? (
                <LexemeSaveActions
                  key={lookup.id}
                  entry={lookup.entry}
                  access="learner"
                  loginTo="/login?redirect=%2Ftranslate"
                  onReloadAuth={() => undefined}
                  onCreateNote={(body) => createNote({ ...body, sourceType: 'TRANSLATION' })}
                  onLoadDecks={listDecks}
                  onAddCard={addCard}
                />
              ) : undefined
            }
          />
        ) : undefined
      }
    />
  )
}

export default function Scr026TranslationPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('LEARNER')) {
    return (
      <div className="space-y-4">
        <PageHeader title="Dịch câu" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState
            message={
              auth.status === 'error'
                ? 'Chưa kiểm tra được phiên đăng nhập.'
                : 'Cần đăng nhập với quyền học viên để dịch.'
            }
            onRetry={
              auth.status === 'error'
                ? () => {
                    void auth.reloadCurrentUser()
                  }
                : undefined
            }
          />
        )}
      </div>
    )
  }
  return <TranslationContent key={auth.user.id} />
}
