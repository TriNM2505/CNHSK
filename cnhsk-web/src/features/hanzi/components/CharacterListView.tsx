import { ArrowUpRight, Search, Timer } from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  HanziText,
  Input,
  LevelBadge,
  PageBanner,
  PageState,
  Pagination,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
  type HskLevel,
  type PageStateValue,
} from '@/shared/ui'

import type { CharacterSummary } from '../types'

export interface CharacterListViewProps {
  characters: CharacterSummary[]
  error?: { message: string; requestId?: string }
  hskLevel?: HskLevel
  onHskLevelChange: (level?: HskLevel) => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onSearch: (q: string) => void
  /** Trang hiển thị, đếm từ 1. */
  page: number
  q: string
  size: number
  state: PageStateValue
  total: number
}

export const CHARACTER_PAGE_SIZE = 20

export function CharacterListView({
  characters,
  error,
  hskLevel,
  onHskLevelChange,
  onPageChange,
  onRetry,
  onSearch,
  page,
  q,
  size,
  state,
  total,
}: CharacterListViewProps) {
  const [draft, setDraft] = useState(q)
  useEffect(() => setDraft(q), [q])

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSearch(draft.trim())
  }

  return (
    <div className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 sm:px-6 lg:px-8">
      <PageBanner
        actions={
          <Button to="/learn/characters/writing/challenge" variant="outline">
            <span className="flex items-center gap-2">
              <Timer aria-hidden="true" className="size-5 shrink-0" />
              Thử thách viết
            </span>
          </Button>
        }
        subtitle="Chọn một chữ để luyện viết theo nét, nhớ rồi viết hoặc nghe chép."
        title="Luyện viết chữ Hán"
      />

      <Card className="border-brand-100 p-4 shadow-sm sm:p-5">
        <form
          className="grid gap-4 sm:grid-cols-2 sm:items-end lg:grid-cols-3"
          onSubmit={submit}
          role="search"
        >
          <FormField className="min-w-0 flex-1" htmlFor="character-search" label="Tìm chữ">
            <Input
              id="character-search"
              maxLength={20}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="Chữ Hán hoặc pinyin, vd 好 hoặc hao"
              value={draft}
            />
          </FormField>
          <FormField htmlFor="character-hsk-level" label="Cấp HSK">
            <Select
              id="character-hsk-level"
              onChange={(event) =>
                onHskLevelChange(
                  event.target.value ? (Number(event.target.value) as HskLevel) : undefined,
                )
              }
              value={hskLevel ? String(hskLevel) : ''}
            >
              <option value="">Tất cả</option>
              {SUPPORTED_HSK_LEVELS.map((level) => (
                <option key={level} value={String(level)}>
                  HSK {level}
                </option>
              ))}
            </Select>
          </FormField>
          <Button type="submit">
            <span className="flex items-center gap-2">
              <Search aria-hidden="true" className="size-5 shrink-0" />
              Tìm
            </span>
          </Button>
        </form>
      </Card>

      <PageState
        empty={
          <EmptyState
            description={
              q || hskLevel
                ? 'Không có chữ khớp bộ lọc. Hãy thử từ khoá khác.'
                : 'Chưa có chữ nào để luyện.'
            }
            title="Không tìm thấy chữ"
          />
        }
        error={
          <ErrorState
            message={error?.message ?? 'Vui lòng thử lại sau.'}
            onRetry={onRetry}
            requestId={error?.requestId}
            title="Không thể tải danh sách chữ"
          />
        }
        loading={
          <div
            aria-label="Đang tải danh sách chữ"
            className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
          >
            <Skeleton variant="card" />
            <Skeleton variant="card" />
            <Skeleton variant="card" />
          </div>
        }
        state={state}
      >
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {characters.map((character) => (
            <li className="min-w-0" key={character.id}>
              <Link
                className="group flex h-full min-h-touch min-w-0 items-center gap-4 rounded-md border border-line bg-surface p-4 shadow-sm transition-colors hover:border-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 sm:p-5"
                to={`/learn/characters/writing/${character.id}`}
              >
                <span className="flex size-20 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-brand-50">
                  <HanziText className="text-brand-700" size="lg">
                    {character.text}
                  </HanziText>
                </span>
                <span className="min-w-0 flex-1 space-y-1">
                  <span className="block text-body font-semibold text-ink">
                    {character.pinyin ?? '—'}
                  </span>
                  <span className="block break-words text-body-sm text-ink-secondary">
                    {character.meaning}
                  </span>
                  <span className="flex flex-wrap items-center gap-2 pt-1 text-caption text-ink-secondary">
                    {character.hskLevel ? <LevelBadge exact level={character.hskLevel} /> : null}
                    {character.strokeCount ? `${character.strokeCount} nét` : 'Chưa có dữ liệu nét'}
                  </span>
                </span>
                <ArrowUpRight aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
              </Link>
            </li>
          ))}
        </ul>
        <div className="mt-6">
          <Pagination onChange={onPageChange} page={page} size={size} total={total} />
        </div>
      </PageState>
    </div>
  )
}
