import { Button } from '@/shared/ui/Button'

export type AttemptQuestion = { no: number; text: string; options: { key: string; text: string }[] }
export type AttemptSection = { index: number; name: string; questions: AttemptQuestion[] }

type Props = {
  title: string
  sections: AttemptSection[]
  currentNo: number
  choices: Readonly<Record<number, string | null>>
  marks: readonly number[]
  saveState?: 'saving' | 'saved' | 'failed'
  placement: boolean
  pending: boolean
  readOnly: boolean
  confirming: boolean
  error?: string
  onNavigate: (no: number) => void
  onChoose: (key: string) => void
  onSkip: () => void
  onMark: () => void
  onConfirmChange: (open: boolean) => void
  onSubmit: () => void
  onContinue: () => void
  retryNext?: boolean
}

export function ExamAttemptView(props: Props) {
  const { sections, currentNo, choices, marks, placement, pending, readOnly } = props
  const questions = sections.flatMap((section) => section.questions)
  const index = questions.findIndex((question) => question.no === currentNo)
  const question = questions[index]
  const answered = questions.filter(({ no }) => choices[no] != null).length
  const remaining = questions.length - answered
  return (
    <main className="mx-auto max-w-content space-y-5 p-4 sm:p-6">
      <h1 className="text-h1-sm sm:text-h1">{props.title}</h1>
      <p>
        Đã làm {answered}/{questions.length} câu · Còn lại {remaining} câu
      </p>
      {props.error && (
        <p role="alert" className="text-danger">
          {props.error}
        </p>
      )}
      {!placement && (
        <nav aria-label="Các phần của đề" className="space-y-3">
          {sections.map((section) => (
            <div key={section.index} role="group" aria-label={section.name}>
              <h2 className="text-h3">{section.name}</h2>
              <div className="flex flex-wrap gap-2">
                {section.questions.map(({ no }) => (
                  <Button
                    key={no}
                    variant="outline"
                    aria-current={no === currentNo ? 'step' : undefined}
                    onClick={() => props.onNavigate(no)}
                  >
                    Câu {no}
                    {marks.includes(no) ? ' · Đã đánh dấu' : ''}
                  </Button>
                ))}
              </div>
            </div>
          ))}
        </nav>
      )}
      {question && (
        <section className="space-y-3 rounded-md border border-line bg-surface p-5">
          <h2 className="text-h3">Câu {question.no}</h2>
          <p>{question.text}</p>
          <div className="grid gap-2" role="group" aria-label="Phương án">
            {question.options.map(({ key, text }) => (
              <Button
                key={key}
                variant="outline"
                aria-pressed={choices[question.no] === key}
                disabled={pending || readOnly}
                onClick={() => props.onChoose(key)}
              >
                {key}. {text}
              </Button>
            ))}
          </div>
          {!placement && !readOnly && (
            <Button
              variant="outline"
              aria-pressed={marks.includes(question.no)}
              onClick={props.onMark}
            >
              Đánh dấu câu
            </Button>
          )}
          {!placement && props.saveState && (
            <p role="status">
              {props.saveState === 'saved'
                ? 'Đã lưu'
                : props.saveState === 'failed'
                  ? 'Chưa lưu được'
                  : 'Đang lưu…'}
            </p>
          )}
        </section>
      )}
      <div className="flex flex-wrap gap-3">
        {questions[index - 1] && (
          <Button variant="outline" onClick={() => props.onNavigate(questions[index - 1]!.no)}>
            Câu trước
          </Button>
        )}
        {questions[index + 1] && (
          <Button variant="outline" onClick={() => props.onNavigate(questions[index + 1]!.no)}>
            Câu tiếp theo
          </Button>
        )}
        {placement && !readOnly && (
          <Button variant="outline" disabled={pending || !question} onClick={props.onSkip}>
            Bỏ qua
          </Button>
        )}
        {readOnly ? (
          <Button disabled={pending} onClick={props.onContinue}>
            {placement ? (props.retryNext ? 'Thử lại' : 'Tiếp tục') : 'Xem kết quả'}
          </Button>
        ) : (
          <Button
            disabled={pending || !question}
            onClick={placement ? props.onSubmit : () => props.onConfirmChange(true)}
          >
            {placement ? 'Nộp khối' : 'Nộp bài'}
          </Button>
        )}
      </div>
      {!readOnly && props.confirming && (
        <section
          aria-label="Xác nhận nộp bài"
          className="space-y-3 rounded-md border border-line p-4"
        >
          <p>Còn {remaining} câu chưa trả lời. Bạn muốn nộp bài?</p>
          <Button variant="outline" disabled={pending} onClick={() => props.onConfirmChange(false)}>
            Quay lại làm bài
          </Button>
          <Button className="ml-3" disabled={pending} onClick={props.onSubmit}>
            Xác nhận nộp bài
          </Button>
        </section>
      )}
    </main>
  )
}
