import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { getGameLeaderboard, listLeaderboardGames } from '../api/leaderboards'
import { LeaderboardView } from '../components/LeaderboardView'
import { firstGame, parsePeriod } from '../query'
import type { Leaderboard } from '../types'

export default function Scr047GameLeaderboardPage() {
  const [params, setParams] = useSearchParams()
  const { user } = useCurrentUser()
  const period = parsePeriod(params.get('period'))
  const gameCode = params.get('game') ?? ''
  const gamesQuery = useApiQuery((signal) => listLeaderboardGames(signal), [])
  const boardQuery = useApiQuery(
    (signal) =>
      gameCode
        ? getGameLeaderboard(gameCode, period, signal)
        : Promise.resolve(undefined as unknown as Leaderboard),
    [gameCode, period, user?.id],
  )

  useEffect(() => {
    if (gamesQuery.state !== 'ready' || gameCode) return
    const first = firstGame(gamesQuery.data ?? [])
    if (first) {
      const next = new URLSearchParams(params)
      next.set('game', first.code)
      if (!next.has('period')) next.set('period', period)
      setParams(next, { replace: true })
    }
  }, [gameCode, gamesQuery.data, gamesQuery.state, params, period, setParams])

  function updateParams(values: { game?: string; period?: string }) {
    const next = new URLSearchParams(params)
    if (values.game !== undefined) next.set('game', values.game)
    if (values.period) next.set('period', values.period)
    setParams(next)
  }

  const state =
    gamesQuery.state === 'loading' && !gamesQuery.data
      ? 'loading'
      : gamesQuery.state === 'error'
        ? 'error'
        : !gamesQuery.data?.length
          ? 'empty'
          : !gameCode
            ? 'loading'
            : boardQuery.data === undefined && boardQuery.error === undefined
              ? 'loading'
              : boardQuery.state

  return (
    <LeaderboardView
      data={boardQuery.data}
      error={gamesQuery.state === 'error' ? gamesQuery.error : boardQuery.error}
      games={gamesQuery.data}
      guest={!user}
      kind="game"
      onPeriodChange={(value) => updateParams({ period: value })}
      onRetry={() => (gamesQuery.state === 'error' ? gamesQuery.reload() : boardQuery.reload())}
      onSelect={(value) => updateParams({ game: value })}
      period={period}
      selectedId={gameCode}
      state={state}
      title="Bảng xếp hạng trò chơi"
    />
  )
}
