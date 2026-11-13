import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, Card, PageHeader } from '@/shared/ui'

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
    <main className="mx-auto w-full min-w-0 max-w-content px-4 py-6 text-ink [overflow-wrap:anywhere]">
      <Link to="/references/books">← Danh mục sách tham khảo</Link>
      <PageHeader title="Chi tiết sách tham khảo" className="mt-4" />
      {state === 'loading' ? <p role="status">Đang tải sách…</p> : null}
      {state === 'error' ? (
        <div role="alert" className="space-y-3">
          <p>{error?.message ?? 'Không tải được sách.'}</p>
          <Button onClick={onRetry} variant="outline">
            Thử lại
          </Button>
        </div>
      ) : null}
      {state === 'empty' ? <p role="status">Không tìm thấy sách</p> : null}
      {state === 'ready' && book ? (
        <Card className="min-w-0 space-y-4">
          {book.thumbnailUrl ? (
            <img
              alt=""
              src={book.thumbnailUrl}
              className="max-h-80 max-w-full rounded-sm object-contain"
            />
          ) : null}
          <h2 className="text-h2">{book.title}</h2>
          <dl className="grid gap-2 text-body-sm">
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
          <p>
            {book.hskMin === null && book.hskMax === null
              ? 'Mọi cấp HSK'
              : `Cấp HSK: ${book.hskMin ?? 'không giới hạn'} – ${book.hskMax ?? 'không giới hạn'}`}
          </p>
          <ul aria-label="Tag tài liệu" className="flex flex-wrap gap-2 text-caption">
            {book.tags.map((tag, index) => (
              <li key={`${tag}-${index}`}>{tag}</li>
            ))}
          </ul>
          {book.description ? (
            <p className="whitespace-pre-wrap text-body">{book.description}</p>
          ) : null}
          <div className="flex flex-wrap items-center gap-3">
            <Button href={book.url} target="_blank" rel="noopener noreferrer">
              Mua / tham khảo
            </Button>
            {book.isAffiliate ? (
              <span className="text-body-sm text-ink-secondary">Link tiếp thị liên kết</span>
            ) : null}
            {canReport ? (
              <Button onClick={onReport} variant="ghost">
                Báo lỗi
              </Button>
            ) : null}
          </div>
          {canReport ? report : null}
        </Card>
      ) : null}
    </main>
  )
}
