import { Search } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  PageBanner,
  Button,
  EmptyState,
  ErrorState,
  Input,
  PageState,
  Pagination,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
} from '@/shared/ui'

import type { PublicReference, PublicReferencePage } from '../types'
import { BookCover } from './BookCover'
import './ReferenceListView.css'

export type ReferenceListViewProps = {
  state: ApiQueryState
  result?: PublicReferencePage
  error?: ApiError
  hsk: string
  tag: string
  onHskChange: (hsk: string) => void
  onTagChange: (tag: string) => void
  onApply: () => void
  onPageChange: (page: number) => void
  onRetry: () => void
}

const levels = [
  { value: '', label: 'Mọi cấp' },
  ...SUPPORTED_HSK_LEVELS.map((level) => ({ value: String(level), label: `HSK ${level}` })),
]

function BookTile({ item, index }: { item: PublicReference; index: number }) {
  const href = `/references/books/${item.id}`
  const facts = [item.publisher, item.publishedYear].filter(Boolean).join(' · ')
  return (
    <li
      className="reference-shelf-item group min-w-0"
      style={{ animationDelay: `${Math.min(index, 10) * 40}ms` }}
    >
      <Link
        aria-hidden="true"
        className="block transition-transform duration-200 motion-safe:group-hover:-translate-y-1"
        tabIndex={-1}
        to={href}
      >
        <BookCover book={item} />
      </Link>
      <div className="mt-3 min-w-0 space-y-1 break-words">
        <h2 className="line-clamp-2 text-label text-ink">
          <Link
            className="rounded-sm hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
            to={href}
          >
            {item.title}
          </Link>
        </h2>
        {item.author ? (
          <p className="line-clamp-1 text-body-sm text-ink-secondary">{item.author}</p>
        ) : null}
        {facts ? <p className="text-caption text-ink-muted">{facts}</p> : null}
        {item.previewUrl ? (
          <p className="inline-flex rounded-pill bg-success-bg px-2 py-0.5 text-caption text-success">
            Đọc được ngay
          </p>
        ) : null}
      </div>
    </li>
  )
}

/** The HSK book shelf: published, reputable titles only; each opens its own detail page. */
export function ReferenceListView(props: ReferenceListViewProps) {
  const { state, result, error, hsk, tag } = props
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 text-ink sm:px-6 lg:px-8">
      <PageBanner
        className="mb-0"
        title="Danh mục sách tham khảo"
        subtitle="Sách HSK đã phát hành công khai, chọn lọc từ nhà xuất bản uy tín. Bấm vào sách để xem thông tin và nơi mua."
      />

      <div className="flex min-w-0 flex-col gap-4 border-b border-line pb-4 lg:flex-row lg:items-end lg:justify-between">
        <fieldset className="m-0 min-w-0 border-0 p-0">
          <legend className="mb-2 text-label text-ink-secondary">Cấp HSK</legend>
          <div className="flex flex-wrap gap-2">
            {levels.map((option) => {
              const active = hsk === option.value
              return (
                <button
                  aria-pressed={active}
                  className={`min-h-touch rounded-pill border px-4 text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
                    active
                      ? 'border-brand-600 bg-brand-600 text-surface'
                      : 'border-line bg-surface text-ink hover:border-brand-600 hover:text-brand-700'
                  }`}
                  key={option.value || 'all'}
                  onClick={() => props.onHskChange(option.value)}
                  type="button"
                >
                  {option.label}
                </button>
              )
            })}
          </div>
        </fieldset>
        <form
          aria-label="Lọc sách"
          className="flex min-w-0 items-end gap-2 lg:w-96"
          onSubmit={(event) => {
            event.preventDefault()
            props.onApply()
          }}
        >
          <div className="min-w-0 flex-1">
            <label className="mb-2 block text-label text-ink-secondary" htmlFor="reference-tag">
              Tag
            </label>
            <div className="relative">
              <Search
                aria-hidden="true"
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted"
              />
              <Input
                className="pl-9"
                id="reference-tag"
                maxLength={30}
                onChange={(event) => props.onTagChange(event.target.value)}
                placeholder="giáo trình, luyện đề, ngữ pháp…"
                value={tag}
              />
            </div>
          </div>
          <Button type="submit">Áp dụng</Button>
        </form>
      </div>

      <PageState
        state={state}
        loading={
          <div role="status">
            <span className="sr-only">Đang tải sách…</span>
            <div
              aria-hidden="true"
              className="grid grid-cols-2 gap-x-5 gap-y-8 sm:grid-cols-3 lg:grid-cols-5"
            >
              {Array.from({ length: 5 }, (_, index) => (
                <Skeleton className="aspect-[3/4] w-full" key={index} />
              ))}
            </div>
          </div>
        }
        error={
          <div role="alert">
            <ErrorState
              title="Chưa tải được danh mục"
              message={error?.message ?? 'Không tải được sách.'}
              requestId={error?.requestId}
              onRetry={props.onRetry}
            />
          </div>
        }
        empty={
          <EmptyState
            title="Chưa có sách phù hợp."
            description="Thử chọn cấp HSK khác hoặc xoá tag để mở rộng danh mục."
          />
        }
      >
        <div className="space-y-6">
          <p aria-live="polite" className="text-body-sm text-ink-secondary">
            <span className="font-semibold tabular-nums text-ink">{result?.total ?? 0}</span> sách
            {hsk ? ` cho HSK ${hsk}` : ''}
          </p>
          <ul
            aria-label="Kệ sách"
            className="grid min-w-0 grid-cols-2 gap-x-5 gap-y-8 sm:grid-cols-3 lg:grid-cols-5"
          >
            {result?.items.map((item, index) => (
              <BookTile index={index} item={item} key={item.id} />
            ))}
          </ul>
        </div>
      </PageState>
      {(state === 'ready' || state === 'empty') && result ? (
        <Pagination
          page={result.page + 1}
          size={result.size}
          total={result.total}
          onChange={(page) => props.onPageChange(page - 1)}
        />
      ) : null}
    </main>
  )
}
