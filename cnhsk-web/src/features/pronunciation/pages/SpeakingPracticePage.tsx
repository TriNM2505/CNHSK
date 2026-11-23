import { CheckCircle2, Mic, RotateCcw, Send, Shuffle, Square, Volume2 } from 'lucide-react'
import { useEffect, useState } from 'react'

import { useCurrentUser } from '@/shared/auth'
import { cn } from '@/shared/lib/cn'
import { Button, Card, EmptyState, ErrorState, PageBanner, Skeleton, SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import { getSpeakingItems, submitSpeaking, type SpeakingItem, type SpeakingKind } from '../api/speaking'
import { PronunciationTabs } from '../components/PronunciationTabs'
import { MAX_RECORDING_SECONDS, playModel, useVoiceRecorder } from '../use-voice-recorder'

const KINDS: readonly { label: string; value: SpeakingKind }[] = [
  { label: 'Từ', value: 'WORD' },
  { label: 'Câu', value: 'SENTENCE' },
]

function clock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/**
 * SCR-016 speaking half: hear the model, record the same word or sentence, send it to a teacher.
 * In `guest` mode (trial learning) the visitor records and listens back, but sending needs an account.
 */
export default function SpeakingPracticePage({ guest = false }: { guest?: boolean }) {
  const { user } = useCurrentUser()
  const levels: number[] = [...SUPPORTED_HSK_LEVELS]
  const [level, setLevel] = useState<number>(() =>
    user?.hskLevel && levels.includes(user.hskLevel) ? user.hskLevel : (levels[0] ?? 1),
  )
  const [kind, setKind] = useState<SpeakingKind>('WORD')
  const [items, setItems] = useState<SpeakingItem[]>([])
  const [index, setIndex] = useState(0)
  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>('loading')
  const [loadError, setLoadError] = useState<string>()
  const [sending, setSending] = useState(false)
  const [progress, setProgress] = useState(0)
  const [sendError, setSendError] = useState<string>()
  const [sent, setSent] = useState<{ id: number; reference: string }>()
  const recorder = useVoiceRecorder()
  const item = items[index]

  async function fetchItems() {
    setLoad('loading')
    try {
      setItems(await getSpeakingItems(level, kind, guest))
      setIndex(0)
      setLoad('ready')
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : undefined)
      setLoad('error')
    }
  }

  useEffect(() => {
    void fetchItems()
    recorder.reset()
    setSent(undefined)
    // Reload only when the learner picks another level or kind.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [level, kind])

  function nextItem() {
    recorder.reset()
    setSent(undefined)
    setSendError(undefined)
    if (index + 1 < items.length) setIndex(index + 1)
    else void fetchItems()
  }

  async function send() {
    if (!item || !recorder.recording) return
    setSending(true)
    setProgress(0)
    setSendError(undefined)
    try {
      const request = await submitSpeaking(item, recorder.recording, setProgress)
      setSent({ id: request.id, reference: request.reference })
    } catch (error) {
      setSendError(error instanceof Error ? error.message : 'Chưa gửi được, thử lại.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className={cn('w-full min-w-0 space-y-6', !guest && 'mx-auto max-w-content pb-16')}>
      {guest ? null : (
        <>
          <PageBanner subtitle="Nghe mẫu, đọc lại và gửi giáo viên chấm phát âm." title="Phát âm" />
          <PronunciationTabs />
        </>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <div aria-label="Loại bài" className="inline-flex rounded-pill border border-line bg-surface p-1" role="group">
          {KINDS.map((option) => (
            <button
              aria-pressed={kind === option.value}
              className={cn(
                'min-h-10 rounded-pill px-5 text-body-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                kind === option.value ? 'bg-brand-600 text-surface' : 'text-ink-secondary hover:text-ink',
              )}
              key={option.value}
              onClick={() => setKind(option.value)}
              type="button"
            >
              {option.label}
            </button>
          ))}
        </div>
        <select
          aria-label="Cấp HSK"
          className="min-h-10 rounded-md border border-line bg-surface px-3 text-body-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          onChange={(event) => setLevel(Number(event.target.value))}
          value={level}
        >
          {levels.map((value) => (
            <option key={value} value={value}>
              HSK {value}
            </option>
          ))}
        </select>
        <Button disabled={load !== 'ready' || sending} onClick={nextItem} size="sm" variant="outline">
          <Shuffle aria-hidden="true" className="size-4" />
          Bài khác
        </Button>
      </div>

      <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
        <div className={cn('min-w-0', guest ? 'lg:col-span-12' : 'lg:col-span-8')}>
          {load === 'loading' ? <Skeleton className="h-96" /> : null}
          {load === 'error' ? (
            <ErrorState message={loadError ?? 'Vui lòng thử lại sau.'} onRetry={() => void fetchItems()} title="Không tải được bài đọc" />
          ) : null}
          {load === 'ready' && !item ? (
            <EmptyState
              description="Cấp này chưa có nội dung để đọc. Hãy chọn cấp hoặc loại bài khác."
              icon={<Mic aria-hidden="true" className="size-5" />}
              title="Chưa có bài đọc"
            />
          ) : null}
          {load === 'ready' && item ? (
            <Card className="min-w-0 space-y-6 p-6 sm:p-8">
              <div className="space-y-3 text-center">
                <span className="inline-flex rounded-pill bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
                  {item.kind === 'WORD' ? 'Đọc từ' : 'Đọc câu'} · HSK {item.hskLevel}
                </span>
                <p className={cn('break-words font-hanzi text-ink', item.kind === 'WORD' ? 'text-hanzi-lg' : 'text-hanzi-md')} lang="zh">
                  {item.text}
                </p>
                {item.pinyin ? <p className="text-h3 text-brand-700">{item.pinyin}</p> : null}
                {item.meaning ? <p className="text-body text-ink-secondary">{item.meaning}</p> : null}
                <Button onClick={() => playModel(item.text, item.audioUrl)} variant="outline">
                  <Volume2 aria-hidden="true" className="size-5" />
                  Nghe mẫu
                </Button>
              </div>

              <div className="space-y-4 border-t border-line pt-6 text-center">
                {recorder.state === 'unsupported' ? (
                  <p className="text-body-sm text-ink-secondary" role="status">
                    Trình duyệt này không ghi âm được. Hãy dùng Chrome hoặc Edge bản mới.
                  </p>
                ) : null}
                {recorder.state === 'denied' ? (
                  <p className="text-body-sm text-danger" role="alert">
                    Chưa được phép dùng micro. Bấm biểu tượng ổ khoá trên thanh địa chỉ, cho phép micro rồi thử lại.
                  </p>
                ) : null}

                {recorder.state === 'idle' || recorder.state === 'denied' ? (
                  <div className="space-y-2">
                    <button
                      aria-label="Bắt đầu ghi âm"
                      className="mx-auto flex size-20 items-center justify-center rounded-full bg-brand-600 text-surface shadow-sm hover:bg-brand-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus"
                      onClick={() => void recorder.start()}
                      type="button"
                    >
                      <Mic aria-hidden="true" className="size-9" />
                    </button>
                    <p className="text-body-sm text-ink-secondary">Bấm để ghi âm, tối đa {MAX_RECORDING_SECONDS} giây</p>
                  </div>
                ) : null}

                {recorder.state === 'recording' ? (
                  <div className="space-y-2">
                    <button
                      aria-label="Dừng ghi âm"
                      className="mx-auto flex size-20 items-center justify-center rounded-full bg-danger text-surface shadow-sm motion-safe:animate-pulse focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus"
                      onClick={recorder.stop}
                      type="button"
                    >
                      <Square aria-hidden="true" className="size-7 fill-current" />
                    </button>
                    <p className="text-body font-semibold tabular-nums text-danger" role="timer">
                      Đang ghi {clock(recorder.seconds)} / {clock(MAX_RECORDING_SECONDS)}
                    </p>
                  </div>
                ) : null}

                {recorder.state === 'recorded' && recorder.previewUrl ? (
                  <div className="mx-auto max-w-md space-y-4">
                    <audio aria-label="Bản ghi của bạn" className="w-full" controls src={recorder.previewUrl} />
                    {sent ? (
                      <div className="space-y-3 rounded-md bg-success-bg p-4" role="status">
                        <p className="flex items-center justify-center gap-2 text-label text-success">
                          <CheckCircle2 aria-hidden="true" className="size-5" />
                          Đã gửi {sent.reference}. Bạn sẽ nhận thông báo khi giáo viên chấm xong.
                        </p>
                        <div className="flex flex-wrap justify-center gap-2">
                          <Button size="sm" to={`/grading/${sent.id}`} variant="outline">
                            Xem bài đã gửi
                          </Button>
                          <Button onClick={nextItem} size="sm">
                            Bài tiếp theo
                          </Button>
                        </div>
                      </div>
                    ) : guest ? (
                      <div className="space-y-3">
                        <div className="flex flex-wrap justify-center gap-3">
                          <Button onClick={recorder.reset} variant="outline">
                            <RotateCcw aria-hidden="true" className="size-4" />
                            Ghi lại
                          </Button>
                          <Button to="/register?redirect=%2Flearn%2Fpronunciation%2Fspeaking">
                            Đăng ký để gửi giáo viên chấm
                          </Button>
                        </div>
                        <p className="text-body-sm text-ink-secondary">Bản ghi học thử chỉ nằm trên máy bạn, không được tải lên.</p>
                      </div>
                    ) : (
                      <div className="flex flex-wrap justify-center gap-3">
                        <Button disabled={sending} onClick={recorder.reset} variant="outline">
                          <RotateCcw aria-hidden="true" className="size-4" />
                          Ghi lại
                        </Button>
                        <Button disabled={sending} loading={sending} onClick={() => void send()}>
                          <Send aria-hidden="true" className="size-4" />
                          {sending ? `Đang gửi ${Math.round(progress * 100)}%` : 'Gửi giáo viên chấm'}
                        </Button>
                      </div>
                    )}
                    {sendError ? (
                      <p className="text-body-sm text-danger" role="alert">
                        {sendError}
                      </p>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </Card>
          ) : null}
        </div>

        <aside className={cn('min-w-0 space-y-5 lg:col-span-4', guest && 'hidden')}>
          <Card className="min-w-0 space-y-3 border-brand-100 bg-brand-50 p-5">
            <h2 className="text-h3 text-ink">Cách luyện</h2>
            <ol className="list-decimal space-y-2 pl-5 text-body-sm text-ink-secondary">
              <li>Bấm Nghe mẫu, nghe kỹ thanh điệu.</li>
              <li>Bấm micro và đọc lại; nghe bản ghi, chưa ưng thì ghi lại.</li>
              <li>Gửi giáo viên chấm thanh điệu, thanh mẫu, vận mẫu và độ trôi chảy.</li>
            </ol>
          </Card>
          <Card className="min-w-0 space-y-2 p-5">
            <h2 className="text-label text-ink">Lượt gửi</h2>
            <p className="text-body-sm text-ink-secondary">
              Mỗi bài gửi dùng 1 lượt Phát âm của tháng. Hết lượt, mỗi bài trừ 1 điểm.
            </p>
            <Button size="sm" to="/grading" variant="outline">
              Bài nói đã gửi
            </Button>
          </Card>
        </aside>
      </div>
    </div>
  )
}
