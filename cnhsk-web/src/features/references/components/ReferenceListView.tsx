import type { ReactNode } from 'react'
import { BookOpen, Compass, Layers } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageState,
  Pagination,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
  Tabs,
} from '@/shared/ui'

import type { PublicReferencePage, ReferenceKind } from '../types'
import { InkLandscapeBanner } from './InkLandscapeBanner'

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
  const totalCount = result?.total ?? 0
  const content = (
    <>
      <form
        aria-label="Lọc tài liệu"
        className="my-4 grid min-w-0 items-end gap-x-4 rounded-md border border-brand-100 bg-surface p-4 sm:grid-cols-12"
        onSubmit={(event) => {
          event.preventDefault()
          props.onApply()
        }}
      >
        <div className="sm:col-span-4">
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
        </div>
        <div className="sm:col-span-6">
          <FormField htmlFor="reference-tag" label="Tag">
            <Input
              maxLength={30}
              placeholder="Ví dụ: nghe, hội thoại, đọc..."
              value={tag}
              onChange={(event) => props.onTagChange(event.target.value)}
            />
          </FormField>
        </div>
        <div className="pb-4 sm:col-span-2">
          <Button type="submit" className="w-full">
            Áp dụng
          </Button>
        </div>
      </form>
      <PageState
        state={state}
        loading={
          <div role="status">
            <span className="sr-only">Đang tải tài liệu…</span>
            <div aria-hidden="true" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Skeleton variant="card" />
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
            <Card
              key={item.id}
              className="flex min-w-0 flex-col gap-3 border-brand-100 break-words"
            >
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
                <p className="my-2 line-clamp-3 whitespace-pre-wrap text-body-sm text-ink-secondary">
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
                className="mb-2 flex flex-wrap gap-2 text-caption text-ink-secondary"
              >
                {item.tags.map((value, index) => (
                  <li key={`${value}-${index}`} className="rounded-sm bg-canvas px-2 py-1">
                    {value}
                  </li>
                ))}
              </ul>
              <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-line pt-3">
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
                    <summary className="min-h-touch cursor-pointer rounded-sm px-3 py-2 text-caption text-brand-700">
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
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 text-ink sm:px-6 lg:px-8">
      <InkLandscapeBanner
        className="mb-0"
        title={books ? 'Danh mục sách tham khảo' : 'Tài liệu nghe nhìn'}
        subtitle="Nguồn tham khảo bên ngoài, mở ở tab mới."
      />

      <div className="grid min-w-0 gap-4 sm:grid-cols-2">
        <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
          <div className="flex min-w-0 items-start gap-4">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
              <BookOpen aria-hidden="true" className="size-6" />
            </div>
            <div className="min-w-0">
              <p className="text-h1 tabular-nums text-ink">{totalCount}</p>
              <p className="mt-0.5 text-label text-ink">Nguồn học tập hiện có</p>
              <p className="mt-0.5 text-body-sm text-ink-secondary">
                Tổng hợp kênh luyện nghe, hội thoại và sách tham khảo chọn lọc
              </p>
            </div>
          </div>
          <span className="shrink-0 rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
            Tuyển chọn
          </span>
        </Card>

        <Card className="flex min-w-0 items-start justify-between gap-4 border-brand-100 bg-brand-50 p-5">
          <div className="flex min-w-0 items-start gap-4">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
              <Layers aria-hidden="true" className="size-6" />
            </div>
            <div className="min-w-0">
              <p className="text-h1 tabular-nums text-ink">3 cấp độ</p>
              <p className="mt-0.5 text-label text-ink">Phân loại theo lộ trình Sơ cấp</p>
              <p className="mt-0.5 text-body-sm text-ink-secondary">
                Tra cứu nhanh theo từng bậc năng lực và từ khoá chủ đề
              </p>
            </div>
          </div>
          <span className="shrink-0 rounded-pill bg-success-bg px-3 py-1 text-caption font-semibold text-success">
            Chuẩn hoá
          </span>
        </Card>
      </div>

      <Card className="flex flex-wrap items-center justify-between gap-3 border-brand-100 bg-brand-50 p-4 sm:px-5">
        <div className="flex items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
            <Compass aria-hidden="true" className="size-5" />
          </div>
          <div>
            <p className="text-label text-ink">Chuyên mục tài liệu tham khảo</p>
            <p className="text-body-sm text-ink-secondary">
              Chuyển đổi giữa kênh nghe nhìn trực tuyến và danh mục sách in / giáo trình.
            </p>
          </div>
        </div>
        <nav aria-label="Loại tài liệu" className="flex flex-wrap gap-2 text-body-sm">
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
      </Card>

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
