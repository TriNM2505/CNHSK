import { useEffect, useRef, useState } from 'react'
import type { ComponentType } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../api/client'
import { abandonRound, answerRound, finishRound, readRound, startRound } from '../api/rounds'
import type { RoundResult, RoundStart, RoundView, ServedItem } from '../api/rounds'

export type GameProps = {
  items: readonly ServedItem[]
  uiConfig: Readonly<Record<string, unknown>>
  submitAnswer: (no: number, answer: string) => void
}

type Props = { code: string; hskLevel?: number; Game: ComponentType<GameProps> }

type Screen =
  | {
      mode: 'ready' | 'starting' | 'finishing' | 'leaving' | 'finished' | 'expired' | 'abandoned'
      message?: string
    }
  | {
      mode: 'round'
      round: RoundStart
      countdown: number
      seconds: number
      points: number
      pending: number
      confirming: boolean
      blocked: boolean
      message?: string
    }
  | { mode: 'result'; result: RoundResult }
  | { mode: 'finish-error' | 'leave-error'; message: string }

type Pending = { no: number; answer: string; durationMs: number; uncertain: boolean }
const control =
  'inline-flex min-h-touch items-center justify-center rounded-sm border border-line bg-surface px-4 font-semibold'
const primary =
  'inline-flex min-h-touch items-center justify-center rounded-sm bg-brand-800 px-4 font-semibold text-surface'
const hubLink = (
  <Link to="/" className={control}>
    Về danh sách trò chơi
  </Link>
)

function hasCode(error: unknown, code: string) {
  return error instanceof ApiError && error.errorCode === code
}

