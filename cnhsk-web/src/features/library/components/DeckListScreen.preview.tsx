import { useState } from 'react'

import type { PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'

import { emptyDeckDraft, validateDeckDraft, type TopicChoice } from '../flashcardForms'
import type { DeckSummary } from '../types'
import { DeckListView } from './DeckListView'

// Original fixtures. Creating a sample only changes this component's state.
const samples: DeckSummary[] = [
  {
    id: 801,
    name: 'Từ dùng mỗi ngày',
    description: 'Chào hỏi, thời gian và những câu quen thuộc để bắt đầu ngày mới.',
    cardCount: 24,
    dueCount: 6,
    createdAt: '2026-11-17T02:00:00Z',
  },
  {
    id: 802,
    name: 'Gia đình và bạn bè',
    description: 'Nhớ cách gọi người thân và giới thiệu những người bên cạnh mình.',
    cardCount: 18,
    dueCount: 3,
    createdAt: '2026-11-16T02:00:00Z',
  },
  {
    id: 803,
    name: 'Một chuyến đi nhỏ',
    description: 'Tự đặt câu về đường đi, phương tiện và nơi muốn đến.',
    cardCount: 16,
    dueCount: 0,
    createdAt: '2026-11-15T02:00:00Z',
  },
  {
    id: 804,
    name: '今天 · 明天',
    description: 'Những từ chỉ thời gian và câu mẫu mình muốn ôn lại.',
    cardCount: 12,
    dueCount: 4,
    createdAt: '2026-11-14T02:00:00Z',
  },
  {
    id: 805,
    name: 'Học qua món ăn',
    description: 'Từ mới khi gọi món, hỏi giá và nói về khẩu vị.',
    cardCount: 20,
    dueCount: 5,
    createdAt: '2026-11-13T02:00:00Z',
  },
  {
    id: 806,
    name: 'Câu tự đặt tuần này',
    description: 'Bộ trống dành cho những câu sẽ viết sau buổi học.',
    cardCount: 0,
    dueCount: 0,
    createdAt: '2026-11-12T02:00:00Z',
  },
]

const topics: TopicChoice[] = [
  { id: 91, name: 'Chào hỏi mỗi ngày', totalWords: 8 },
  { id: 92, name: 'Gia đình và bạn bè', totalWords: 12 },
]

interface DeckPreviewProps {
  state: PreviewState
  source?: 'empty' | 'topic'
  topicState?: PreviewState
  pending?: boolean
  limited?: boolean
}

function DeckSampleContent({
  state,
  source = 'empty',
  topicState = 'ready',
  pending = false,
  limited = false,
}: DeckPreviewProps) {
  const [decks, setDecks] = useState(state === 'empty' ? [] : samples)
  const [draft, setDraft] = useState({
    ...emptyDeckDraft(),
    source,
    name: pending || limited ? 'Bộ thẻ mới của tôi' : '',
  })
  const [page, setPage] = useState(0)
  const [validation, setValidation] = useState<string>()
  const [createError, setCreateError] = useState<ApiError>()
  const [notice, setNotice] = useState('')
  return (
    <>
      {notice ? (
        <p
          className="mx-4 mt-4 rounded-md bg-success-bg p-3 text-body-sm text-success sm:mx-6 lg:mx-8"
          role="status"
        >
          {notice}
        </p>
      ) : null}
      <DeckListView
        state={state === 'loading' || state === 'error' ? state : decks.length ? 'ready' : 'empty'}
        decks={decks}
        draft={draft}
        page={page}
        validation={validation}
        createError={createError}
        pending={pending}
        topics={topics}
        topicsState={topicState}
        error={
          new ApiError({
            status: 503,
            errorCode: 'UNAVAILABLE',
            message: 'Chưa tải được bộ thẻ. Vui lòng thử lại.',
          })
        }
        topicsError={
          new ApiError({
            status: 503,
            errorCode: 'UNAVAILABLE',
            message: 'Chưa tải được danh sách chủ đề.',
          })
        }
        onPageChange={setPage}
        onRetry={() => undefined}
        onTopicsRetry={() => undefined}
        onChange={(next) => {
          setDraft(next)
          setValidation(undefined)
          setCreateError(undefined)
        }}
        onCreate={() => {
          if (pending) return
          const error = validateDeckDraft(draft, topicState === 'ready' ? topics : undefined)
          setValidation(error)
          if (error) return
          if (limited) {
            setCreateError(
              new ApiError({
                status: 422,
                errorCode: 'LIBRARY_LIMIT_REACHED',
                message: 'Đã đạt giới hạn bộ thẻ.',
                details: { kind: 'DECKS', limit: 50 },
              }),
            )
            return
          }
          const count =
            draft.source === 'topic'
              ? topics.find(({ id }) => String(id) === draft.topicId)!.totalWords
              : 0
          setDecks((current) => [
            {
              id: Math.max(800, ...current.map(({ id }) => id)) + 1,
              name: draft.name.trim(),
              description: draft.description || null,
              cardCount: count,
              dueCount: count,
              createdAt: '2026-11-17T08:00:00Z',
            },
            ...current,
          ])
          setDraft(emptyDeckDraft())
          setPage(0)
          setNotice(
            'Đã tạo bộ thẻ mẫu. Thay đổi chỉ có trong bản xem trước, không ghi dữ liệu thật.',
          )
        }}
      />
    </>
  )
}

export function DeckListScreenPreview(props: DeckPreviewProps) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: 'authenticated',
    user: {
      id: 101,
      displayName: 'Bạn học CNHSK',
      email: '',
      emailVerified: true,
      hskLevel: 1,
      roles: ['LEARNER'],
    },
  }
  return (
    <AuthContext.Provider value={auth}>
      <LearnerLayout>
        <DeckSampleContent {...props} />
      </LearnerLayout>
    </AuthContext.Provider>
  )
}
