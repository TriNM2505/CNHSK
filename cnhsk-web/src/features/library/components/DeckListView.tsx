import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Textarea,
} from '@/shared/ui'

import {
  characterCount,
  clientPage,
  flashcardError,
  flashcardPageSize,
  type DeckDraft,
  type TopicChoice,
} from '../flashcardForms'
import type { DeckSummary } from '../types'

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
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Bộ thẻ của tôi"
        subtitle="Tạo bộ thẻ riêng và ôn lại những điều muốn nhớ."
      />
      <p className="text-body-sm text-ink-secondary">
        Giới hạn: 50 bộ thẻ, tối đa 500 thẻ trong mỗi bộ.
      </p>
      <div className="grid items-start gap-6 lg:grid-cols-2">
        <section aria-label="Danh sách bộ thẻ" className="space-y-4">
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
              title="Chưa có bộ thẻ"
              description="Tạo bộ trống hoặc chọn một chủ đề để bắt đầu."
            />
          ) : null}
          {props.state === 'ready' ? (
            <>
              <ul className="space-y-3">
                {props.decks
                  .slice(page * flashcardPageSize, (page + 1) * flashcardPageSize)
                  .map((deck) => (
                    <li
                      key={deck.id}
                      className="space-y-2 rounded-md border border-line bg-surface p-4"
                    >
                      <h2 className="break-words text-h3 text-ink">{deck.name}</h2>
                      {deck.description ? (
                        <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
                          {deck.description}
                        </p>
                      ) : null}
                      <p className="text-body-sm text-ink-secondary">
                        {deck.cardCount} thẻ · {deck.dueCount} đến hạn
                      </p>
                      <Button to={`/library/decks/${deck.id}`} variant="outline">
                        Mở bộ thẻ
                      </Button>
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
          className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
          onSubmit={(event) => {
            event.preventDefault()
            props.onCreate()
          }}
        >
          <h2 className="text-h3 text-ink">Tạo bộ thẻ</h2>
          <fieldset disabled={props.pending}>
            <legend className="sr-only">Thông tin bộ mới</legend>
            <FormField
              htmlFor="deck-name"
              label="Tên bộ thẻ"
              hint={`${characterCount(props.draft.name)}/100 ký tự`}
              required
            >
              <Input
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
            type="submit"
            loading={props.pending}
            disabled={props.draft.source === 'topic' && props.topicsState !== 'ready'}
          >
            Tạo bộ thẻ
          </Button>
        </form>
      </div>
    </div>
  )
}