function createRoundController(
  code: string,
  hskLevel: number | undefined,
  publish: (screen: Screen) => void,
) {
  const abort = new AbortController()
  const { signal } = abort
  let mode: Screen['mode'] = 'ready'
  let round: RoundStart | undefined
  let offset = 0
  let lastAnswerAt = 0
  let points = 0
  let queue: Pending[] = []
  const accepted = new Set<number>()
  const answered = new Set<number>()
  let busy = false
  let blocked = false
  let closing = false
  let leaveRequested = false
  let confirming = false
  let message: string | undefined

  function setScreen(screen: Screen) {
    if (signal.aborted) return
    mode = screen.mode
    publish(screen)
  }

  const serverNow = () => Date.now() + offset
  const countdownUntil = () => Date.parse(round?.startedAt ?? '') + 3000
  const deadline = () => Date.parse(round?.deadline ?? '')

  function syncClock(next: RoundStart) {
    offset = Date.parse(next.serverTime) - Date.now()
  }

  function renderRound() {
    if (mode !== 'round' || !round) return
    const now = serverNow()
    setScreen({
      mode: 'round',
      round,
      countdown: Math.max(0, Math.ceil((countdownUntil() - now) / 1000)),
      seconds: Math.max(0, Math.ceil((deadline() - now) / 1000)),
      points,
      pending: queue.length,
      confirming,
      blocked,
      message,
    })
  }

  async function start() {
    if (mode !== 'ready' && mode !== 'result') return
    setScreen({ mode: 'starting' })
    try {
      const next = await startRound(code, hskLevel, signal)
      if (signal.aborted) return
      round = next
      syncClock(next)
      lastAnswerAt = countdownUntil()
      points = 0
      queue = []
      accepted.clear()
      answered.clear()
      busy = blocked = closing = leaveRequested = confirming = false
      message = undefined
      mode = 'round'
      tick()
    } catch (error) {
      if (signal.aborted) return
      setScreen({
        mode: 'ready',
        message: error instanceof ApiError ? error.message : 'Chưa bắt đầu được ván.',
      })
    }
  }

  function applyRoundStatus(view: RoundView): boolean {
    if (view.status === 'EXPIRED') setScreen({ mode: 'expired' })
    if (view.status === 'SUBMITTED') setScreen({ mode: 'finished' })
    if (view.status === 'ABANDONED') setScreen({ mode: 'abandoned' })
    return view.status === 'IN_PROGRESS'
  }

  async function finish() {
    if (!round || (mode !== 'round' && mode !== 'finish-error')) return
    setScreen({ mode: 'finishing' })
    try {
      const result = await finishRound(round.roundId, signal)
      setScreen({ mode: 'result', result })
    } catch (error) {
      if (hasCode(error, 'ROUND_ALREADY_SUBMITTED')) setScreen({ mode: 'finished' })
      else if (hasCode(error, 'ROUND_EXPIRED')) setScreen({ mode: 'expired' })
      else setScreen({ mode: 'finish-error', message: 'Chưa nhận được kết quả ván.' })
    }
  }

  async function retryFinish() {
    if (!round || mode !== 'finish-error') return
    setScreen({ mode: 'finishing' })
    try {
      const view = await readRound(round.roundId, signal)
      if (!signal.aborted && applyRoundStatus(view)) {
        mode = 'finish-error'
        await finish()
      }
    } catch {
      setScreen({ mode: 'finish-error', message: 'Chưa kiểm tra được trạng thái ván.' })
    }
  }

  async function abandon() {
    if (!round || (mode !== 'round' && mode !== 'leave-error')) return
    setScreen({ mode: 'leaving' })
    try {
      await abandonRound(round.roundId, signal)
      setScreen({ mode: 'abandoned' })
    } catch (error) {
      if (hasCode(error, 'ROUND_EXPIRED')) setScreen({ mode: 'expired' })
      else if (hasCode(error, 'ROUND_ALREADY_SUBMITTED')) setScreen({ mode: 'finished' })
      else setScreen({ mode: 'leave-error', message: 'Chưa rời được ván.' })
    }
  }

  async function drain() {
    if (busy || !round || mode !== 'round' || signal.aborted) return
    if (leaveRequested) {
      await abandon()
      return
    }
    if (blocked) return
    busy = true
    try {
      while (mode === 'round' && !leaveRequested && !signal.aborted) {
        const next = queue[0]
        if (!next) break
        try {
          if (next.uncertain) {
            const view = await readRound(round.roundId, signal)
            if (signal.aborted || !applyRoundStatus(view)) return
            round = view
            syncClock(view)
            points = view.totalPoints
            for (const item of view.answered) {
              accepted.add(item.no)
              answered.add(item.no)
            }
          }
          if (!answered.has(next.no)) {
            const response = await answerRound(
              round.roundId,
              {
                no: next.no,
                answer: next.answer,
                durationMs: next.durationMs,
              },
              signal,
            )
            if (signal.aborted) return
            answered.add(next.no)
            points = response.totalPoints
          }
          queue.shift()
        } catch (error) {
          if (signal.aborted) return
          if (hasCode(error, 'ROUND_EXPIRED')) {
            setScreen({ mode: 'expired' })
            return
          }
          if (hasCode(error, 'ROUND_ALREADY_SUBMITTED')) {
            setScreen({ mode: 'finished' })
            return
          }
          if (hasCode(error, 'ROUND_TIME_OVER')) {
            closing = true
            queue = []
            break
          }
          next.uncertain = true
          blocked = true
          message = 'Chưa gửi được đáp án. Các đáp án đang chờ sẽ được kiểm tra khi kết nối lại.'
          break
        }
        renderRound()
      }
    } finally {
      busy = false
      renderRound()
    }
    if (signal.aborted || mode !== 'round') return
    if (leaveRequested) await abandon()
    else if (!blocked && (closing || answered.size >= round.items.length)) await finish()
  }

  function submitAnswer(no: number, answer: string) {
    if (!round || mode !== 'round' || closing || confirming || leaveRequested || accepted.has(no))
      return
    const now = serverNow()
    if (now < countdownUntil() || !round.items.some((item) => item.no === no)) return
    if (now >= deadline()) {
      tick()
      return
    }
    accepted.add(no)
    queue.push({
      no,
      answer,
      durationMs: Math.max(0, Math.min(600000, now - lastAnswerAt)),
      uncertain: false,
    })
    lastAnswerAt = now
    renderRound()
    void drain()
  }

  function tick() {
    if (mode !== 'round') return
    if (!closing && serverNow() >= deadline()) {
      closing = true
      confirming = false
      void drain()
    }
    renderRound()
  }

  function retryAnswers() {
    if (mode !== 'round' || !blocked) return
    blocked = false
    message = undefined
    renderRound()
    void drain()
  }

  function confirmExit(confirmed: boolean) {
    if (mode !== 'round' || closing) return
    if (confirmed) {
      leaveRequested = true
      confirming = false
      void drain()
    } else {
      confirming = !confirming
    }
    renderRound()
  }

  return {
    start,
    tick,
    submitAnswer,
    retryAnswers,
    confirmExit,
    retryFinish,
    abandon,
    dispose: () => abort.abort(),
  }
}

export function GameShell(props: Props) {
  return <RoundScreen key={JSON.stringify([props.code, props.hskLevel])} {...props} />
}

