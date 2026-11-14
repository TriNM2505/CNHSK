import { createElement } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'

import { LeaderboardView } from './components/LeaderboardView'
import type { Leaderboard } from './types'

const sample: Leaderboard = {
  type: 'TOPIC',
  ref: '43',
  period: 'week',
  periodStart: '2026-11-09T00:00:00+07:00',
  periodEnd: '2026-11-16T00:00:00+07:00',
  entries: Array.from({ length: 8 }, (_, index) => ({
    rank: index + 1,
    userId: index + 1,
    displayName: `Người học ${index + 1}`,
    avatarUrl: null,
    score: 100 - index * 3,
    durationMs: 60000 + index * 7000,
  })),
  myEntry: {
    rank: 128,
    userId: 1000,
    displayName: 'Tôi',
    avatarUrl: null,
    score: 72,
    durationMs: 90000,
  },
}
const networkError = new ApiError({
  status: 0,
  errorCode: 'NETWORK_ERROR',
  message: 'Không có kết nối mạng.',
})

function Preview({ state, game = false }: { state: PreviewState; game?: boolean }) {
  return createElement(LeaderboardView, {
    kind: game ? 'game' : 'topic',
    title: game ? 'Bảng xếp hạng trò chơi' : 'Bảng xếp hạng chủ đề',
    games: [
      { code: 'CHAR_RAIN', name: 'Mưa Chữ' },
      { code: 'FLYING_DART', name: 'Phi Đao' },
    ],
    topics: [{ id: 43, name: 'Chào hỏi', hskLevel: 1 }],
    selectedId: game ? 'CHAR_RAIN' : '43',
    hsk: 1,
    period: 'week',
    data:
      state === 'empty'
        ? { ...sample, entries: [], myEntry: null }
        : state === 'ready'
          ? sample
          : undefined,
    state,
    error: state === 'error' ? networkError : undefined,
    guest: false,
    onSelect: () => undefined,
    onHskChange: () => undefined,
    onPeriodChange: () => undefined,
    onRetry: () => undefined,
  })
}

export const previews = [
  {
    group: 'screen',
    id: 'leaderboard-topics',
    scr: 'SCR-046',
    title: 'Bảng xếp hạng chủ đề',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(Preview, { state }),
  },
  {
    group: 'screen',
    id: 'leaderboard-games',
    scr: 'SCR-047',
    title: 'Bảng xếp hạng trò chơi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(Preview, { state, game: true }),
  },
] satisfies readonly PreviewEntry[]
