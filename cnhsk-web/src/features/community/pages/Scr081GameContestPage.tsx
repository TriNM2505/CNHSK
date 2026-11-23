import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'

import { listLeaderboardGames } from '@/features/leaderboard/api/leaderboards'
import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { Button } from '@/shared/ui'

import {
  contestPlayUrl,
  getGameContestLive,
  type GameContestLive,
  type GameContestPhase,
} from '../api/game-contests'
import { GameContestStage } from '../components/GameContestStage'

/** Refresh cadence per phase: the live ranking moves fast, the waiting room and the results do not. */
const POLL_MS: Record<GameContestPhase, number | null> = {
  WAITING: 30_000,
  OPEN: 5_000,
  TALLYING: 3_000,
  FINISHED: null,
}

/** The phase the server clock is in right now, so the page flips on time between refreshes. */
function phaseAt(contest: GameContestLive, now: number): GameContestPhase {
  if (now < new Date(contest.startsAt).getTime()) return 'WAITING'
  if (now < new Date(contest.endsAt).getTime()) return 'OPEN'
  return now < new Date(contest.resultsAt).getTime() ? 'TALLYING' : 'FINISHED'
}

export default function Scr081GameContestPage() {
  const { id } = useParams()
  const contestId = Number(id)
  const auth = useCurrentUser()
  const user = auth.user
  const [contest, setContest] = useState<GameContestLive>()
  const [failed, setFailed] = useState(false)
  const [refresh, setRefresh] = useState(0)
  /** Server time minus device time, measured on every response. */
  const offset = useRef(0)
  const [now, setNow] = useState(() => Date.now())
  const games = useApiQuery((signal) => listLeaderboardGames(signal), [])

  useEffect(() => {
    if (!Number.isInteger(contestId) || contestId < 1) {
      setFailed(true)
      return
    }
    const controller = new AbortController()
    getGameContestLive(contestId, controller.signal)
      .then((live) => {
        offset.current = new Date(live.serverTime).getTime() - Date.now()
        setContest(live)
        setFailed(false)
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true)
      })
    return () => controller.abort()
  }, [contestId, refresh])

  // One clock tick a second keeps the countdowns moving and notices when a phase boundary passes.
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now() + offset.current), 1000)
    return () => window.clearInterval(timer)
  }, [])

  const phase = contest ? phaseAt(contest, now) : undefined
  // Poll at the phase's cadence, and refetch at once when the clock crosses into a new phase.
  useEffect(() => {
    if (!contest || !phase) return
    // A short wait on a boundary lets the server's clock catch up instead of hammering it.
    const every = phase !== contest.phase ? 1500 : POLL_MS[phase]
    if (every === null) return
    const timer = window.setTimeout(() => setRefresh((value) => value + 1), every)
    return () => window.clearTimeout(timer)
  }, [contest, phase])

  if (failed && !contest) {
    return (
      <main className="mx-auto max-w-reading space-y-3 px-4 py-10 text-center">
        <p className="text-body text-ink" role="alert">Không tải được cuộc thi.</p>
        <Button onClick={() => setRefresh((value) => value + 1)} variant="outline">Thử lại</Button>
      </main>
    )
  }
  if (!contest) {
    return <p className="px-4 py-10 text-center text-body-sm text-ink-secondary" role="status">Đang tải cuộc thi…</p>
  }

  const gameName = games.data?.find((game) => game.code === contest.gameCode)?.name ?? contest.gameCode
  const gameOrigin = import.meta.env.VITE_GAME_ORIGIN
  const learner = Boolean(user?.roles.includes('LEARNER'))
  const play = !user
    ? { external: false, href: `/login?redirect=${encodeURIComponent(`/community/contests/${contest.id}`)}`, label: 'Đăng nhập để vào thi' }
    : learner && gameOrigin
      ? { external: true, href: contestPlayUrl(gameOrigin, contest.gameCode, contest.id), label: 'Vào thi' }
      : undefined

  return (
    <GameContestStage
      // The winners appear only once the server has the final ranking.
      contest={{ ...contest, phase: phase === 'FINISHED' && contest.phase !== 'FINISHED' ? 'TALLYING' : phase ?? contest.phase }}
      gameName={gameName}
      now={now}
      play={play}
      resultsHref={user?.roles.includes('MODERATOR') ? `/moderation/contests/${contest.id}/results` : undefined}
      viewerId={user?.id}
    />
  )
}
