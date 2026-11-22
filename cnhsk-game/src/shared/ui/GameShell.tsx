import { useEffect, useRef, useState } from 'react'
import type { ComponentType, ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../api/client'
import { abandonRound, answerRound, finishRound, readRound, startRound } from '../api/rounds'
import type { RoundResult, RoundStart, RoundView, ServedItem } from '../api/rounds'
import { GameArtwork } from './arcade'
import { gameMeta, webHref } from './gameMeta'

export type GameAnswer = { correct: boolean; correctAnswer: string }

export type GameProps = {
  items: readonly ServedItem[]
  uiConfig: Readonly<Record<string, unknown>>
  submitAnswer: (no: number, answer: string) => Promise<GameAnswer>
  onMissed: (no: number) => void
  disabled: boolean
  onItemShown?: (no: number) => void
  /** Ends the round now and keeps the score so far (the ink jar ran dry, every board is done…). */
  endRound?: () => void
}

/** Levels with game content; the server rejects any other level. */
export const HSK_LEVELS = [1, 2, 3] as const

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
      disabled: boolean
      message?: string
      /** Lives left; undefined when the game has no lives. */
      lives?: number
    }
  | { mode: 'result'; result: RoundResult; outOfLives: boolean }
  | { mode: 'finish-error' | 'leave-error'; message: string }

type Pending = { no: number; answer: string; durationMs: number; uncertain: boolean; resolve: (value: GameAnswer) => void; reject: (error: Error) => void }
const feedbackMs = 800

function feedbackPromise(executor: ConstructorParameters<typeof Promise<GameAnswer>>[0]) {
  const promise = new Promise<GameAnswer>(executor)
  // Legacy games can ignore the feedback promise.
  void promise.catch(() => {})
  return promise
}
const hubLink = (
  <Link to="/" className="secondary-button">
    Về danh sách trò chơi
  </Link>
)

function hasCode(error: unknown, code: string) {
  return error instanceof ApiError && error.errorCode === code
}

