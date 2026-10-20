import { useState, type ReactNode } from 'react'

import {
  audioChoiceItemPreview,
  choiceItemPreview,
  choiceResultPreview,
  clozeItemPreview,
  finishResultPreview,
  pinyinInputPreview,
  sessionProgressPreview,
  studyItemWithAudioPreview,
  studyItemWithoutAudioPreview,
} from '@/features/practice/components/PracticeComponents.preview'
import {
  AudioButton,
  ChoiceQuestion,
  PinyinInput,
  SessionProgress,
  SessionSummary,
  StudyCard,
} from '@/features/practice/components'
import type { AnswerResult } from '@/features/practice/types'
import { Button } from '@/shared/ui'

function PracticeGallery({ children, title }: { children: ReactNode; title: string }) {
  return (
    <section aria-labelledby="practice-gallery-title" className="mx-auto max-w-content space-y-8">
      <h1 className="text-h2 text-ink" id="practice-gallery-title">
        {title}
      </h1>
      {children}
    </section>
  )
}

function Example({ children, title }: { children: ReactNode; title: string }) {
  return (
    <section className="space-y-3">
      <h2 className="text-h3 text-ink">{title}</h2>
      <div className="rounded-md border border-line bg-surface p-4 sm:p-6">{children}</div>
    </section>
  )
}

export function ChoiceQuestionGallery() {
  const [selected, setSelected] = useState<string | null>(null)
  const [result, setResult] = useState<AnswerResult | null>(null)

  return (
    <PracticeGallery title="ChoiceQuestion">
      <Example title="Phương án dài và phản hồi từ máy chủ">
        <ChoiceQuestion
          feedbackMode="IMMEDIATE"
          item={choiceItemPreview}
          onAnswer={(answer) => {
            setSelected(answer)
            setResult(choiceResultPreview(answer))
          }}
          result={result}
          selectedAnswer={selected}
        />
      </Example>
      <Example title="Câu điền chỗ trống">
        <ChoiceQuestion
          feedbackMode="DEFERRED"
          item={clozeItemPreview}
          onAnswer={() => undefined}
        />
      </Example>
    </PracticeGallery>
  )
}

export function PinyinInputGallery() {
  const [submitted, setSubmitted] = useState('Chưa gửi')
  return (
    <PracticeGallery title="PinyinInput">
      <Example title="Nhập pinyin bằng số thanh">
        <PinyinInput
          hint={pinyinInputPreview.hint}
          onSubmit={(answer) => setSubmitted(`Đã gửi: ${answer}`)}
          prompt={pinyinInputPreview.prompt}
        />
        <p className="mt-3 text-body-sm text-ink-secondary" role="status">
          {submitted}
        </p>
      </Example>
    </PracticeGallery>
  )
}

export function AudioButtonGallery() {
  const [replayCount, setReplayCount] = useState(0)
  return (
    <PracticeGallery title="AudioButton">
      <Example title="Tối đa ba lượt nghe">
        <AudioButton
          audioUrl={audioChoiceItemPreview.prompt.audioUrl ?? ''}
          maxReplays={audioChoiceItemPreview.rules.maxReplays ?? 3}
          onAudioFailed={() => undefined}
          onReplayCountChange={setReplayCount}
        />
        <p className="mt-3 text-body-sm text-ink-secondary">replayCount sẽ gửi: {replayCount}</p>
      </Example>
    </PracticeGallery>
  )
}

export function StudyCardGallery() {
  return (
    <PracticeGallery title="StudyCard">
      <Example title="Có âm thanh và ví dụ dài">
        <StudyCard
          item={studyItemWithAudioPreview}
          onAudioFailed={() => undefined}
          onContinue={() => undefined}
          onKnown={() => undefined}
        />
      </Example>
      <Example title="Không có âm thanh và âm Hán Việt">
        <StudyCard
          item={studyItemWithoutAudioPreview}
          onAudioFailed={() => undefined}
          onContinue={() => undefined}
          onKnown={() => undefined}
        />
      </Example>
    </PracticeGallery>
  )
}

export function SessionProgressGallery() {
  return (
    <PracticeGallery title="SessionProgress">
      <Example title="Tiến độ lượt luyện">
        <div className="max-w-reading space-y-4">
          {sessionProgressPreview.map(({ current, total }) => (
            <SessionProgress current={current} key={current} total={total} />
          ))}
        </div>
      </Example>
    </PracticeGallery>
  )
}

export function SessionSummaryGallery() {
  return (
    <PracticeGallery title="SessionSummary">
      <Example title="Điểm và câu sai do máy chủ trả về">
        <SessionSummary
          actions={
            <>
              <Button>Luyện lại câu sai</Button>
              <Button variant="outline">Về chủ đề</Button>
            </>
          }
          result={finishResultPreview}
        />
      </Example>
    </PracticeGallery>
  )
}