function RoundScreen({ code, hskLevel, Game }: Props) {
  const [screen, setScreen] = useState<Screen>({ mode: 'ready' })
  const controller = useRef<ReturnType<typeof createRoundController>>()
  useEffect(() => {
    const instance = createRoundController(code, hskLevel, setScreen)
    controller.current = instance
    const timer = window.setInterval(instance.tick, 200)
    window.addEventListener('online', instance.retryAnswers)
    return () => {
      controller.current = undefined
      instance.dispose()
      window.clearInterval(timer)
      window.removeEventListener('online', instance.retryAnswers)
    }
  }, [code, hskLevel])

  const result = screen.mode === 'result' ? screen.result : null
  return (
    <section
      aria-label="Ván chơi"
      className="mx-auto max-w-reading space-y-5 rounded-md border border-line bg-surface p-4 sm:p-6"
    >
      {screen.mode === 'ready' && (
        <>
          <h1 className="text-h2">Sẵn sàng?</h1>
          {screen.message && <p role="alert">{screen.message}</p>}
          <button
            type="button"
            className={primary}
            onClick={() => void controller.current?.start()}
          >
            Bắt đầu
          </button>
        </>
      )}
      {screen.mode === 'starting' && <p role="status">Đang tạo ván…</p>}
      {screen.mode === 'round' && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p>
              Thời gian còn lại: <span className="tabular-nums">{screen.seconds} giây</span>
            </p>
            <p>
              Điểm: <strong>{screen.points}</strong>
            </p>
            {screen.seconds > 0 && (
              <button
                type="button"
                className={control}
                onClick={() => controller.current?.confirmExit(false)}
              >
                Thoát
              </button>
            )}
          </div>
          {screen.confirming && (
            <div
              role="group"
              aria-label="Xác nhận thoát"
              className="flex flex-wrap gap-3 rounded-sm border border-line p-3"
            >
              <p className="w-full">Bạn có muốn bỏ ván này?</p>
              <button
                type="button"
                className={control}
                onClick={() => controller.current?.confirmExit(false)}
              >
                Chơi tiếp
              </button>
              <button
                type="button"
                className={control}
                onClick={() => controller.current?.confirmExit(true)}
              >
                Bỏ ván
              </button>
            </div>
          )}
          {screen.blocked && (
            <div>
              <p role="status">
                {screen.message} ({screen.pending} đang chờ)
              </p>
              <button
                type="button"
                className={control}
                onClick={() => controller.current?.retryAnswers()}
              >
                Thử gửi lại
              </button>
            </div>
          )}
          {screen.countdown > 0 ? (
            <p role="status" className="text-h2">
              Bắt đầu sau {screen.countdown}
            </p>
          ) : (
            screen.seconds > 0 && (
              <Game
                items={screen.round.items}
                uiConfig={screen.round.uiConfig}
                submitAnswer={(no, answer) => controller.current?.submitAnswer(no, answer)}
              />
            )
          )}
        </>
      )}
      {screen.mode === 'finishing' && <p role="status">Đang kết thúc ván…</p>}
      {screen.mode === 'leaving' && <p role="status">Đang rời ván…</p>}
      {screen.mode === 'finish-error' && (
        <div className="space-y-3">
          <p role="alert">{screen.message}</p>
          <button
            type="button"
            className={control}
            onClick={() => void controller.current?.retryFinish()}
          >
            Kiểm tra lại
          </button>
        </div>
      )}
      {screen.mode === 'leave-error' && (
        <div className="space-y-3">
          <p role="alert">{screen.message}</p>
          <button
            type="button"
            className={control}
            onClick={() => void controller.current?.abandon()}
          >
            Thử lại
          </button>
        </div>
      )}
      {(screen.mode === 'finished' || screen.mode === 'expired' || screen.mode === 'abandoned') && (
        <>
          <h1 className="text-h2">
            {screen.mode === 'finished'
              ? 'Ván đã kết thúc'
              : screen.mode === 'expired'
                ? 'Ván đã hết hạn'
                : 'Đã bỏ ván'}
          </h1>
          {hubLink}
        </>
      )}
      {result && (
        <>
          <h1 className="text-h2">Kết quả ván chơi</h1>
          {result.newBest && (
            <p role="status" className="font-bold text-brand-800">
              Kỷ lục mới!
            </p>
          )}
          <dl className="grid grid-cols-2 gap-3 break-words">
            <div>
              <dt>Điểm</dt>
              <dd>
                {result.points} / {result.maxPoints}
              </dd>
            </div>
            <div>
              <dt>Đúng</dt>
              <dd>{result.correct}</dd>
            </div>
            <div>
              <dt>Sai</dt>
              <dd>{result.wrong}</dd>
            </div>
            <div>
              <dt>Bỏ lỡ</dt>
              <dd>{result.missed}</dd>
            </div>
            <div>
              <dt>Độ chính xác</dt>
              <dd>{result.accuracy === null ? '—' : result.accuracy + '%'}</dd>
            </div>
            <div>
              <dt>Thời gian</dt>
              <dd>{Math.ceil(result.durationMs / 1000)} giây</dd>
            </div>
          </dl>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              className={primary}
              onClick={() => void controller.current?.start()}
            >
              Chơi lại
            </button>
            {hubLink}
          </div>
        </>
      )}
    </section>
  )
}
