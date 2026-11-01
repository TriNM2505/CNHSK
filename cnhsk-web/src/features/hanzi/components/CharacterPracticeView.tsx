import { ArrowLeft, Eye, Headphones, Lock, RotateCcw, Timer, Undo2 } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { AudioButton } from '@/features/practice'
import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageHeader,
  Skeleton,
} from '@/shared/ui'

import type { StrokeData } from 'hanzi-writer'

import type { CharacterDetail, WritingAttemptRequest, WritingAttemptResult, WritingMode } from '../types'
import { WRITING_MODE_LABELS, WRITING_MODES, writingModeAvailability } from '../writingModes'
import { HanziCanvas, type HanziCanvasHandle } from './HanziCanvas'

export interface CharacterPracticeViewProps {
  attemptError?: { errorCode?: string; message: string }
  attemptResult?: WritingAttemptResult | null
  attemptState?: 'idle' | 'submitting' | 'recorded' | 'unknown' | 'rejected'
  character?: CharacterDetail
  error?: { message: string; requestId?: string }
  mode?: WritingMode
  onModeChange?: (mode: WritingMode) => void
  onRetry: () => void
  onRetryUnknown?: () => void
  onSubmitAttempt?: (request: WritingAttemptRequest) => void
  retryAfterSeconds?: number
  state: 'loading' | 'ready' | 'not-found' | 'error'
}

const BACK_PATH = '/learn/characters/writing'
const CHALLENGE_PATH = '/learn/characters/writing/challenge'
const RECALL_INITIAL_PREVIEW_MS = 5000
const RECALL_EXTRA_PREVIEW_MS = 3000
const MAX_RECALL_PREVIEWS = 2

type ActivityStage = 'loading' | 'preview' | 'writing' | 'animating' | 'fallback' | 'submitting'

function clampDuration(value: number) {
  return Math.min(Math.max(Math.round(value), 0), 3_600_000)
}

