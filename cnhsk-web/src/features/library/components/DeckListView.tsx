import { ArrowRight, Info, Layers3, Plus, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  EmptyState,
  ErrorState,
  FormField,
  HanziText,
  Input,
  Pagination,
  Select,
  Skeleton,
  Textarea,
} from '@/shared/ui'
import './LibraryView.css'

import {
  characterCount,
  clientPage,
  flashcardError,
  flashcardPageSize,
  type DeckDraft,
  type TopicChoice,
} from '../flashcardForms'
import type { DeckSummary } from '../types'
import './DeckListView.css'

function DeckText({ text }: { text: string }) {
  return text.split(/([\p{Script=Han}\u3000-\u303f\uff00-\uffef]+)/u).map((part, index) =>
    /[\p{Script=Han}\u3000-\u303f\uff00-\uffef]/u.test(part) ? (
      <HanziText className="deck-text" key={index} size="sm">
        {part}
      </HanziText>
    ) : (
      part
    ),
  )
}

export interface DeckListViewProps {
  state: ApiQueryState
  decks: readonly DeckSummary[]
  error?: ApiError
  draft: DeckDraft
  validation?: string
  createError?: ApiError
  pending: boolean
  topics: readonly TopicChoice[]
  topicsState: ApiQueryState
  topicsError?: ApiError
  page: number
  onPageChange: (page: number) => void
  onChange: (draft: DeckDraft) => void
  onCreate: () => void
  onRetry: () => void
  onTopicsRetry: () => void
}