function createRoundController(
  code: string,
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
  const answered = new Map<number, { correct: boolean | null; correctAnswer: string | null }>()
  const missed = new Set<number>()
  const shown = new Map<number, number>()
  let finishTimer: number | undefined
  let busy = false
  let blocked = false
  let closing = false
  let leaveRequested = false
  let confirming = false
  let message: string | undefined
  let lives: number | undefined

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
      disabled: closing || confirming || blocked || leaveRequested || finishTimer !== undefined,
      message,
      lives,
    })
  }

  async function start(hskLevel: number) {
    if (mode !== 'ready' && mode !== 'result') return
    setScreen({ mode: 'starting' })
    try {
      const next = await startRound(code, hskLevel, signal)
      if (signal.aborted) return
      round = next
      syncClock(next)
      lastAnswerAt = countdownUntil()
      points = 0
      lives = typeof next.uiConfig.maxLives === 'number' ? next.uiConfig.maxLives : undefined
      queue = []
      accepted.clear()
      answered.clear()
      missed.clear()
      shown.clear()
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

  function stopPending() {
    window.clearTimeout(finishTimer)
    finishTimer = undefined
    for (const item of queue) item.reject(new Error('Ván không còn nhận câu trả lời.'))
    queue = []
  }

  async function finish() {
    if (!round || (mode !== 'round' && mode !== 'finish-error')) return
    stopPending()
    setScreen({ mode: 'finishing' })
    try {
      const result = await finishRound(round.roundId, signal)
      setScreen({ mode: 'result', result, outOfLives: lives === 0 })
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
    stopPending()
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
            if (signal.aborted) return
            if (!applyRoundStatus(view)) {
              stopPending()
              return
            }
            round = view
            syncClock(view)
            points = view.totalPoints
            for (const item of view.answered) {
              accepted.add(item.no)
              answered.set(item.no, item)
              missed.delete(item.no)
            }
            if (lives !== undefined) {
              // The lost response carried the lives left; the recorded answers say the same.
              const max = typeof view.uiConfig.maxLives === 'number' ? view.uiConfig.maxLives : lives
              lives = Math.max(0, max - view.answered.filter((item) => item.correct === false).length)
              if (lives === 0) closing = true
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
            answered.set(next.no, response)
            points = response.totalPoints
            if (typeof response.livesLeft === 'number') {
              lives = response.livesLeft
              // The server refuses answers after the last life, so the round ends here.
              if (lives === 0) closing = true
            }
          }
          const feedback = answered.get(next.no)
          if (feedback?.correct != null && feedback.correctAnswer != null) {
            next.resolve({ correct: feedback.correct, correctAnswer: feedback.correctAnswer })
          } else {
            next.reject(new Error('Không có phản hồi chấm của câu này.'))
          }
          queue.shift()
        } catch (error) {
          if (signal.aborted) return
          if (hasCode(error, 'ROUND_EXPIRED')) {
            stopPending()
            setScreen({ mode: 'expired' })
            return
          }
          if (hasCode(error, 'ROUND_ALREADY_SUBMITTED')) {
            stopPending()
            setScreen({ mode: 'finished' })
            return
          }
          if (hasCode(error, 'ROUND_TIME_OVER') || hasCode(error, 'RATE_LIMITED')) {
            // Retrying cannot succeed; finish with what the server has recorded.
            closing = true
            stopPending()
            break
          }
          next.uncertain = true
          blocked = true
          message = 'Mất kết nối, đang thử lại. Các đáp án đang chờ sẽ được kiểm tra khi kết nối lại.'
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
    // The answer that cost the last life still shows its feedback before the result.
    else if (!blocked && closing && lives !== 0) await finish()
    else if (!blocked && finishTimer === undefined
      && (lives === 0 || answered.size + missed.size >= round.items.length)) {
      const delay = Math.max(0, Math.min(feedbackMs, deadline() - serverNow()))
      finishTimer = window.setTimeout(() => void finish(), delay)
      renderRound()
    }
  }

  function canProcess(no: number, now: number) {
    return round && mode === 'round' && !closing && !confirming && !leaveRequested
      && !accepted.has(no) && now >= countdownUntil() && now < deadline()
      && round.items.some((item) => item.no === no)
  }

  function onItemShown(no: number) {
    const now = serverNow()
    if (canProcess(no, now) && !shown.has(no)) shown.set(no, now)
  }

  function onMissed(no: number) {
    const now = serverNow()
    if (!canProcess(no, now)) return
    accepted.add(no)
    missed.add(no)
    shown.delete(no)
    lastAnswerAt = now
    void drain()
  }

  function submitAnswer(no: number, answer: string) {
    const now = serverNow()
    if (!canProcess(no, now)) {
      if (mode === 'round' && now >= deadline()) tick()
      return feedbackPromise((_resolve, reject) => reject(new Error('Ván không nhận câu trả lời này.')))
    }
    accepted.add(no)
    const durationMs = Math.max(0, Math.min(600000, now - (shown.get(no) ?? lastAnswerAt)))
    shown.delete(no)
    lastAnswerAt = now
    const feedback = feedbackPromise((resolve, reject) => {
      queue.push({ no, answer, durationMs, uncertain: false, resolve, reject })
    })
    renderRound()
    void drain()
    return feedback
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

  /** A game ends the round itself (out of ink, every board done): pending answers are sent, then it finishes. */
  function endRound() {
    if (mode !== 'round' || closing || leaveRequested) return
    closing = true
    confirming = false
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
    onMissed,
    onItemShown,
    retryAnswers,
    endRound,
    confirmExit,
    retryFinish,
    abandon,
    dispose: () => {
      abort.abort()
      stopPending()
    },
  }
}

export function GameShell(props: Props) {
  return <RoundScreen key={JSON.stringify([props.code, props.hskLevel])} {...props} />
}

function livesMax(screen: Extract<Screen, { mode: 'round' }>): number {
  const max = screen.round.uiConfig.maxLives
  return typeof max === 'number' ? max : 0
}

/** The arcade page frame shared by every game: back link, title and the round panel. */
export function ArcadePage({ code, playing, children }: { code: string; playing?: boolean; children: ReactNode }) {
  const meta = gameMeta(code)
  return (
    <div className={`core-page core-page-${meta.slug} ${playing ? 'core-page-playing' : ''}`}>
      <div className="core-header">
        <Link to="/" className="text-button">← CNHSK Arcade</Link>
        <span className="demo-pill">{meta.eyebrow}</span>
      </div>
      <div className="core-title">
        <span className="eyebrow">GAME BOX / {meta.name.toUpperCase()}</span>
        <h1>{meta.name}</h1>
        <p>{meta.detail}</p>
      </div>
      {children}
    </div>
  )
}

const LEVEL_KEY = 'cnhsk-game:hsk-level'

function isGameLevel(value: unknown): value is (typeof HSK_LEVELS)[number] {
  return HSK_LEVELS.some((level) => level === value)
}

/** The level asked for in the link, else the one the learner picked last time on this device. */
function initialLevel(requested: number | undefined): number | undefined {
  if (isGameLevel(requested)) return requested
  try {
    const stored = Number(window.localStorage.getItem(LEVEL_KEY))
    return isGameLevel(stored) ? stored : undefined
  } catch {
    return undefined
  }
}

function rememberLevel(level: number) {
  try {
    window.localStorage.setItem(LEVEL_KEY, String(level))
  } catch {
    // Remembering the level is a convenience; private windows may refuse storage.
  }
}

function RoundScreen({ code, hskLevel, Game }: Props) {
  const [screen, setScreen] = useState<Screen>({ mode: 'ready' })
  const [level, setLevel] = useState(() => initialLevel(hskLevel))
  const controller = useRef<ReturnType<typeof createRoundController>>()
  function start() {
    if (level === undefined) return
    rememberLevel(level)
    void controller.current?.start(level)
  }
  useEffect(() => {
    const instance = createRoundController(code, setScreen)
    controller.current = instance
    const timer = window.setInterval(instance.tick, 200)
    window.addEventListener('online', instance.retryAnswers)
    return () => {
      controller.current = undefined
      instance.dispose()
      window.clearInterval(timer)
      window.removeEventListener('online', instance.retryAnswers)
    }
  }, [code])

  const result = screen.mode === 'result' ? screen.result : null
  const total = screen.mode === 'round' ? screen.round.timeLimitSeconds : 0
  return (
    <ArcadePage code={code} playing={screen.mode === 'round'}>
      <section aria-label="Ván chơi" className="min-w-0">
        {screen.mode === 'ready' && (
          <div className="core-panel core-setup">
            <div className="core-setup-copy">
              <span className="core-kicker">✦ MỘT VÁN NHANH · MỘT KỸ NĂNG MỚI</span>
              <h2>Sẵn sàng?<br /><em>Vào trận thôi.</em></h2>
              <p>Mỗi ván rút ngẫu nhiên câu hỏi đúng cấp bạn chọn. Máy chủ chấm từng lượt; điểm được xếp hạng riêng theo từng cấp HSK.</p>
              <div className="core-field-label" id="hsk-level-label">CẤP HSK</div>
              <div className="choice-row" role="group" aria-labelledby="hsk-level-label">
                {HSK_LEVELS.map((value) => (
                  <button key={value} type="button" aria-pressed={level === value}
                    className={level === value ? 'selected' : ''} onClick={() => setLevel(value)}>
                    HSK {value}
                  </button>
                ))}
              </div>
              {screen.message && <p role="alert">{screen.message}</p>}
              <button type="button" className="primary-button" disabled={level === undefined} onClick={start}>
                Bắt đầu <span aria-hidden="true">↗</span>
              </button>
              {level === undefined && <p className="core-hint">Chọn một cấp HSK để bắt đầu.</p>}
            </div>
            <GameArtwork code={code} />
          </div>
        )}
        {screen.mode === 'starting' && <p role="status" className="core-feedback">Đang tạo ván…</p>}
        {screen.mode === 'round' && (
          <div className="core-play">
            <div className="core-stats">
              <span className="stat-level">HSK {level}</span>
              {screen.lives !== undefined && (
                <span className="stat-lives" role="status">
                  <span className="sr-only">Còn {screen.lives} mạng</span>
                  <span aria-hidden="true">
                    {'♥'.repeat(screen.lives)}<span className="lost">{'♥'.repeat(Math.max(0, livesMax(screen) - screen.lives))}</span>
                  </span>
                </span>
              )}
              <span className={screen.seconds <= 10 ? 'stat-time urgent' : 'stat-time'}>
                Thời gian còn lại: <span className="tabular-nums">{screen.seconds} giây</span>
              </span>
              <span className="stat-score">Điểm: <strong>{screen.points}</strong></span>
              {screen.seconds > 0 && (
                <button type="button" className="round-exit" onClick={() => controller.current?.confirmExit(false)}>
                  Thoát
                </button>
              )}
            </div>
            <div className="core-timebar">
              <span style={{ width: `${total ? Math.min(100, screen.seconds / total * 100) : 0}%` }} />
            </div>
            {screen.confirming && (
              <div role="group" aria-label="Xác nhận thoát" className="round-notice">
                <p className="w-full">Bạn có muốn bỏ ván này?</p>
                <button type="button" className="primary-button" onClick={() => controller.current?.confirmExit(false)}>
                  Chơi tiếp
                </button>
                <button type="button" className="secondary-button" onClick={() => controller.current?.confirmExit(true)}>
                  Bỏ ván
                </button>
              </div>
            )}
            {screen.blocked && (
              <div className="round-notice">
                <p role="status">
                  {screen.message} ({screen.pending} đang chờ)
                </p>
                <button type="button" className="secondary-button" onClick={() => controller.current?.retryAnswers()}>
                  Thử gửi lại
                </button>
              </div>
            )}
            {screen.countdown > 0 ? (
              <p role="status" className="round-countdown">
                Bắt đầu sau {screen.countdown}
              </p>
            ) : (
              screen.seconds > 0 && (
                <Game
                  items={screen.round.items}
                  uiConfig={screen.round.uiConfig}
                  disabled={screen.disabled}
                  submitAnswer={(no, answer) => controller.current!.submitAnswer(no, answer)}
                  onMissed={(no) => controller.current?.onMissed(no)}
                  onItemShown={(no) => controller.current?.onItemShown(no)}
                  endRound={() => controller.current?.endRound()}
                />
              )
            )}
          </div>
        )}
        {screen.mode === 'finishing' && <p role="status" className="core-feedback">Đang kết thúc ván…</p>}
        {screen.mode === 'leaving' && <p role="status" className="core-feedback">Đang rời ván…</p>}
        {screen.mode === 'finish-error' && (
          <div className="core-panel core-center">
            <p role="alert">{screen.message}</p>
            <button type="button" className="secondary-button" onClick={() => void controller.current?.retryFinish()}>
              Kiểm tra lại
            </button>
          </div>
        )}
        {screen.mode === 'leave-error' && (
          <div className="core-panel core-center">
            <p role="alert">{screen.message}</p>
            <button type="button" className="secondary-button" onClick={() => void controller.current?.abandon()}>
              Thử lại
            </button>
          </div>
        )}
        {(screen.mode === 'finished' || screen.mode === 'expired' || screen.mode === 'abandoned') && (
          <div className="core-panel core-center core-result">
            <h1>
              {screen.mode === 'finished'
                ? 'Ván đã kết thúc'
                : screen.mode === 'expired'
                  ? 'Ván đã hết hạn'
                  : 'Đã bỏ ván'}
            </h1>
            <div className="button-row">{hubLink}</div>
          </div>
        )}
        {result && (
          <div className="core-panel core-center core-result">
            <span className="result-star" aria-hidden="true">✦ ✦ ✦</span>
            <span className="eyebrow">{screen.mode === 'result' && screen.outOfLives ? 'HẾT MẠNG' : 'KẾT THÚC VÁN'}</span>
            <h1>Kết quả ván chơi</h1>
            {result.newBest && (
              <p role="status" className="round-new-best">
                Kỷ lục mới!
              </p>
            )}
            <dl className="round-result-grid break-words">
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
            <div className="button-row">
              <button type="button" className="primary-button" onClick={start}>
                Chơi lại
              </button>
              {hubLink}
              <a className="secondary-button" href={webHref(`/leaderboards/games?game=${code}&hskLevel=${level ?? 1}`)}>
                Xem bảng xếp hạng
              </a>
            </div>
          </div>
        )}
      </section>
    </ArcadePage>
  )
}