export function CharacterPracticeView({
  attemptError,
  attemptResult,
  attemptState = 'idle',
  character,
  error,
  mode,
  onModeChange,
  onRetry,
  onRetryUnknown,
  onSubmitAttempt,
  retryAfterSeconds,
  state,
}: CharacterPracticeViewProps) {
  const canvasRef = useRef<HanziCanvasHandle>(null)
  const mountedRef = useRef(false)
  const readyRef = useRef(false)
  const modeRef = useRef<WritingMode | undefined>(undefined)
  const modeGenerationRef = useRef(0)
  const quizGenerationRef = useRef(0)
  const quizBaseMistakesRef = useRef(0)
  const totalMistakesRef = useRef(0)
  const currentStrokeRef = useRef(0)
  const startedAtRef = useRef<number | null>(null)
  const timerRef = useRef<number | null>(null)
  const submitStartedRef = useRef(false)
  const submitAttemptRef = useRef(onSubmitAttempt)
  const audioReplayCountRef = useRef(0)
  const previewCountRef = useRef(0)
  const previewMsRef = useRef(0)
  const usedAnimationRef = useRef(false)
  const [writerReady, setWriterReady] = useState(false)
  const [dataError, setDataError] = useState(false)
  const [stage, setStage] = useState<ActivityStage>('loading')
  const [totalMistakes, setTotalMistakes] = useState(0)
  const [previewCount, setPreviewCount] = useState(0)
  const [remainingPreviewCount, setRemainingPreviewCount] = useState(MAX_RECALL_PREVIEWS)

  submitAttemptRef.current = onSubmitAttempt

  const stopTimer = useCallback(() => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    timerRef.current = null
  }, [])

  const cancelQuiz = useCallback(() => {
    quizGenerationRef.current += 1
    canvasRef.current?.cancelQuiz()
  }, [])

  const startQuiz = useCallback((startStroke: number, previousMistakes: number, forMode: WritingMode) => {
    const canvas = canvasRef.current
    const expectedModeGeneration = modeGenerationRef.current
    if (!canvas || !mountedRef.current || modeRef.current !== forMode || dataError) return

    cancelQuiz()
    const run = quizGenerationRef.current
    quizBaseMistakesRef.current = previousMistakes
    currentStrokeRef.current = startStroke
    canvas.quiz({
      quizStartStrokeNum: startStroke,
      showHintAfterMisses: forMode === 'STROKE' ? 1 : false,
      onMistake: (stroke: StrokeData) => {
        if (run !== quizGenerationRef.current || expectedModeGeneration !== modeGenerationRef.current) return
        currentStrokeRef.current = stroke.strokeNum
        const count = previousMistakes + stroke.totalMistakes
        totalMistakesRef.current = count
        setTotalMistakes(count)
      },
      onCorrectStroke: (stroke: StrokeData) => {
        if (run !== quizGenerationRef.current || expectedModeGeneration !== modeGenerationRef.current) return
        currentStrokeRef.current = stroke.strokeNum + 1
        const count = previousMistakes + stroke.totalMistakes
        totalMistakesRef.current = count
        setTotalMistakes(count)
      },
      onComplete: (summary) => {
        if (run !== quizGenerationRef.current || expectedModeGeneration !== modeGenerationRef.current
            || submitStartedRef.current) return
        const count = previousMistakes + summary.totalMistakes
        totalMistakesRef.current = count
        setTotalMistakes(count)
        submitStartedRef.current = true
        setStage('submitting')
        const strokeCount = character?.strokeCount ?? 0
        const accuracy = strokeCount > 0
          ? Math.round(Math.max(0, 1 - count / strokeCount) * 100) / 100
          : 0
        submitAttemptRef.current?.({
          accuracy,
          completed: true,
          durationMs: clampDuration(performance.now() - (startedAtRef.current ?? performance.now())),
          lexemeId: character?.id ?? 0,
          mode: forMode,
          previewCount: previewCountRef.current,
          previewMs: previewMsRef.current,
          replayCount: audioReplayCountRef.current,
          strokeMisses: Math.min(count, strokeCount * 10),
          usedAnimation: usedAnimationRef.current,
        })
      },
    })
    setStage('writing')
  }, [cancelQuiz, character?.id, character?.strokeCount, dataError])

  const beginMode = useCallback((activeMode: WritingMode) => {
    const canvas = canvasRef.current
    if (!canvas || !mountedRef.current || !readyRef.current || dataError) return
    canvas.setVisibility(activeMode === 'RECALL', activeMode !== 'DICTATION')
    totalMistakesRef.current = 0
    quizBaseMistakesRef.current = 0
    currentStrokeRef.current = 0
    audioReplayCountRef.current = 0
    previewCountRef.current = 0
    previewMsRef.current = activeMode === 'RECALL' ? RECALL_INITIAL_PREVIEW_MS : 0
    usedAnimationRef.current = false
    submitStartedRef.current = false
    setTotalMistakes(0)
    setPreviewCount(0)
    setRemainingPreviewCount(MAX_RECALL_PREVIEWS)

    const generation = modeGenerationRef.current
    if (activeMode === 'RECALL') {
      canvas.setVisibility(true, true)
      setStage('preview')
      stopTimer()
      timerRef.current = window.setTimeout(() => {
        if (!mountedRef.current || generation !== modeGenerationRef.current || modeRef.current !== activeMode) return
        canvas.setVisibility(false, false)
        startedAtRef.current = performance.now()
        startQuiz(0, 0, activeMode)
      }, RECALL_INITIAL_PREVIEW_MS)
      return
    }

    startedAtRef.current = performance.now()
    startQuiz(0, 0, activeMode)
  }, [dataError, startQuiz, stopTimer])

  const submitIncomplete = useCallback(() => {
    const activeMode = modeRef.current
    const currentCharacter = character
    if (!activeMode || !currentCharacter || submitStartedRef.current || attemptState === 'submitting') return
    submitStartedRef.current = true
    cancelQuiz()
    stopTimer()
    setStage('submitting')
    const strokeCount = currentCharacter.strokeCount ?? 0
    const count = totalMistakesRef.current
    const accuracy = strokeCount > 0 ? Math.round(Math.max(0, 1 - count / strokeCount) * 100) / 100 : 0
    submitAttemptRef.current?.({
      accuracy,
      completed: false,
      durationMs: clampDuration(startedAtRef.current === null ? 0 : performance.now() - startedAtRef.current),
      lexemeId: currentCharacter.id,
      mode: activeMode,
      previewCount: previewCountRef.current,
      previewMs: previewMsRef.current,
      replayCount: audioReplayCountRef.current,
      strokeMisses: Math.min(count, strokeCount * 10),
      usedAnimation: usedAnimationRef.current,
    })
  }, [attemptState, cancelQuiz, character, stopTimer])

  useEffect(() => {
    mountedRef.current = true
    const canvas = canvasRef.current
    return () => {
      mountedRef.current = false
      modeGenerationRef.current += 1
      stopTimer()
      canvas?.cancelAnimations()
      canvas?.cancelQuiz()
    }
  }, [stopTimer])

  useEffect(() => {
    if (modeRef.current !== mode) {
      stopTimer()
      canvasRef.current?.cancelAnimations()
      cancelQuiz()
      modeGenerationRef.current += 1
      modeRef.current = mode
      startedAtRef.current = null
      readyRef.current = writerReady
      setDataError(false)
      setStage('loading')
      submitStartedRef.current = false
      if (mode) beginMode(mode)
      return
    }
    if (mode && writerReady && !readyRef.current) {
      readyRef.current = true
      beginMode(mode)
    }
  }, [beginMode, cancelQuiz, mode, stopTimer, writerReady])

  useEffect(() => {
    if (attemptState === 'recorded') {
      stopTimer()
      canvasRef.current?.cancelAnimations()
      cancelQuiz()
      canvasRef.current?.setVisibility(true, true)
      setStage('submitting')
    } else if (attemptState === 'unknown' || attemptState === 'rejected') {
      stopTimer()
      canvasRef.current?.cancelAnimations()
      cancelQuiz()
      setStage('submitting')
    } else if (attemptState === 'submitting') {
      setStage('submitting')
    }
  }, [attemptState, cancelQuiz, stopTimer])

  const handleReady = useCallback(() => {
    if (!mountedRef.current) return
    setWriterReady(true)
  }, [])

  const handleDataError = useCallback(() => {
    if (!mountedRef.current) return
    setDataError(true)
    stopTimer()
    cancelQuiz()
    canvasRef.current?.cancelAnimations()
    setStage('fallback')
  }, [cancelQuiz, stopTimer])

  const handleAnimate = useCallback(() => {
    if (modeRef.current !== 'STROKE' || stage !== 'writing' || !canvasRef.current) return
    const generation = modeGenerationRef.current
    const resumeStroke = currentStrokeRef.current
    const previousMistakes = totalMistakesRef.current
    usedAnimationRef.current = true
    setStage('animating')
    cancelQuiz()
    canvasRef.current.animateCharacter((canceled) => {
      if (canceled || !mountedRef.current || generation !== modeGenerationRef.current || modeRef.current !== 'STROKE') return
      startQuiz(resumeStroke, previousMistakes, 'STROKE')
    })
  }, [cancelQuiz, stage, startQuiz])

  const handlePreview = useCallback(() => {
    if (modeRef.current !== 'RECALL' || stage !== 'writing' || previewCountRef.current >= MAX_RECALL_PREVIEWS) return
    const canvas = canvasRef.current
    if (!canvas) return
    const generation = modeGenerationRef.current
    const resumeStroke = currentStrokeRef.current
    const previousMistakes = totalMistakesRef.current
    cancelQuiz()
    previewCountRef.current += 1
    previewMsRef.current = RECALL_INITIAL_PREVIEW_MS + previewCountRef.current * RECALL_EXTRA_PREVIEW_MS
    setPreviewCount(previewCountRef.current)
    setRemainingPreviewCount(MAX_RECALL_PREVIEWS - previewCountRef.current)
    canvas.setVisibility(true, true)
    setStage('preview')
    stopTimer()
    timerRef.current = window.setTimeout(() => {
      if (!mountedRef.current || generation !== modeGenerationRef.current || modeRef.current !== 'RECALL') return
      canvas.setVisibility(false, false)
      startQuiz(resumeStroke, previousMistakes, 'RECALL')
    }, RECALL_EXTRA_PREVIEW_MS)
  }, [cancelQuiz, stage, startQuiz, stopTimer])

  const handleAudioFailed = useCallback(() => {
    if (modeRef.current !== 'DICTATION') return
    stopTimer()
    cancelQuiz()
    canvasRef.current?.cancelAnimations()
    onModeChange?.('STROKE')
  }, [cancelQuiz, onModeChange, stopTimer])

  const handleRecallNotRemembered = useCallback(() => {
    if (modeRef.current !== 'RECALL') return
    stopTimer()
    cancelQuiz()
    canvasRef.current?.cancelAnimations()
    onModeChange?.('STROKE')
  }, [cancelQuiz, onModeChange, stopTimer])

  if (state === 'loading') {
    return (
      <div aria-label="Đang tải chữ" className="space-y-4">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    )
  }

  if (state === 'not-found') {
    return (
      <EmptyState
        action={<Button to={BACK_PATH}>Chọn chữ khác</Button>}
        description="Chữ này không tồn tại hoặc không phải chữ đơn."
        title="Không tìm thấy chữ"
      />
    )
  }

  if (state === 'error' || !character) {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải chữ"
      />
    )
  }

  const writingUnavailable = !mode || dataError
  const isBlocked = attemptState === 'unknown' || attemptState === 'submitting' || attemptState === 'recorded'
  const showCanvas = Boolean(mode) && !dataError

  return (
    <div className="space-y-6">
      <Button to={BACK_PATH} variant="ghost">
        <ArrowLeft aria-hidden="true" className="size-5" />
        Danh sách chữ
      </Button>

      <PageHeader
        subtitle="Tập viết theo thứ tự nét chuẩn và chọn mức luyện phù hợp với chữ này."
        title="Luyện viết chữ Hán"
      />

      <fieldset className="space-y-3">
        <legend className="text-h3 text-ink">Chế độ luyện</legend>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {WRITING_MODES.map((option) => {
            const availability = writingModeAvailability(character, option)
            const selected = option === mode
            return (
              <button
                aria-pressed={selected}
                className={cn(
                  'flex min-h-touch flex-col items-start gap-1 rounded-md border bg-surface p-4 text-left transition-colors',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2',
                  'disabled:cursor-not-allowed disabled:opacity-70',
                  selected ? 'border-brand-600 bg-brand-50' : 'border-line hover:border-brand-600',
                )}
                disabled={!availability.available || isBlocked}
                key={option}
                onClick={() => onModeChange?.(option)}
                type="button"
              >
                <span className="text-body font-semibold text-ink">{WRITING_MODE_LABELS[option].title}</span>
                <span className="text-body-sm text-ink-secondary">{WRITING_MODE_LABELS[option].description}</span>
                {availability.reason ? (
                  <span className="mt-1 flex items-center gap-1 text-caption text-ink-muted">
                    <Lock aria-hidden="true" className="size-3.5" />
                    {availability.reason}
                  </span>
                ) : null}
              </button>
            )
          })}
          <Link
            className="flex min-h-touch flex-col items-start gap-1 rounded-md border border-line bg-surface p-4 text-left transition-colors hover:border-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
            to={CHALLENGE_PATH}
          >
            <span className="text-body font-semibold text-ink">Thử thách</span>
            <span className="text-body-sm text-ink-secondary">Viết 10 chữ hệ thống chọn trong giới hạn 200 giây.</span>
            <span className="mt-1 flex items-center gap-1 text-caption text-ink-muted">
              <Timer aria-hidden="true" className="size-3.5" />
              Tối đa 3 chữ trượt
            </span>
          </Link>
        </div>
      </fieldset>

      {mode ? (
        <>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card className="min-w-0 space-y-5">
              <div className="text-center">
                <p className="text-label text-ink-secondary">{WRITING_MODE_LABELS[mode].title}</p>
                {mode === 'STROKE' ? (
                  <>
                    <HanziText className="mt-2 block" size="lg">{character.text}</HanziText>
                    <p className="mt-2 text-body text-ink-secondary">{character.pinyin ?? '—'} · {character.meaning}</p>
                  </>
                ) : (
                  <p className="mt-2 text-body-sm text-ink-secondary" role="status">
                    {mode === 'RECALL'
                      ? 'Chữ mẫu chỉ hiện 5 giây trước khi bắt đầu và được ẩn trong lúc viết.'
                      : 'Nghe phát âm rồi viết; chữ và viền mẫu được ẩn trong lúc viết.'}
                  </p>
                )}
              </div>

              {showCanvas ? (
                <HanziCanvas
                  character={character.text}
                  onDataError={handleDataError}
                  onReady={handleReady}
                  ref={canvasRef}
                  showCharacter={mode === 'RECALL'}
                  showOutline={mode !== 'DICTATION'}
                />
              ) : (
                <div className="mx-auto flex aspect-square w-full min-w-[240px] max-w-[300px] items-center justify-center rounded-md border border-line bg-surface-subtle">
                  <HanziText size="lg">{character.text}</HanziText>
                </div>
              )}

              {mode === 'DICTATION' && character.audioUrl && !dataError ? (
                <AudioButton
                  audioUrl={character.audioUrl}
                  disabled={attemptState === 'submitting' || attemptState === 'recorded' || attemptState === 'unknown'}
                  initialReplayCount={0}
                  maxReplays={3}
                  onAudioFailed={handleAudioFailed}
                  onReplayCountChange={(count) => { audioReplayCountRef.current = count }}
                />
              ) : null}

              {dataError ? (
                <p className="rounded-md bg-warning-bg p-3 text-body-sm text-warning" role="alert">
                  Chữ này chưa có dữ liệu nét
                </p>
              ) : null}

              {mode !== 'DICTATION' && stage === 'loading' ? (
                <p className="text-center text-body-sm text-ink-secondary" role="status">Đang tải dữ liệu nét…</p>
              ) : null}
              {stage === 'preview' ? (
                <p className="text-center text-body-sm text-ink-secondary" role="status">
                  {previewCount === 0 ? 'Xem chữ mẫu trong 5 giây…' : `Xem mẫu thêm ${previewCount}/2…`}
                </p>
              ) : null}
              {stage === 'animating' ? (
                <p className="text-center text-body-sm text-ink-secondary" role="status">Đang xem lại nét…</p>
              ) : null}
              {stage === 'writing' ? (
                <p className="text-center text-body-sm text-ink-secondary" role="status">
                  Số nét sai: {totalMistakes}
                </p>
              ) : null}

              {attemptState === 'unknown' ? (
                <div className="space-y-3 rounded-md bg-warning-bg p-3 text-body-sm text-warning" role="alert">
                  <p>Chưa xác nhận được kết quả. Máy chủ có thể đã ghi nhận lượt này.</p>
                  <div className="flex flex-wrap gap-2">
                    <Button onClick={onRetryUnknown} variant="outline">Gửi lại</Button>
                    <Button to={BACK_PATH} variant="ghost">Quay về danh sách</Button>
                  </div>
                </div>
              ) : null}
              {attemptState === 'rejected' && attemptError ? (
                <p className="rounded-md bg-danger-bg p-3 text-body-sm text-danger" role="alert">
                  {attemptError.errorCode === 'RATE_LIMITED' && retryAfterSeconds !== undefined
                    ? `Bạn đã ghi quá nhiều lượt viết. Hãy thử lại sau ${retryAfterSeconds} giây.`
                    : attemptError.message}
                </p>
              ) : null}
              {attemptState === 'recorded' && attemptResult ? (
                <p className="rounded-md bg-info-bg p-3 text-body-sm text-info" role="status">
                  {attemptResult.recorded && !attemptResult.counted
                    ? 'Đã ghi nhận; lượt này chưa được tính vì bạn vừa luyện lại.'
                    : attemptResult.recorded
                      ? 'Đã ghi nhận kết quả.'
                      : 'Máy chủ chưa ghi nhận kết quả.'}
                  {mode === 'DICTATION' ? <span className="block">Chữ đúng: {character.text}</span> : null}
                </p>
              ) : null}

              {attemptState !== 'unknown' && attemptState !== 'rejected' && attemptState !== 'recorded' ? (
                <ModeControls
                  disabled={stage === 'loading' || stage === 'submitting' || stage === 'fallback' || stage === 'animating'}
                  mode={mode}
                  onAnimation={handleAnimate}
                  onPreview={handlePreview}
                  onRecallNotRemembered={handleRecallNotRemembered}
                  onStop={submitIncomplete}
                  previewRemaining={remainingPreviewCount}
                />
              ) : null}
              {attemptState === 'submitting' || stage === 'submitting' ? (
                <p className="text-center text-body-sm text-ink-secondary" role="status">Đang gửi kết quả…</p>
              ) : null}
            </Card>

            <PracticeContext character={character} mode={mode} showPrivateCharacter={attemptState === 'recorded'} />
          </div>
          {!writingUnavailable ? (
            <p className="rounded-md bg-info-bg p-3 text-body-sm text-info">
              Viết lại cùng chữ, cùng chế độ trong 10 phút được máy chủ ghi nhận nhưng không tính thêm.
            </p>
          ) : null}
        </>
      ) : (
        <EmptyState
          description="Chữ này chưa có dữ liệu nét nên chưa luyện viết được. Bạn vẫn có thể luyện nhận diện chữ."
          title="Chưa luyện viết được"
        />
      )}
    </div>
  )
}

