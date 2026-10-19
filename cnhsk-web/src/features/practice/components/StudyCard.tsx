import type { ServedItemView } from '@/features/practice/types'
import { AudioButton, type AudioFailurePayload } from '@/features/practice/components/AudioButton'
import { Button, Card, HanziText } from '@/shared/ui'

export interface StudyCardProps {
  item: ServedItemView
  pending?: boolean
  onAudioFailed: (payload: AudioFailurePayload) => Promise<unknown> | unknown
  onContinue: () => Promise<unknown> | unknown
  onKnown: () => Promise<unknown> | unknown
  onReplayCountChange?: (replayCount: number) => void
}

export function StudyCard({
  item,
  pending = false,
  onAudioFailed,
  onContinue,
  onKnown,
  onReplayCountChange,
}: StudyCardProps) {
  const { prompt } = item

  return (
    <Card className="mx-auto max-w-reading p-5 sm:p-8">
      <div className="text-center">
        <HanziText className="block" size="lg">
          {prompt.text}
        </HanziText>
        {prompt.pinyin ? <p className="mt-2 text-h3 text-brand-700">{prompt.pinyin}</p> : null}
        {prompt.sinoViet?.trim() ? (
          <p className="mt-1 text-label uppercase text-ink-secondary">{prompt.sinoViet}</p>
        ) : null}
        {prompt.meaning ? <p className="mt-4 text-body text-ink">{prompt.meaning}</p> : null}
      </div>

      {prompt.audioUrl ? (
        <div className="mt-5 flex justify-center">
          <AudioButton
            audioUrl={prompt.audioUrl}
            disabled={pending}
            maxReplays={item.rules.maxReplays ?? 3}
            onAudioFailed={onAudioFailed}
            onReplayCountChange={onReplayCountChange}
          />
        </div>
      ) : null}

      {prompt.examples?.length ? (
        <section
          aria-labelledby={`study-examples-${item.no}`}
          className="mt-6 border-t border-line pt-5"
        >
          <h3 className="text-label text-ink" id={`study-examples-${item.no}`}>
            Ví dụ
          </h3>
          <div className="mt-3 space-y-4">
            {prompt.examples.map((example, index) => (
              <div key={`${example.cn ?? 'example'}-${index}`}>
                {example.cn ? <HanziText size="sm">{example.cn}</HanziText> : null}
                {example.pinyin ? (
                  <p className="mt-1 text-body-sm text-brand-700">{example.pinyin}</p>
                ) : null}
                {example.vi ? (
                  <p className="mt-1 text-body-sm text-ink-secondary">{example.vi}</p>
                ) : null}
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <div className="mt-6 grid gap-3 sm:grid-cols-2">
        <Button loading={pending} onClick={() => void onContinue()}>
          Tiếp
        </Button>
        <Button disabled={pending} onClick={() => void onKnown()} variant="outline">
          Tôi đã biết từ này
        </Button>
      </div>
    </Card>
  )
}
