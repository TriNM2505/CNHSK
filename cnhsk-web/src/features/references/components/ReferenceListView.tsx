import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  PageState,
  Pagination,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
  Tabs,
} from '@/shared/ui'

import type { PublicReferencePage, ReferenceKind } from '../types'

export type ReferenceListViewProps = {
  kind: ReferenceKind
  state: ApiQueryState
  result?: PublicReferencePage
  error?: ApiError
  hsk: string
  tag: string
  canReport: boolean
  reportId?: number
  report?: ReactNode
  onReport: (id: number) => void
  onKindChange: (kind: ReferenceKind) => void
  onHskChange: (hsk: string) => void
  onTagChange: (tag: string) => void
  onApply: () => void
  onPageChange: (page: number) => void
  onRetry: () => void
}

export function ReferenceListView(props: ReferenceListViewProps) {
  const { kind, state, result, error, hsk, tag, canReport, reportId, report } = props
  const books = kind === 'BOOK'
  const content = (
    <>
      <form
        aria-label="Lọc tài liệu"
        className="my-4 grid min-w-0 gap-x-4 rounded-md border border-line bg-surface p-4 sm:grid-cols-2 lg:grid-cols-3"
        onSubmit={(event) => {
          event.preventDefault()
          props.onApply()
        }}
      >
        <FormField htmlFor="reference-hsk" label="Cấp HSK">
          <Select value={hsk} onChange={(event) => props.onHskChange(event.target.value)}>
            <option value="">Mọi cấp</option>
            {SUPPORTED_HSK_LEVELS.map((level) => (
              <option key={level} value={level}>
                HSK {level}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField htmlFor="reference-tag" label="Tag">
          <Input
            maxLength={30}
            value={tag}
            onChange={(event) => props.onTagChange(event.target.value)}
          />
        </FormField>
        <Button type="submit" className="justify-self-start sm:col-span-2 lg:col-span-1 lg:mt-7">
          Áp dụng
        </Button>
      </form>
      <PageState
        state={state}
        loading={
          <div role="status">
            <span className="sr-only">Đang tải tài liệu…</span>
            <div aria-hidden="true" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Skeleton variant="card" />
              <Skeleton variant="card" />
            </div>
          </div>
        }
        error={
          <div role="alert">
            <ErrorState
              title="Chưa tải được danh mục"
              message={error?.message ?? 'Không tải được tài liệu.'}
              requestId={error?.requestId}
              onRetry={props.onRetry}
            />
          </div>
        }
        empty={
          <EmptyState
            title="Chưa có tài liệu phù hợp."
            description="Thử chọn cấp HSK khác hoặc xoá tag để mở rộng danh mục."
          />
        }
      >
        <div className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {result?.items.map((item) => (
            <Card key={item.id} className="flex min-w-0 flex-col gap-3 break-words">
              {item.thumbnailUrl ? (
                <img
                  alt=""
                  loading="lazy"
                  className="aspect-video w-full rounded-sm object-cover"
                  src={item.thumbnailUrl}
                />
              ) : null}
              <h2 className="text-h3 text-ink">
                {books ? (
                  <Link
                    className="rounded-sm text-brand-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                    to={`/references/books/${item.id}`}
                  >
                    {item.title}
                  </Link>
                ) : (
                  item.title
                )}
              </h2>
              {books && item.author ? (
                <p className="mt-1 text-body-sm text-ink-secondary">{item.author}</p>
              ) : null}
              {item.description ? (
                <p className="my-3 line-clamp-3 whitespace-pre-wrap text-body-sm text-ink-secondary">
                  {item.description}
                </p>
              ) : null}
              <p className="w-fit rounded-pill bg-brand-50 px-3 py-1 text-label text-brand-800">
                {item.hskMin === null && item.hskMax === null
                  ? 'Mọi cấp HSK'
                  : item.hskMin === null
                    ? `Đến HSK ${item.hskMax}`
                    : item.hskMax === null
                      ? `Từ HSK ${item.hskMin}`
                      : `HSK ${item.hskMin}–${item.hskMax}`}
              </p>
              <ul
                aria-label="Tag tài liệu"
                className="mb-3 flex flex-wrap gap-2 text-caption text-ink-secondary"
              >
                {item.tags.map((value, index) => (
                  <li key={`${value}-${index}`} className="rounded-sm bg-canvas px-2 py-1">
                    {value}
                  </li>
                ))}
              </ul>
              <div className="mt-auto flex flex-wrap gap-2 border-t border-line pt-3">
                {books ? (
                  <Button to={`/references/books/${item.id}`} size="sm" variant="outline">
                    Xem chi tiết
                  </Button>
                ) : (
                  <Button href={item.url} target="_blank" rel="noopener noreferrer" size="sm">
                    Mở
                  </Button>
                )}
                {canReport ? (
                  <details className="relative">
                    <summary className="min-h-touch cursor-pointer rounded-sm px-3 py-3 text-caption text-brand-700">
                      Tuỳ chọn
                    </summary>
                    <Button onClick={() => props.onReport(item.id)} size="sm" variant="ghost">
                      Báo lỗi
                    </Button>
                  </details>
                ) : null}
              </div>
              {canReport && reportId === item.id ? report : null}
            </Card>
          ))}
        </div>
      </PageState>
      {(state === 'ready' || state === 'empty') && result ? (
        <div className="mt-6">
          <Pagination
            page={result.page + 1}
            size={result.size}
            total={result.total}
            onChange={(page) => props.onPageChange(page - 1)}
          />
        </div>
      ) : null}
    </>
  )
  return (
    <main className="mx-auto w-full min-w-0 max-w-content px-4 py-6 text-ink">
      <PageHeader
        title={books ? 'Danh mục sách tham khảo' : 'Tài liệu nghe nhìn'}
        subtitle="Nguồn tham khảo bên ngoài, mở ở tab mới."
      />
      <nav aria-label="Loại tài liệu" className="mb-4 flex flex-wrap gap-4 text-body-sm">
        <Button
          to="/references/media"
          variant={books ? 'outline' : 'secondary'}
          aria-current={books ? undefined : 'page'}
        >
          YouTube & Podcast
        </Button>
        <Button
          to="/references/books"
          variant={books ? 'secondary' : 'outline'}
          aria-current={books ? 'page' : undefined}
        >
          Sách tham khảo
        </Button>
      </nav>
      {books ? (
        content
      ) : (
        <Tabs
          label="Nguồn nghe nhìn"
          value={kind}
          onChange={(value) => props.onKindChange(value as ReferenceKind)}
          items={[
            { id: 'YOUTUBE_CHANNEL', label: 'YouTube', panel: content },
            { id: 'PODCAST', label: 'Podcast', panel: content },
          ]}
        />
      )}
    </main>
  )
}
