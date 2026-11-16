import type { ReactNode } from 'react'
import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  PageHeader,
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
    <main className="mx-auto w-full min-w-0 max-w-reading break-words px-4 py-6 text-ink">
      <PageHeader
        title="Chi tiết sách tham khảo"
        actions={
          state === 'ready' && book ? (
            <div className="flex flex-wrap items-center gap-2">
              <Button href={book.url} target="_blank" rel="noopener noreferrer">
                Mua / tham khảo
              </Button>
              {book.isAffiliate ? (
                <StatusBadge tone="info">Link tiếp thị liên kết</StatusBadge>
              ) : null}
            </div>
          ) : undefined
        }
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
        <>
          <Card className="min-w-0 space-y-4">
            <div className={book.thumbnailUrl ? 'grid gap-4 sm:grid-cols-2' : undefined}>
              {book.thumbnailUrl ? (
                <img
                  alt=""
                  src={book.thumbnailUrl}
                  className="max-h-80 w-full rounded-sm bg-surface-subtle object-contain"
                />
              ) : null}
              <div className="min-w-0 space-y-3">
                <h2 className="text-h2 text-brand-800">{book.title}</h2>
                <dl className="grid gap-3 rounded-sm bg-surface-subtle p-4 text-body-sm">
                  <div>
                    <dt className="font-semibold">Tác giả</dt>
                    <dd>{book.author ?? 'Đang cập nhật'}</dd>
                  </div>
                  <div>
                    <dt className="font-semibold">Nhà xuất bản</dt>
                    <dd>{book.publisher ?? 'Đang cập nhật'}</dd>
                  </div>
                  <div>
                    <dt className="font-semibold">Năm xuất bản</dt>
                    <dd>{book.publishedYear ?? 'Đang cập nhật'}</dd>
                  </div>
                </dl>
                <p className="w-fit rounded-pill bg-brand-50 px-3 py-1 text-label text-brand-800">
                  {book.hskMin === null && book.hskMax === null
                    ? 'Mọi cấp HSK'
                    : `Cấp HSK: ${book.hskMin ?? 'không giới hạn'} – ${book.hskMax ?? 'không giới hạn'}`}
                </p>
                <ul aria-label="Tag tài liệu" className="flex flex-wrap gap-2 text-caption">
                  {book.tags.map((tag, index) => (
                    <li
                      key={`${tag}-${index}`}
                      className="rounded-sm bg-canvas px-2 py-1 text-ink-secondary"
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
              className="mt-6"
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
        </>
      ) : null}
      <Button className="mt-6" to="/references/books" variant="outline">
        ← Danh mục sách tham khảo
      </Button>
    </main>
  )
}