function PracticeContext({
  character,
  mode,
  showPrivateCharacter,
}: {
  character: CharacterDetail
  mode: WritingMode
  showPrivateCharacter: boolean
}) {
  if (mode !== 'STROKE' && !showPrivateCharacter) {
    return (
      <Card className="space-y-4">
        <h2 className="text-h3 text-ink">{mode === 'RECALL' ? 'Viết từ trí nhớ' : 'Nghe rồi viết'}</h2>
        <p className="text-body text-ink-secondary">
          {mode === 'RECALL'
            ? 'Bạn có tối đa 2 lần xem mẫu thêm, mỗi lần 3 giây. Khi khung viết mở, chữ mẫu không nằm cạnh bài làm.'
            : 'Bạn có tối đa 3 lượt nghe. Nếu không nghe được, hãy chuyển về Viết theo nét mà không gửi kết quả nghe chép.'}
        </p>
        <p className="rounded-md bg-warning-bg p-3 text-body-sm text-warning">Chữ đích và thông tin gợi ý được giấu trong lúc trả lời.</p>
      </Card>
    )
  }

  return (
    <Card className="space-y-4">
      <h2 className="text-h3 text-ink">Thông tin chữ</h2>
      <div className="flex flex-wrap items-center gap-4">
        <HanziText size="md">{character.text}</HanziText>
        <div className="min-w-0 flex-1">
          <p className="text-h3 text-ink">
            {character.pinyin ?? '—'}
            {character.sinoViet ? <span className="ml-2 text-body text-ink-secondary">({character.sinoViet})</span> : null}
          </p>
          <p className="text-body text-ink-secondary">{character.meaning}</p>
        </div>
      </div>
      <dl className="grid gap-3 sm:grid-cols-2">
        <Fact label="Cấp">{character.hskLevel ? <LevelBadge exact level={character.hskLevel} /> : '—'}</Fact>
        <Fact label="Số nét">{character.strokeCount ? `${character.strokeCount} nét` : '—'}</Fact>
        <Fact label="Bộ thủ">
          {character.radical ? <span className="font-hanzi" lang="zh-CN">{character.radical}</span> : '—'}
        </Fact>
      </dl>
    </Card>
  )
}

