import { useCallback, useEffect, useRef, useState } from 'react'

import type { ServedItem } from '../../shared/api/rounds'
import type { GameAnswer, GameProps } from '../../shared/ui/GameShell'

const fallbackItemMs = 4000

export function CharCatchGame(props: GameProps) {
  const [index, setIndex] = useState(0)
  const advance = useCallback(() => setIndex((current) => current + 1), [])
  const item = props.items[index]
  const { perItemMs, gridColumns } = props.uiConfig

  if (!item) return <p role="status">Đã đi qua tất cả các chữ.</p>
  return (
    <CatchItem
      key={item.no}
      item={item}
      perItemMs={typeof perItemMs === 'number' && perItemMs > 0 ? perItemMs : fallbackItemMs}
      columns={typeof gridColumns === 'number' && Number.isInteger(gridColumns) && gridColumns > 0 ? gridColumns : 3}
      disabled={props.disabled}
      submitAnswer={props.submitAnswer}
      onMissed={props.onMissed}
      onItemShown={props.onItemShown}
      onAdvance={advance}
    />
  )
}

type ItemProps = Pick<GameProps, 'disabled' | 'submitAnswer' | 'onMissed' | 'onItemShown'> & {
  item: ServedItem
  perItemMs: number
  columns: number
  onAdvance: () => void
}

