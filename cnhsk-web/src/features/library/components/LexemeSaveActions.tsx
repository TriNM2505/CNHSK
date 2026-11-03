import { useEffect, useRef, useState } from 'react'

import { toApiError, useApiQuery, type ApiError } from '@/shared/api'
import { Button, ErrorState, FormField, Input, Select, Textarea } from '@/shared/ui'

import type { CardRequest, DeckSummary, LexemeDetail, NoteRequest } from '../types'

export interface LexemeSaveActionsProps {
  entry: LexemeDetail
  access: 'learner' | 'guest' | 'other' | 'loading' | 'error'
  loginTo: string
  onReloadAuth: () => void
  onCreateNote: (body: NoteRequest) => Promise<unknown>
  onLoadDecks: (signal: AbortSignal) => Promise<DeckSummary[]>
  onAddCard: (deckId: number, body: CardRequest) => Promise<unknown>
}

function saveErrorMessage(error: ApiError) {
  if (error.errorCode === 'CARD_DUPLICATE')
    return 'Mục từ đã có trong bộ thẻ này. Hãy chọn bộ khác.'
  if (error.status === 403) return 'Bạn không có quyền lưu vào sổ tay hoặc bộ thẻ này.'
  return error.message
}

export function LexemeSaveActions({
  entry,
  access,
  loginTo,
  onReloadAuth,
  onCreateNote,
  onLoadDecks,
  onAddCard,
}: LexemeSaveActionsProps) {
  const [panel, setPanel] = useState<'note' | 'card' | null>(null)
  const [title, setTitle] = useState(entry.text.slice(0, 200))
  const [content, setContent] = useState(
    [entry.text, entry.pinyin, entry.meaningVi, entry.structure].filter(Boolean).join('\n'),
  )
  const [tags, setTags] = useState('')
  const [deckId, setDeckId] = useState('')
  const [validation, setValidation] = useState<string>()
  const [error, setError] = useState<ApiError>()
  const [notice, setNotice] = useState('')
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])
  const decks = useApiQuery(
    (signal) =>
      panel === 'card' && access === 'learner' ? onLoadDecks(signal) : Promise.resolve([]),
    [panel, access],
  )

  const open = (next: 'note' | 'card') => {
    setValidation(undefined)
    setError(undefined)
    setNotice('')
    setPanel(next)
  }
  const save = async (kind: 'note' | 'card') => {
    if (access !== 'learner' || busy.current) return
    const noteTags = tags
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean)
    if (
      kind === 'note' &&
      (!content.trim() ||
        content.trim().length > 10_000 ||
        title.trim().length > 200 ||
        noteTags.length > 10 ||
        noteTags.some((tag) => tag.length > 50))
    ) {
      setValidation(
        'Nội dung cần 1–10.000 ký tự, tiêu đề tối đa 200 ký tự; tối đa 10 thẻ, mỗi thẻ 50 ký tự.',
      )
      return
    }
    if (kind === 'card' && !decks.data?.some((deck) => String(deck.id) === deckId)) {
      setValidation('Chọn một bộ thẻ của bạn.')
      return
    }
    busy.current = true
    setPending(true)
    setValidation(undefined)
    setError(undefined)
    try {
      if (kind === 'note')
        await onCreateNote({
          title: title.trim() || null,
          content: content.trim(),
          tags: noteTags,
          lexemeId: entry.id,
          sourceType: 'DICTIONARY',
        })
      else await onAddCard(Number(deckId), { lexemeId: entry.id })
      if (!mounted.current) return
      setNotice(kind === 'note' ? 'Đã lưu vào sổ tay.' : 'Đã thêm vào bộ thẻ.')
      setPanel(null)
    } catch (requestError) {
      if (mounted.current) setError(toApiError(requestError))
    } finally {
      busy.current = false
      if (mounted.current) setPending(false)
    }
  }

  if (access === 'guest') return <Button to={loginTo}>Đăng nhập để lưu</Button>
  if (access === 'other')
    return <p className="text-body-sm text-ink-secondary">Cần quyền học viên để lưu</p>
  if (access === 'loading')
    return <p className="text-body-sm text-ink-secondary">Đang kiểm tra quyền lưu…</p>
  if (access === 'error')
    return (
      <ErrorState
        message="Chưa xác định được quyền lưu. Vui lòng thử lại."
        onRetry={onReloadAuth}
      />
    )

  return (
    <section
      aria-label="Lưu mục từ"
      className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
    >
      <div className="flex flex-wrap gap-3">
        <Button disabled={pending} onClick={() => open('note')}>
          Lưu vào sổ tay
        </Button>
        <Button disabled={pending} onClick={() => open('card')} variant="outline">
          Thêm vào bộ thẻ
        </Button>
      </div>
      {notice ? (
        <p className="text-body text-success" role="status">
          {notice}
        </p>
      ) : null}
      {panel ? (
        <form
          className="space-y-3"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void save(panel)
          }}
        >
          {panel === 'note' ? (
            <fieldset disabled={pending}>
              <legend className="mb-3 text-h3 text-ink">Ghi chú từ mục từ</legend>
              <FormField htmlFor="lexeme-note-title" label="Tiêu đề">
                <Input
                  maxLength={200}
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                />
              </FormField>
              <FormField
                htmlFor="lexeme-note-content"
                label="Nội dung ghi chú"
                hint={`${content.length}/10.000 ký tự`}
                required
              >
                <Textarea
                  maxLength={10_000}
                  value={content}
                  onChange={(event) => setContent(event.target.value)}
                />
              </FormField>
              <FormField
                htmlFor="lexeme-note-tags"
                label="Thẻ ghi chú"
                hint="Ngăn cách bằng dấu phẩy; tối đa 10 thẻ, mỗi thẻ 50 ký tự."
              >
                <Input value={tags} onChange={(event) => setTags(event.target.value)} />
              </FormField>
            </fieldset>
          ) : (
            <>
              <h2 className="text-h3 text-ink">Chọn bộ thẻ của bạn</h2>
              {decks.state === 'loading' ? <p role="status">Đang tải bộ thẻ…</p> : null}
              {decks.state === 'error' ? (
                <ErrorState
                  message={decks.error?.message ?? 'Chưa tải được bộ thẻ.'}
                  requestId={decks.error?.requestId}
                  onRetry={decks.reload}
                />
              ) : null}
              {decks.state === 'ready' && !decks.data?.length ? (
                <p className="text-body-sm text-ink-secondary">
                  Bạn chưa có bộ thẻ.{' '}
                  <Button to="/library/decks" variant="ghost">
                    Tạo bộ thẻ
                  </Button>
                </p>
              ) : null}
              {decks.state === 'ready' && !!decks.data?.length ? (
                <FormField htmlFor="lexeme-deck" label="Bộ thẻ" required>
                  <Select
                    disabled={pending}
                    value={deckId}
                    onChange={(event) => {
                      setDeckId(event.target.value)
                      setValidation(undefined)
                      setError(undefined)
                    }}
                  >
                    <option value="">Chọn bộ thẻ</option>
                    {decks.data.map((deck) => (
                      <option key={deck.id} value={deck.id}>
                        {deck.name} ({deck.cardCount} thẻ)
                      </option>
                    ))}
                  </Select>
                </FormField>
              ) : null}
            </>
          )}
          {validation ? (
            <p className="text-body-sm text-danger" role="alert">
              {validation}
            </p>
          ) : null}
          {error ? (
            <div role="alert">
              <ErrorState message={saveErrorMessage(error)} requestId={error.requestId} />
            </div>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <Button
              disabled={panel === 'card' && (decks.state !== 'ready' || !decks.data?.length)}
              loading={pending}
              type="submit"
            >
              {panel === 'note' ? 'Lưu ghi chú' : 'Thêm thẻ'}
            </Button>
            <Button
              disabled={pending}
              onClick={() => {
                setPanel(null)
                setError(undefined)
                setValidation(undefined)
              }}
              variant="ghost"
            >
              Huỷ
            </Button>
          </div>
        </form>
      ) : null}
    </section>
  )
}