function Fact({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="rounded-sm bg-surface-subtle p-3">
      <dt className="text-caption text-ink-muted">{label}</dt>
      <dd className="mt-1 text-body font-semibold text-ink">{children}</dd>
    </div>
  )
}

function ModeControls({
  disabled,
  mode,
  onAnimation,
  onPreview,
  onRecallNotRemembered,
  onStop,
  previewRemaining,
}: {
  disabled: boolean
  mode: WritingMode
  onAnimation: () => void
  onPreview: () => void
  onRecallNotRemembered: () => void
  onStop: () => void
  previewRemaining: number
}) {
  return (
    <div className="flex flex-wrap justify-center gap-3">
      {mode === 'STROKE' ? (
        <Button disabled={disabled} onClick={onAnimation} variant="outline">
          <RotateCcw aria-hidden="true" className="size-5" />Xem lại nét
        </Button>
      ) : null}
      {mode === 'RECALL' ? (
        <>
          <Button disabled={disabled || previewRemaining === 0} onClick={onPreview} variant="outline">
            <Eye aria-hidden="true" className="size-5" />Xem mẫu thêm (còn {previewRemaining} lần)
          </Button>
          <Button disabled={disabled} onClick={onRecallNotRemembered} variant="ghost">
            <Undo2 aria-hidden="true" className="size-5" />Chưa nhớ — về viết theo nét
          </Button>
        </>
      ) : null}
      <Button disabled={disabled} onClick={onStop} variant="ghost">Dừng</Button>
      {mode === 'DICTATION' ? (
        <span className="flex items-center gap-2 text-body-sm text-ink-secondary">
          <Headphones aria-hidden="true" className="size-5" />Nghe tối đa 3 lần
        </span>
      ) : null}
    </div>
  )
}
