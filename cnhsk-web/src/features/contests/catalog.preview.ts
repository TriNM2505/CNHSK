import { createElement, useEffect, useState } from 'react'
import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ContestListView } from './components/ContestListView'
import { ContestDetailView } from './components/ContestDetailView'
import { ContestAttemptView } from './components/ContestAttemptView'
import { ContestResultsView } from './components/ContestResultsView'
import {
  contestFixture,
  contestPageFixture,
  contestSessionFixture,
  publishedFixture,
  unpublishedFixture,
} from './test-fixtures'
import type { ScheduleState } from './types'

function ListPreview({ state }: { state: PreviewState }) {
  const [filter, setFilter] = useState<ScheduleState | ''>('')
  const result = {
    ...contestPageFixture,
    items:
      state === 'empty'
        ? []
        : contestPageFixture.items.filter((item) => !filter || item.state === filter),
  }
  return createElement(ContestListView, {
    state,
    result,
    filter,
    onFilter: setFilter,
    onPage: () => undefined,
    onRetry: () => undefined,
    error: 'Không tải được danh sách cuộc thi. Vui lòng thử lại.',
  })
}

function DetailPreview({ state }: { state: PreviewState }) {
  const [confirming, setConfirming] = useState(false)
  const [eligibility, setEligibility] = useState<'learner' | 'guest' | 'unverified'>('learner')
  return createElement(
    'div',
    null,
    createElement(
      'div',
      {
        className: 'flex flex-wrap gap-3 p-4 text-body-sm',
        role: 'group',
        'aria-label': 'Vai trò xem trước',
      },
      ...(['learner', 'guest', 'unverified'] as const).map((role) =>
        createElement(
          'button',
          {
            key: role,
            onClick: () => setEligibility(role),
            className: 'min-h-touch rounded-sm border border-line px-3',
          },
          role === 'learner' ? 'Người học' : role === 'guest' ? 'Khách' : 'Chưa xác thực email',
        ),
      ),
    ),
    createElement(ContestDetailView, {
      state: state === 'empty' ? 'error' : state,
      contest: state === 'empty' ? undefined : contestFixture,
      eligibility,
      now: Date.parse('2026-11-14T12:08:00Z'),
      confirming,
      onConfirmChange: setConfirming,
      onEnter: () => setConfirming(false),
      onRetry: () => undefined,
      error: state === 'empty' ? 'Cuộc thi không khả dụng.' : 'Không tải được cuộc thi.',
    }),
  )
}

function AttemptPreview({ state }: { state: PreviewState }) {
  const [choices, setChoices] = useState<Record<number, string>>({ 1: 'A' })
  const [remainingMs, setRemaining] = useState(11 * 60_000 + 42_000)
  const [submitted, setSubmitted] = useState(false)
  useEffect(() => {
    if (state !== 'ready' || submitted) return
    const timer = window.setInterval(() => setRemaining((value) => Math.max(0, value - 1000)), 1000)
    return () => window.clearInterval(timer)
  }, [state, submitted])
  return createElement(ContestAttemptView, {
    state,
    name: contestFixture.name,
    session: contestSessionFixture,
    remainingMs,
    choices,
    saveStates: Object.fromEntries(Object.keys(choices).map((key) => [key, 'saved' as const])),
    locked: submitted,
    submitting: false,
    submitted,
    error: 'Không mở được lượt thi. Vui lòng thử lại.',
    onChoose: (no, answer) => setChoices((current) => ({ ...current, [no]: answer })),
    onSelectQuestion: () => undefined,
    onSubmit: () => setSubmitted(true),
    onRetrySubmit: () => setSubmitted(true),
    onRetry: () => undefined,
  })
}

function ResultsPreview({ state, published = true }: { state: PreviewState; published?: boolean }) {
  const result = published ? publishedFixture : unpublishedFixture
  return createElement(ContestResultsView, {
    state,
    result:
      state === 'empty'
        ? {
            ...publishedFixture,
            mine: null,
            ranking: { ...publishedFixture.ranking!, items: [], totalElements: 0 },
          }
        : result,
    onRetry: () => undefined,
    error: 'Không tải được kết quả. Vui lòng thử lại.',
  })
}

export const previews = [
  {
    group: 'screen',
    id: 'contests-list',
    scr: 'SCR-048',
    title: 'Danh sách cuộc thi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(ListPreview, { state }),
  },
  {
    group: 'screen',
    id: 'contests-detail',
    scr: 'SCR-049',
    title: 'Chi tiết cuộc thi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(DetailPreview, { state }),
  },
  {
    group: 'screen',
    id: 'contests-attempt',
    scr: 'SCR-050',
    title: 'Làm bài cuộc thi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(AttemptPreview, { state }),
  },
  {
    group: 'screen',
    id: 'contests-results',
    scr: 'SCR-051',
    title: 'Kết quả cuộc thi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(ResultsPreview, { state }),
  },
  {
    group: 'screen',
    id: 'contests-results-unpublished',
    scr: 'SCR-051',
    title: 'Cuộc thi — chờ công bố',
    states: ['ready'],
    render: (state) => createElement(ResultsPreview, { state, published: false }),
  },
] satisfies readonly PreviewEntry[]
