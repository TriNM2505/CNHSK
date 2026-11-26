import type { ReactNode } from 'react'
import { ArrowLeft, BookOpenText, ExternalLink, Flag } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiError, ApiQueryState } from '@/shared/api'
import { PageBanner, Button, EmptyState, ErrorState, Skeleton, StatusBadge } from '@/shared/ui'

import { hskRangeLabel } from '../referenceLabels'
import type { PublicReference } from '../types'
import { BookCover } from './BookCover'

export type BookDetailViewProps = {
  state: ApiQueryState
  book?: PublicReference
  error?: ApiError
  canReport: boolean
  report?: ReactNode
  onReport: () => void
  onRetry: () => void
}

function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

function BackLink() {
  return (
    <Link
      className="inline-flex min-h-touch items-center gap-1.5 rounded-sm text-label text-ink-secondary hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
      to="/references/books"
    >
      <ArrowLeft aria-hidden="true" className="size-4" />
      Danh mục sách tham khảo
    </Link>
  )
}

export function BookDetailView({
  state,
  book,
  error,
  canReport,
  report,
  onReport,
  onRetry,
}: BookDetailViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 break-words px-4 py-6 pb-16 text-ink sm:px-6 lg:px-8">
      <PageBanner
        className="mb-0"
        title="Chi tiết sách tham khảo"
        subtitle="Thông tin xuất bản, cấp HSK khuyến nghị và nơi phát hành."
      />
      <BackLink />
      {state === 'loading' ? (
        <div role="status">
          <span className="sr-only">Đang tải sách…</span>
          <div aria-hidden="true" className="grid gap-8 sm:grid-cols-[minmax(0,16rem)_1fr]">
            <Skeleton className="aspect-[3/4] w-full" />
            <Skeleton variant="card" />
          </div>
        </div>
      ) : null}
      {state === 'error' ? (
        <div role="alert">
          <ErrorState
            title="Chưa tải được sách"
            message={error?.message ?? 'Không tải được sách.'}
            requestId={error?.requestId}
            onRetry={onRetry}
          />
        </div>
      ) : null}
      {state === 'empty' ? (
        <EmptyState
          title="Không tìm thấy sách"
          description="Bạn có thể chọn một sách khác trong danh mục."
        />
      ) : null}
      {state === 'ready' && book ? (
        <article className="grid min-w-0 items-start gap-8 sm:grid-cols-[minmax(0,14rem)_1fr] lg:grid-cols-[minmax(0,18rem)_1fr]">
          <div className="mx-auto w-48 sm:mx-0 sm:w-full">
            <BookCover book={book} large />
          </div>

          <div className="min-w-0 space-y-6">
            <header className="space-y-2">
              <p className="text-label uppercase tracking-wider text-brand-700">
                {hskRangeLabel(book.hskMin, book.hskMax)}
              </p>
              <h2 className="text-h1-sm text-ink sm:text-h1">{book.title}</h2>
              {book.author ? <p className="text-body text-ink-secondary">{book.author}</p> : null}
            </header>

            <dl className="grid grid-cols-1 border-y border-line text-body-sm sm:grid-cols-3">
              {[
                ['Tác giả', book.author],
                ['Nhà xuất bản', book.publisher],
                ['Năm xuất bản', book.publishedYear],
              ].map(([label, value]) => (
                <div
                  className="border-line py-3 sm:border-l sm:px-4 sm:first:border-l-0 sm:first:pl-0"
                  key={label}
                >
                  <dt className="text-caption uppercase tracking-wider text-ink-muted">{label}</dt>
                  <dd className={`mt-1 font-medium ${value ? 'text-ink' : 'text-ink-muted'}`}>
                    {value ?? 'Đang cập nhật'}
                  </dd>
                </div>
              ))}
            </dl>

            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-3">
                {book.previewUrl ? (
                  <Button href={book.previewUrl} rel="noopener noreferrer" target="_blank">
                    <BookOpenText
                      aria-hidden="true"
                      className="mr-2 inline-block size-4 align-middle"
                    />
                    Đọc ngay
                  </Button>
                ) : null}
                {/* A free document has no shop page: its main link is the reading link itself. */}
                {book.previewUrl !== book.url ? (
                  <Button
                    href={book.url}
                    rel="noopener noreferrer"
                    target="_blank"
                    variant={book.previewUrl ? 'outline' : 'primary'}
                  >
                    Mua / tham khảo
                  </Button>
                ) : null}
                {book.isAffiliate ? (
                  <StatusBadge tone="info">Link tiếp thị liên kết</StatusBadge>
                ) : null}
              </div>
              <p className="inline-flex items-center gap-1.5 text-caption text-ink-muted">
                <ExternalLink aria-hidden="true" className="size-3.5" />
                {!book.previewUrl
                  ? `Mở ${hostOf(book.url)} ở tab mới`
                  : book.previewUrl === book.url
                    ? `Tài liệu miễn phí, đọc trên ${hostOf(book.url)} ở tab mới`
                    : `Đọc trên ${hostOf(book.previewUrl)} · mua tại ${hostOf(book.url)}, đều mở ở tab mới`}
              </p>
            </div>

            {book.description ? (
              <section aria-labelledby="book-about" className="space-y-2">
                <h3 className="text-h3 text-ink" id="book-about">
                  Giới thiệu
                </h3>
                <p className="max-w-reading whitespace-pre-wrap text-body leading-relaxed text-ink">
                  {book.description}
                </p>
              </section>
            ) : null}

            {book.tags.length ? (
              <ul aria-label="Tag tài liệu" className="flex flex-wrap gap-2 text-caption">
                {book.tags.map((tag, index) => (
                  <li
                    className="rounded-pill bg-canvas px-3 py-1 text-ink-secondary"
                    key={`${tag}-${index}`}
                  >
                    {tag}
                  </li>
                ))}
              </ul>
            ) : null}

            {canReport ? (
              <section
                aria-label="Báo lỗi tài liệu"
                className="space-y-2 border-t border-line pt-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-body-sm text-ink-secondary">
                    Liên kết hỏng hoặc thông tin sai?
                  </p>
                  <Button onClick={onReport} size="sm" variant="ghost">
                    <Flag aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
                    Báo lỗi
                  </Button>
                </div>
                {report}
              </section>
            ) : null}
          </div>
        </article>
      ) : null}
    </main>
  )
}