export function DeckListView(props: DeckListViewProps) {
  const page = clientPage(props.page, props.decks.length)
  return (
    <div className="library-screen deck-library mx-auto w-full min-w-0 max-w-content space-y-4 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <header className="flex min-w-0 flex-wrap items-end justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h1 className="inline-flex items-center gap-3 text-h1 text-ink">
            <span aria-hidden="true" className="h-8 w-1.5 rounded-pill bg-brand-600" />
            Bộ thẻ của tôi
          </h1>
          <p className="inline-flex items-start gap-2 text-body-sm text-ink-secondary">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-600" />
            Giới hạn: 50 bộ thẻ, tối đa 500 thẻ trong mỗi bộ.
          </p>
        </div>
        {props.state === 'ready' ? (
          <span className="inline-flex items-center gap-2 rounded-pill bg-surface px-4 py-2 text-label text-brand-700 shadow-sm">
            <Layers3 aria-hidden="true" className="size-4" />
            {props.decks.length} bộ thẻ
          </span>
        ) : null}
      </header>
      <div className="deck-workspace grid min-w-0 items-start gap-4 lg:grid-cols-2 xl:grid-cols-3">
        <section
          aria-label="Danh sách bộ thẻ"
          className="deck-catalog deck-enter min-w-0 space-y-4 xl:col-span-2"
        >
          {props.state === 'loading' ? (
            <div aria-label="Đang tải bộ thẻ">
              <Skeleton className="h-32" />
            </div>
          ) : null}
          {props.state === 'error' ? (
            <ErrorState
              title="Chưa tải được bộ thẻ"
              message={props.error ? flashcardError(props.error) : 'Vui lòng thử lại.'}
              requestId={props.error?.requestId}
              onRetry={props.onRetry}
            />
          ) : null}
          {props.state === 'empty' ? (
            <EmptyState
              className="border-brand-100 py-6"
              icon={<Layers3 aria-hidden="true" className="size-5" />}
              title="Chưa có bộ thẻ"
              description="Tạo bộ trống hoặc chọn một chủ đề để bắt đầu."
            />
          ) : null}
          {props.state === 'ready' ? (
            <>
              <ul className="grid min-w-0 gap-4 xl:grid-cols-2">
                {props.decks
                  .slice(page * flashcardPageSize, (page + 1) * flashcardPageSize)
                  .map((deck) => (
                    <li
                      className="group relative isolate flex min-h-40 min-w-0 flex-col gap-3 rounded-lg border-b-4 border-transparent bg-surface p-5 shadow-sm transition duration-200 hover:border-brand-600 focus-within:border-brand-600 motion-safe:hover:-translate-y-0.5"
                      key={deck.id}
                    >
                      <h2 className="break-words text-h3 text-ink">
                        <DeckText text={deck.name} />
                      </h2>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="inline-flex items-center gap-1.5 rounded-pill bg-brand-50 px-3 py-1 text-label text-brand-700">
                          <Layers3 aria-hidden="true" className="size-4" />
                          {deck.cardCount} thẻ · {deck.dueCount} đến hạn
                        </span>
                      </div>
                      {deck.description ? (
                        <p className="line-clamp-2 whitespace-pre-wrap break-words text-body-sm text-ink-secondary">
                          <DeckText text={deck.description} />
                        </p>
                      ) : null}
                      <Link
                        className="mt-auto inline-flex items-center gap-1 self-end text-label text-brand-700 no-underline after:absolute after:inset-0 after:rounded-lg focus-visible:outline-none"
                        to={`/library/decks/${deck.id}`}
                      >
                        Mở bộ thẻ
                        <ArrowRight
                          aria-hidden="true"
                          className="size-4 transition-transform duration-200 motion-safe:group-hover:translate-x-1"
                        />
                      </Link>
                    </li>
                  ))}
              </ul>
              <Pagination
                page={page + 1}
                size={flashcardPageSize}
                total={props.decks.length}
                onChange={(value) => props.onPageChange(value - 1)}
              />
            </>
          ) : null}
        </section>
        <form
          aria-label="Tạo bộ thẻ"
          noValidate
          className="deck-create-panel deck-enter min-w-0 space-y-4 rounded-md border border-brand-100 bg-surface p-4 shadow-sm sm:p-5 lg:sticky lg:top-20"
          onSubmit={(event) => {
            event.preventDefault()
            props.onCreate()
          }}
        >
          <div className="relative isolate overflow-hidden rounded-md bg-brand-50 p-4">
            <div className="relative z-base flex items-start gap-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-surface text-brand-600">
                <Plus aria-hidden="true" className="size-5" />
              </span>
              <div>
                <h2 className="text-h3 text-ink">Tạo bộ thẻ</h2>
                <p className="mt-1 text-body-sm text-ink-secondary">
                  Gom từ mới thành một bộ để học từng chút mỗi ngày.
                </p>
              </div>
            </div>
          </div>
          <fieldset disabled={props.pending}>
            <legend className="sr-only">Thông tin bộ mới</legend>
            <FormField
              htmlFor="deck-name"
              label="Tên bộ thẻ"
              hint={`${characterCount(props.draft.name)}/100 ký tự`}
              required
            >
              <Input
                className="font-hanzi"
                value={props.draft.name}
                onChange={(event) => props.onChange({ ...props.draft, name: event.target.value })}
              />
            </FormField>
            <FormField
              htmlFor="deck-description"
              label="Mô tả"
              hint={`${characterCount(props.draft.description)}/500 ký tự`}
            >
              <Textarea
                className="min-h-24 font-hanzi"
                value={props.draft.description}
                onChange={(event) =>
                  props.onChange({ ...props.draft, description: event.target.value })
                }
              />
            </FormField>
            <FormField htmlFor="deck-source" label="Nguồn tạo bộ thẻ">
              <Select
                value={props.draft.source}
                onChange={(event) =>
                  props.onChange({
                    ...props.draft,
                    source: event.target.value === 'topic' ? 'topic' : 'empty',
                  })
                }
              >
                <option value="empty">Bộ trống</option>
                <option value="topic">Từ chủ đề</option>
              </Select>
            </FormField>
            {props.draft.source === 'topic' ? (
              <>
                {props.topicsState === 'loading' ? <p role="status">Đang tải chủ đề…</p> : null}
                {props.topicsState === 'error' ? (
                  <ErrorState
                    title="Chưa tải được chủ đề"
                    message={
                      props.topicsError ? flashcardError(props.topicsError) : 'Vui lòng thử lại.'
                    }
                    onRetry={props.onTopicsRetry}
                  />
                ) : null}
                {props.topicsState === 'empty' ? (
                  <p className="text-body-sm text-ink-secondary">
                    Chưa có chủ đề để tạo bộ. Bạn vẫn có thể tạo bộ trống.
                  </p>
                ) : null}
                {props.topicsState === 'ready' ? (
                  <FormField htmlFor="deck-topic" label="Chủ đề" required>
                    <Select
                      className="font-hanzi"
                      value={props.draft.topicId}
                      onChange={(event) =>
                        props.onChange({ ...props.draft, topicId: event.target.value })
                      }
                    >
                      <option value="">Chọn chủ đề</option>
                      {props.topics.map((topic) => (
                        <option key={topic.id} value={topic.id}>
                          {topic.name} ({topic.totalWords} từ)
                        </option>
                      ))}
                    </Select>
                  </FormField>
                ) : null}
              </>
            ) : null}
          </fieldset>
          {props.validation ? (
            <p role="alert" className="text-body-sm text-danger">
              {props.validation}
            </p>
          ) : null}
          {props.createError ? (
            <div role="alert">
              <ErrorState
                title="Chưa tạo được bộ thẻ"
                message={flashcardError(props.createError)}
                requestId={props.createError.requestId}
              />
            </div>
          ) : null}
          <Button
            className="w-full rounded-pill"
            type="submit"
            loading={props.pending}
            disabled={props.draft.source === 'topic' && props.topicsState !== 'ready'}
          >
            <Sparkles aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
            Tạo bộ thẻ
          </Button>
        </form>
      </div>
    </div>
  )
}
