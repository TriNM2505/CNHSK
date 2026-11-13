import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Select,
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
        className="my-4 grid min-w-0 gap-x-4 sm:grid-cols-[10rem_minmax(0,1fr)_auto] sm:items-start"
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
        <Button type="submit" className="mb-4 sm:mt-7">
          Áp dụng
        </Button>
      </form>
      {state === 'loading' ? <p role="status">Đang tải tài liệu…</p> : null}
      {state === 'error' ? (
        <div role="alert" className="space-y-3">
          <p>{error?.message ?? 'Không tải được tài liệu.'}</p>
          <Button onClick={props.onRetry} variant="outline">
            Thử lại
          </Button>
        </div>
      ) : null}
      {state === 'empty' ? <p role="status">Chưa có tài liệu phù hợp.</p> : null}
      {state === 'ready' && result ? (
        <div className="grid min-w-0 grid-cols-1 gap-4 min-[641px]:grid-cols-2 lg:grid-cols-3">
          {result.items.map((item) => (
            <Card key={item.id} className="min-w-0 break-words [overflow-wrap:anywhere]">
              {item.thumbnailUrl ? (
                <img
                  alt=""
                  loading="lazy"
                  className="mb-3 aspect-video w-full rounded-sm object-cover"
                  src={item.thumbnailUrl}
                />
              ) : null}
              <h2 className="text-h3 text-ink">
                {books ? <Link to={`/references/books/${item.id}`}>{item.title}</Link> : item.title}
              </h2>
              {books && item.author ? (
                <p className="mt-1 text-body-sm text-ink-secondary">{item.author}</p>
              ) : null}
              {item.description ? (
                <p className="my-3 line-clamp-3 whitespace-pre-wrap text-body-sm text-ink-secondary">
                  {item.description}
                </p>
              ) : null}
              <p className="my-2 text-body-sm">
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
              <div className="flex flex-wrap gap-2">
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
      ) : null}
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
        <Link to="/references/media">YouTube & Podcast</Link>
        <Link to="/references/books">Sách tham khảo</Link>
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