function CatchItem({ item, perItemMs, columns, disabled, submitAnswer, onMissed, onItemShown, onAdvance }: ItemProps) {
  const audioUrl = typeof item.prompt.audioUrl === 'string' ? item.prompt.audioUrl : undefined
  const [audioReady, setAudioReady] = useState(false)
  const [audioFailed, setAudioFailed] = useState(false)
  const [needsTap, setNeedsTap] = useState(false)
  const [playing, setPlaying] = useState(false)
  const [replays, setReplays] = useState(0)
  const [remaining, setRemaining] = useState(perItemMs)
  const [selected, setSelected] = useState<string>()
  const [grading, setGrading] = useState(false)
  const [feedback, setFeedback] = useState<GameAnswer>()
  const audio = useRef<HTMLAudioElement>(null)
  const playAttempt = useRef(0)
  const initialPlay = useRef<'pending' | 'autoplay' | 'manual' | 'played'>('pending')
  const active = useRef(false)
  const resolved = useRef(false)
  const announced = useRef(false)
  const loadTimeLeft = useRef(perItemMs)
  const timeLeft = useRef(perItemMs)
  const answerDeadline = useRef<number>()
  const gradingDeadline = useRef(0)
  const options = item.options ?? []
  const ready = !audioUrl || audioReady
  const canChoose = ready && !disabled && selected === undefined && !audioFailed
  const miss = useCallback(() => {
    if (resolved.current) return
    resolved.current = true
    onMissed(item.no)
    onAdvance()
  }, [item.no, onAdvance, onMissed])

  useEffect(() => {
    active.current = true
    const element = audio.current
    return () => {
      active.current = false
      playAttempt.current += 1
      if (initialPlay.current === 'autoplay') initialPlay.current = 'pending'
      element?.pause()
    }
  }, [])

  useEffect(() => {
    if (!disabled || !audio.current) return
    playAttempt.current += 1
    audio.current.pause()
    setPlaying(false)
    if (initialPlay.current === 'autoplay') {
      // A paused load can retry on resume, using only its remaining time.
      initialPlay.current = audioReady ? 'manual' : 'pending'
      if (audioReady) setNeedsTap(true)
    }
  }, [audioReady, disabled])

  useEffect(() => {
    if (!disabled && !audioFailed && !resolved.current && initialPlay.current === 'pending' && audio.current) {
      initialPlay.current = 'autoplay'
      void playAudio(audio.current)
    }
  }, [audioFailed, disabled])

  useEffect(() => {
    if (disabled || !ready || audioFailed || announced.current || resolved.current) return
    announced.current = true
    onItemShown?.(item.no)
  }, [audioFailed, disabled, item.no, onItemShown, ready])

  useEffect(() => {
    if (resolved.current) return
    if (disabled) {
      setRemaining(timeLeft.current)
      return
    }

    if (audioFailed) {
      miss()
      return
    }
    if (!ready) {
      const deadline = performance.now() + loadTimeLeft.current
      const timeout = window.setTimeout(miss, loadTimeLeft.current)
      return () => {
        window.clearTimeout(timeout)
        loadTimeLeft.current = Math.max(0, deadline - performance.now())
      }
    }
    if (selected !== undefined) return
    const deadline = performance.now() + timeLeft.current
    answerDeadline.current = deadline
    let timeout: number
    function tick() {
      const left = Math.max(0, deadline - performance.now())
      setRemaining(left)
      if (left === 0) miss()
      else timeout = window.setTimeout(tick, Math.min(50, left))
    }

    timeout = window.setTimeout(tick, Math.min(50, timeLeft.current))
    return () => {
      window.clearTimeout(timeout)
      answerDeadline.current = undefined
      timeLeft.current = Math.max(0, deadline - performance.now())
    }
  }, [audioFailed, disabled, miss, ready, selected])

  useEffect(() => {
    if (disabled || !grading) return
    const timeout = window.setTimeout(() => setGrading(false), Math.max(0, gradingDeadline.current - performance.now()))
    return () => window.clearTimeout(timeout)
  }, [disabled, grading])

  useEffect(() => {
    if (disabled || !feedback) return
    const timeout = window.setTimeout(onAdvance, 500)
    return () => window.clearTimeout(timeout)
  }, [disabled, feedback, onAdvance])

  async function playAudio(element: HTMLAudioElement) {
    const attempt = ++playAttempt.current
    setPlaying(true)
    try {
      await element.play()
      if (attempt !== playAttempt.current || resolved.current) return
      initialPlay.current = 'played'
      setNeedsTap(false)
      setAudioReady(true)
    } catch (error) {
      if (attempt !== playAttempt.current || resolved.current) return
      setPlaying(false)
      if (error instanceof DOMException && error.name === 'NotAllowedError') {
        if (initialPlay.current !== 'played') {
          initialPlay.current = 'manual'
          setNeedsTap(true)
          setAudioReady(true)
        }
      } else {
        setAudioFailed(true)
      }
    }
  }

  function listen() {
    if (!audio.current || !canChoose || playing || resolved.current) return
    if (!needsTap) {
      if (replays >= item.rules.maxReplays) return
      setReplays(replays + 1)
    }
    audio.current.currentTime = 0
    void playAudio(audio.current)
  }

  async function choose(key: string) {
    if (!canChoose || resolved.current) return
    if (answerDeadline.current !== undefined && performance.now() >= answerDeadline.current) {
      miss()
      return
    }
    resolved.current = true
    playAttempt.current += 1
    audio.current?.pause()
    setSelected(key)
    gradingDeadline.current = performance.now() + 300
    setGrading(true)
    try {
      const result = await submitAnswer(item.no, key)
      if (active.current) {
        setGrading(false)
        setFeedback(result)
      }
    } catch {
      if (active.current) onAdvance()
    }
  }

  const instruction = item.prompt.hint ?? item.prompt.pinyin
  return (
    <section aria-label="Bắt Chữ" className="min-w-0">
      <div className="core-panel core-center core-game-panel catch-panel">
        <span className="question-kicker">{audioUrl ? 'NGHE · BẮT ĐÚNG CHỮ' : 'NHÌN GỢI Ý · BẮT ĐÚNG CHỮ'}</span>
        <div className="catch-prompt">
          <span className="catch-target" aria-hidden="true">◎</span>
          {audioUrl ? (
            <div>
              <audio ref={audio} src={audioUrl} preload="auto" onCanPlay={() => setAudioReady(true)}
                onError={() => setAudioFailed(true)} onEnded={() => setPlaying(false)} />
              <button type="button" onClick={listen} disabled={!canChoose || playing || (!needsTap && replays >= item.rules.maxReplays)}
                className={needsTap ? 'primary-button' : 'secondary-button'}>
                {needsTap ? 'Chạm để nghe' : 'Nghe lại'}
              </button>
              {!ready && <small role="status">Đang tải âm thanh…</small>}
            </div>
          ) : (
            <div><h2 className="break-words"><strong>{String(instruction ?? '')}</strong></h2></div>
          )}
          <span className="catch-clock" aria-hidden="true">{Math.ceil(remaining / 1000)}s</span>
        </div>
        <progress aria-label="Thời gian còn lại" className="catch-progress block w-full" max={perItemMs} value={Math.ceil(remaining)} />
        <div role="group" aria-label="Phương án" className="answer-grid catch-grid"
          style={{ gridTemplateColumns: 'repeat(' + columns + ', minmax(0, 1fr))' }}>
          {options.map(({ key, text }, index) => {
            const correct = feedback?.correctAnswer === key
            const wrong = feedback && !feedback.correct && key === selected
            return (
              <button type="button" key={key} disabled={!canChoose} onClick={() => void choose(key)}
                className={correct ? 'answer-correct' : wrong ? 'answer-wrong' : ''}>
                <span className="answer-index" aria-hidden="true">{index + 1}</span>
                <b className="break-words">{text}</b>
              </button>
            )
          })}
        </div>
      </div>
      <div className="core-feedback">
        {selected && !feedback && <p role="status">{grading && !disabled && performance.now() < gradingDeadline.current ? 'Đang chấm…' : 'Đã gửi câu trả lời.'}</p>}
        {feedback && <p role="status">{feedback.correct ? 'Chính xác' : 'Chưa đúng'}</p>}
      </div>
    </section>
  )
}
