import { ArrowLeft, BookOpenText } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import { Button, EmptyState, ErrorState, HanziText, PageBanner, Skeleton } from '@/shared/ui'
import './LibraryView.css'
import type { PageStateValue } from '@/shared/ui'

import { dictionaryDetailHref } from '../dictionarySearch'
import type { LexemeDetail } from '../types'
import { LexemeEntryBody } from './LexemeEntryBody'

const kindLabels = { CHAR: 'Chữ Hán', WORD: 'Từ vựng', GRAMMAR: 'Ngữ pháp' }

export interface LexemeDetailViewProps {
  entry?: LexemeDetail
  state: PageStateValue
  error?: ApiError
  backTo: string
  onRetry: () => void
  saveActions?: ReactNode
}

export function LexemeDetailView({
  entry,
  state,
  error,
  backTo,
  onRetry,
  saveActions,
}: LexemeDetailViewProps) {
  return (
    <div className="library-screen mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <PageBanner
        title={
          state === 'ready' && entry ? (
            <HanziText size="lg">{entry.text}</HanziText>
          ) : (
            'Chi tiết mục từ'
          )
        }
        subtitle={
          state === 'ready' && entry
            ? kindLabels[entry.kind]
            : 'Khám phá nghĩa, cách đọc và cách dùng trong ngữ cảnh.'
        }
      />
      <Button size="sm" to={backTo} variant="ghost">
        <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
        Về kết quả tra cứu
      </Button>
      {state === 'loading' ? (
        <div aria-label="Đang tải mục từ" className="grid min-w-0 items-start gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <Skeleton className="h-32" />
            <Skeleton className="h-24" />
            <Skeleton className="h-48" />
          </div>
          <Skeleton className="h-80" />
        </div>
      ) : null}
      {state === 'error' ? (
        <ErrorState
          title={error?.status === 404 ? 'Không tìm thấy mục từ' : 'Không thể tải mục từ'}
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          requestId={error?.requestId}
          onRetry={error?.status === 404 ? undefined : onRetry}
        />
      ) : null}
      {state === 'empty' || (state === 'ready' && !entry) ? (
        <EmptyState
          title="Không tìm thấy mục từ"
          description="Quay lại kết quả tra cứu để chọn một mục từ khác."
          icon={<BookOpenText aria-hidden="true" className="size-5" />}
        />
      ) : null}
      {state === 'ready' && entry ? (
        <LexemeEntryBody
          entry={entry}
          related={{ href: (id) => dictionaryDetailHref(id, backTo) }}
          saveActions={saveActions}
        />
      ) : null}
    </div>
  )
}