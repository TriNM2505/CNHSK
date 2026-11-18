import type { ReactNode } from 'react'
import { BookOpen, ExternalLink } from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  PageBanner,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Section,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import type { PublicReference } from '../types'

export type BookDetailViewProps = {
  state: ApiQueryState
  book?: PublicReference
  error?: ApiError
  canReport: boolean
  report?: ReactNode
  onReport: () => void
  onRetry: () => void
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
        subtitle="Thông tin xuất bản, cấp HSK khuyến nghị và liên kết tham khảo."
      />
      {state === 'loading' ? (
        <div role="status">
          <span className="sr-only">Đang tải sách…</span>
          <div aria-hidden="true">
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
        <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
          <div className="min-w-0 space-y-6 lg:col-span-8">
            <Card className="min-w-0 space-y-5 border-brand-100 p-6">
              <div className={book.thumbnailUrl ? 'grid gap-5 sm:grid-cols-2' : undefined}>
                {book.thumbnailUrl ? (
                  <img
                    alt=""
                    src={book.thumbnailUrl}
                    className="max-h-80 w-full rounded-sm bg-surface-subtle object-contain"
                  />
                ) : null}
                <div className="min-w-0 space-y-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-50 text-brand-700">
                        <BookOpen aria-hidden="true" className="size-5" />
                      </div>
                      <h2 className="text-h2 text-brand-800">{book.title}</h2>
                    </div>
                    <p className="w-fit rounded-pill bg-brand-50 px-3 py-1 text-label text-brand-800">
                      {book.hskMin === null && book.hskMax === null
                        ? 'Mọi cấp HSK'
                        : `Cấp HSK: ${book.hskMin ?? 'không giới hạn'} – ${book.hskMax ?? 'không giới hạn'}`}
                    </p>
                  </div>

                  <dl className="grid gap-3 rounded-md border border-brand-100 bg-brand-50 p-4 text-body-sm sm:grid-cols-3">
                    <div>
                      <dt className="font-semibold text-ink-secondary">Tác giả</dt>
                      <dd className="mt-1 font-medium text-ink">{book.author ?? 'Đang cập nhật'}</dd>
                    </div>
                    <div>
                      <dt className="font-semibold text-ink-secondary">Nhà xuất bản</dt>
                      <dd className="mt-1 font-medium text-ink">
                        {book.publisher ?? 'Đang cập nhật'}
                      </dd>
                    </div>
                    <div>
                      <dt className="font-semibold text-ink-secondary">Năm xuất bản</dt>
                      <dd className="mt-1 font-medium text-ink">
                        {book.publishedYear ?? 'Đang cập nhật'}
                      </dd>
                    </div>
                  </dl>

                  <ul aria-label="Tag tài liệu" className="flex flex-wrap gap-2 text-caption">
                    {book.tags.map((tag, index) => (
                      <li
                        key={`${tag}-${index}`}
                        className="rounded-sm bg-canvas px-2.5 py-1 text-ink-secondary"
                      >
                        {tag}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
              {book.description ? (
                <p className="whitespace-pre-wrap border-t border-line pt-4 text-body">
                  {book.description}
                </p>
              ) : null}
            </Card>

            {canReport ? (
              <Section
                title="Báo lỗi tài liệu"
                actions={
                  <Button onClick={onReport} variant="outline">
                    Báo lỗi
                  </Button>
                }
              >
                {report}
              </Section>
            ) : null}
          </div>

          <div className="min-w-0 space-y-5 lg:col-span-4">
            <Card className="min-w-0 space-y-4 border-brand-100 bg-brand-50 p-5">
              <div className="flex items-center gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <ExternalLink aria-hidden="true" className="size-5" />
                </div>
                <div>
                  <h3 className="text-h3 text-ink">Liên kết tham khảo</h3>
                  <p className="text-caption text-ink-secondary">
                    Mở trang giới thiệu hoặc phát hành sách ở tab mới
                  </p>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2 border-t border-brand-100 pt-3">
                <Button href={book.url} target="_blank" rel="noopener noreferrer">
                  Mua / tham khảo
                </Button>
                {book.isAffiliate ? (
                  <StatusBadge tone="info">Link tiếp thị liên kết</StatusBadge>
                ) : null}
              </div>

              <div className="border-t border-brand-100 pt-3">
                <Button to="/references/books" variant="outline">
                  ← Danh mục sách tham khảo
                </Button>
              </div>
            </Card>
          </div>
        </div>
      ) : (
        <div>
          <Button to="/references/books" variant="outline">
            ← Danh mục sách tham khảo
          </Button>
        </div>
      )}
    </main>
  )
}
