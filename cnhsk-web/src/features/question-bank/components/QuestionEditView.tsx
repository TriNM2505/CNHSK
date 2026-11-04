import { useEffect, useRef, useState } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Select,
  Skeleton,
  SUPPORTED_HSK_LEVELS,
  Textarea,
  type PageStateValue,
} from '@/shared/ui'

import {
  limitQuestionInput,
  questionEditErrorMessage,
  questionEditServerErrors,
  questionEditValues,
  toQuestionUpdate,
  validateQuestionEdit,
  type QuestionEditErrors,
} from '../questionEditForm'
import {
  QUESTION_SOURCE_LABELS,
  QUESTION_STATUS_LABELS,
  QUESTION_TYPE_LABELS,
} from '../questionListQuery'
import type { KnowledgeNode, QuestionDetail, QuestionUpdate } from '../types'

export type QuestionEditViewProps = {
  action?: 'save' | 'archive'
  actionError?: ApiError
  backTo: string
  canArchive: boolean
  canEdit: boolean
  data?: QuestionDetail
  error?: ApiError
  nodes: readonly KnowledgeNode[]
  nodesError?: ApiError
  nodesState: PageStateValue
  notice?: string
  onArchive: () => void
  onCancelAction: () => void
  onConfirmAction: () => void
  onDismissError: () => void
  onNodesRetry: () => void
  onRetry: () => void
  onSave: (body: QuestionUpdate) => void
  pending: boolean
  state: PageStateValue
}

function QuestionContent({ data }: { data: QuestionDetail }) {
  return (
    <section
      aria-label="Nội dung và đáp án"
      className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
    >
      <h2 className="text-h3">Nội dung và đáp án</h2>
      <p className="whitespace-pre-wrap break-words">{data.stem}</p>
      <ul className="space-y-3">
        {data.options.map((option) => (
          <li className="rounded-sm border border-line p-3" key={option.key}>
            <p className="whitespace-pre-wrap break-words">
              {option.key}. {option.text} {option.isCorrect ? <strong>— Đáp án đúng</strong> : null}
            </p>
            {option.errorKid ? (
              <p className="mt-2 text-body-sm">Nút lỗi: {option.errorKid}</p>
            ) : null}
            {option.errorNote ? (
              <p className="text-body-sm">Ghi chú lỗi: {option.errorNote}</p>
            ) : null}
          </li>
        ))}
      </ul>
      {data.correctKey ? (
        <p>
          <strong>{data.qtype === 'SENTENCE_ORDER' ? 'Thứ tự đúng' : 'Đáp án đúng'}:</strong>{' '}
          {data.correctKey}
        </p>
      ) : null}
      <div>
        <h3 className="font-semibold">Lời giải</h3>
        <p className="whitespace-pre-wrap break-words">{data.explanation || 'Chưa có lời giải.'}</p>
      </div>
      <div>
        <h3 className="font-semibold">Nhãn năng lực</h3>
        {data.labels.length ? (
          <ul>
            {data.labels.map(({ kid, nameVi, role }) => (
              <li key={kid}>
                {role === 'PRIMARY' ? 'Chính' : 'Phụ'}: {kid} · {nameVi}
              </li>
            ))}
          </ul>
        ) : (
          <p>Chưa gắn nút năng lực.</p>
        )}
      </div>
    </section>
  )
}

export function QuestionEditView({
  action,
  actionError,
  backTo,
  canArchive,
  canEdit,
  data,
  error,
  nodes,
  nodesError,
  nodesState,
  notice,
  onArchive,
  onCancelAction,
  onConfirmAction,
  onDismissError,
  onNodesRetry,
  onRetry,
  onSave,
  pending,
  state,
}: QuestionEditViewProps) {
  const [values, setValues] = useState<QuestionUpdate>(() =>
    data
      ? questionEditValues(data)
      : {
          stem: '',
          options: [],
          explanation: '',
          hskLevel: 1,
          difficulty: 2,
          primaryKid: '',
          secondaryKids: [],
        },
  )
  const [localErrors, setLocalErrors] = useState<QuestionEditErrors>({})
  const formRef = useRef<HTMLFormElement>(null)
  const confirmRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (data) setValues(questionEditValues(data))
    setLocalErrors({})
  }, [data])
  useEffect(() => {
    if (!action) return
    const trigger = document.activeElement
    confirmRef.current?.focus()
    return () => {
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus()
    }
  }, [action])
  const serverErrors = questionEditServerErrors(actionError?.details)
  const errors = { ...localErrors, ...serverErrors }
  const change = <K extends keyof QuestionUpdate>(field: K, value: QuestionUpdate[K]) => {
    setValues((current) => ({ ...current, [field]: value }))
    setLocalErrors({})
    onDismissError()
  }
  const nodeOptions = (selected: string) => (
    <>
      <option value="">Chưa chọn nút</option>
      {selected && !nodes.some(({ kid }) => kid === selected) ? (
        <option value={selected}>{selected} (chưa có trong cây)</option>
      ) : null}
      {nodes.map(({ kid, nameVi }) => (
        <option key={kid} value={kid}>
          {kid} · {nameVi}
        </option>
      ))}
    </>
  )
  const optionChange = (index: number, patch: Partial<QuestionUpdate['options'][number]>) =>
    change(
      'options',
      values.options.map((option, at) => (at === index ? { ...option, ...patch } : option)),
    )
  const blocked = pending || Boolean(action)
  const ready = state === 'ready' && data !== undefined
  const editable = canEdit && data?.status !== 'ARCHIVED' && data?.qtype !== 'ESSAY'
  const choice = data && ['MULTIPLE_CHOICE', 'IMAGE_MATCH', 'SENTENCE_MATCH'].includes(data.qtype)
  const visibleFields = new Set<string>()
  if (editable) {
    for (const field of [
      'stem',
      'hskLevel',
      'difficulty',
      'explanation',
      'audioUrl',
      'imageUrl',
      'primaryKid',
      'secondaryKids',
    ])
      visibleFields.add(field)
    if (data?.qtype === 'FILL_BLANK' || data?.qtype === 'SENTENCE_ORDER')
      visibleFields.add('correctKey')
    if (data?.qtype !== 'FILL_BLANK') {
      visibleFields.add('options')
      for (const [index, option] of values.options.entries()) {
        visibleFields.add(`options[${index}].text`)
        if (choice && !option.isCorrect) {
          visibleFields.add(`options[${index}].errorKid`)
          visibleFields.add(`options[${index}].errorNote`)
        }
      }
    }
  }
  const generalErrors = Object.entries(serverErrors).filter(([field]) => !visibleFields.has(field))

  return (
    <div className="mx-auto max-w-reading space-y-6">
      <Button to={backTo} variant="ghost">
        Về kho câu hỏi
      </Button>
      <PageHeader
        title="Chi tiết câu hỏi"
        subtitle="Xem nội dung, đáp án và nhãn năng lực của câu hỏi."
      />
      {state === 'loading' ? (
        <div aria-label="Đang tải câu hỏi" className="space-y-3" role="status">
          <Skeleton className="h-20" />
          <Skeleton className="h-64" />
        </div>
      ) : null}
      {state === 'empty' ? (
        <EmptyState
          title="Không tìm thấy câu hỏi"
          description="Câu hỏi không có trong kho. Vui lòng quay lại danh sách."
        />
      ) : null}
      {state === 'error' ? (
        <ErrorState
          title="Không thể tải câu hỏi"
          message={error?.message ?? 'Vui lòng thử lại.'}
          requestId={error?.requestId}
          onRetry={onRetry}
        />
      ) : null}
      {ready ? (
        <>
          <section
            aria-label="Thông tin câu hỏi"
            className="rounded-md border border-line bg-surface p-4 sm:p-6"
          >
            <dl className="grid gap-3 text-body-sm sm:grid-cols-2">
              <div>
                <dt className="font-semibold">Mã câu hỏi</dt>
                <dd>{data.code ?? `#${data.id}`}</dd>
              </div>
              <div>
                <dt className="font-semibold">Loại câu hỏi</dt>
                <dd>{QUESTION_TYPE_LABELS[data.qtype]}</dd>
              </div>
              <div>
                <dt className="font-semibold">Nguồn</dt>
                <dd>{QUESTION_SOURCE_LABELS[data.source]}</dd>
              </div>
              <div>
                <dt className="font-semibold">Trạng thái</dt>
                <dd>{QUESTION_STATUS_LABELS[data.status]}</dd>
              </div>
              <div>
                <dt className="font-semibold">Cấp HSK hiện tại</dt>
                <dd>HSK {data.hskLevel}</dd>
              </div>
              <div>
                <dt className="font-semibold">Độ khó hiện tại</dt>
                <dd>{data.difficulty}</dd>
              </div>
              <div>
                <dt className="font-semibold">Sử dụng</dt>
                <dd>{data.used ? 'Đã dùng' : 'Chưa dùng'}</dd>
              </div>
            </dl>
            {data.reviewReason ? (
              <p className="mt-3">Lý do kiểm duyệt: {data.reviewReason}</p>
            ) : null}
            {data.imageUrl && /^https?:\/\//i.test(data.imageUrl) ? (
              <a
                className="mt-3 block underline"
                href={data.imageUrl}
                rel="noreferrer"
                target="_blank"
              >
                Xem ảnh câu hỏi
              </a>
            ) : null}
            {data.audioUrl && /^https?:\/\//i.test(data.audioUrl) ? (
              <audio
                aria-label="Âm thanh câu hỏi"
                className="mt-3 max-w-full"
                controls
                preload="none"
                src={data.audioUrl}
              />
            ) : null}
          </section>
          {data.used ? (
            <p className="rounded-md border border-line bg-canvas p-4" role="note">
              Câu đã có người làm — không đổi được đáp án; tạo câu mới thay thế
            </p>
          ) : null}
          {data.status === 'ARCHIVED' ? (
            <p role="note">Câu đã lưu trữ. Chỉ có thể xem nội dung.</p>
          ) : null}
          {data.qtype === 'ESSAY' ? (
            <p role="note">
              Chưa hỗ trợ sửa câu tự luận. Bạn có thể xem nội dung và đáp án hiện có.
            </p>
          ) : null}
          {notice ? (
            <p className="font-semibold text-success" role="status">
              {notice}
            </p>
          ) : null}
          {actionError ? (
            <section aria-label="Lỗi thao tác" className="space-y-2" role="alert">
              <p>{questionEditErrorMessage(actionError)}</p>
              {actionError.requestId ? (
                <p className="text-body-sm">Mã yêu cầu: {actionError.requestId}</p>
              ) : null}
              {actionError.errorCode === 'GRADING_CHANGE_NOT_ALLOWED' ||
              actionError.errorCode === 'QUESTION_ARCHIVED' ? (
                <Button disabled={pending} onClick={onRetry} variant="outline">
                  Tải lại câu hỏi
                </Button>
              ) : null}
              {generalErrors.map(([field, message]) => (
                <p className="whitespace-pre-wrap" key={field}>
                  {message}
                </p>
              ))}
            </section>
          ) : null}
          {editable ? (
            <form
              aria-label="Sửa câu hỏi"
              className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
              noValidate
              ref={formRef}
              onSubmit={(event) => {
                event.preventDefault()
                if (blocked || nodesState !== 'ready' || nodes.length === 0) return
                const validation = validateQuestionEdit(values, data.qtype, nodes)
                setLocalErrors(validation)
                if (Object.keys(validation).length) {
                  requestAnimationFrame(() =>
                    formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
                  )
                  return
                }
                onSave(toQuestionUpdate(values))
              }}
            >
              <h2 className="text-h3">Sửa câu hỏi</h2>
              <fieldset disabled={blocked}>
                <legend className="sr-only">Nội dung và nhãn câu hỏi</legend>
                <FormField
                  htmlFor="edit-stem"
                  label="Nội dung câu hỏi"
                  required
                  error={errors.stem}
                >
                  <Textarea
                    value={values.stem}
                    onChange={(event) =>
                      change('stem', limitQuestionInput(event.target.value, 2000))
                    }
                  />
                </FormField>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField htmlFor="edit-level" label="Cấp HSK" required error={errors.hskLevel}>
                    <Select
                      value={values.hskLevel}
                      onChange={(event) => change('hskLevel', Number(event.target.value))}
                    >
                      {!SUPPORTED_HSK_LEVELS.some((level) => level === values.hskLevel) ? (
                        <option disabled value={values.hskLevel}>
                          HSK {values.hskLevel} (ngoài phạm vi nội dung)
                        </option>
                      ) : null}
                      {SUPPORTED_HSK_LEVELS.map((level) => (
                        <option key={level} value={level}>
                          HSK {level}
                        </option>
                      ))}
                    </Select>
                  </FormField>
                  <FormField
                    htmlFor="edit-difficulty"
                    label="Độ khó"
                    required
                    error={errors.difficulty}
                  >
                    <Select
                      value={values.difficulty}
                      onChange={(event) => change('difficulty', Number(event.target.value))}
                    >
                      {[1, 2, 3].map((value) => (
                        <option key={value} value={value}>
                          {value}
                        </option>
                      ))}
                    </Select>
                  </FormField>
                </div>
                {data.qtype !== 'FILL_BLANK' ? (
                  <section aria-label="Phương án trả lời" className="mb-4 space-y-3">
                    <h3 className="font-semibold">Phương án trả lời</h3>
                    {errors.options ? (
                      <p className="text-danger" role="alert">
                        {errors.options}
                      </p>
                    ) : null}
                    {values.options.map((option, index) => (
                      <div className="rounded-sm border border-line p-3" key={option.key}>
                        <FormField
                          htmlFor={`edit-option-${option.key}`}
                          label={`Phương án ${option.key}`}
                          required
                          error={errors[`options[${index}].text`]}
                        >
                          <Input
                            disabled={data.qtype === 'TRUE_FALSE'}
                            value={option.text}
                            onChange={(event) =>
                              optionChange(index, {
                                text: limitQuestionInput(event.target.value, 300),
                              })
                            }
                          />
                        </FormField>
                        {data.qtype !== 'SENTENCE_ORDER' ? (
                          <label className="mb-3 flex min-h-touch items-center gap-2">
                            <input
                              checked={option.isCorrect}
                              disabled={data.used}
                              name="correct-option"
                              type="radio"
                              onChange={() =>
                                change(
                                  'options',
                                  values.options.map((entry, at) => ({
                                    ...entry,
                                    isCorrect: at === index,
                                    ...(at === index ? { errorKid: null, errorNote: null } : {}),
                                  })),
                                )
                              }
                            />
                            {`Đáp án đúng ${option.key}`}
                          </label>
                        ) : null}
                        {choice && !option.isCorrect ? (
                          <div className="grid gap-3 sm:grid-cols-2">
                            <FormField
                              htmlFor={`edit-error-kid-${option.key}`}
                              label={`Nút lỗi ${option.key}`}
                              error={errors[`options[${index}].errorKid`]}
                            >
                              <Select
                                disabled={nodesState !== 'ready'}
                                value={option.errorKid ?? ''}
                                onChange={(event) =>
                                  optionChange(index, {
                                    errorKid: event.target.value || null,
                                    ...(event.target.value ? {} : { errorNote: null }),
                                  })
                                }
                              >
                                {nodeOptions(option.errorKid ?? '')}
                              </Select>
                            </FormField>
                            <FormField
                              htmlFor={`edit-error-note-${option.key}`}
                              label={`Ghi chú lỗi ${option.key}`}
                              error={errors[`options[${index}].errorNote`]}
                            >
                              <Input
                                disabled={!option.errorKid}
                                value={option.errorNote ?? ''}
                                onChange={(event) =>
                                  optionChange(index, {
                                    errorNote: limitQuestionInput(event.target.value, 200),
                                  })
                                }
                              />
                            </FormField>
                          </div>
                        ) : null}
                        {data.qtype !== 'TRUE_FALSE' ? (
                          <Button
                            disabled={
                              data.used ||
                              values.options.length <= (data.qtype === 'SENTENCE_ORDER' ? 3 : 2)
                            }
                            onClick={() =>
                              change(
                                'options',
                                values.options
                                  .filter((_, at) => at !== index)
                                  .map((entry, at) => ({ ...entry, key: 'ABCDEF'[at]! })),
                              )
                            }
                            variant="ghost"
                          >
                            Xoá phương án {option.key}
                          </Button>
                        ) : null}
                      </div>
                    ))}
                    {data.qtype !== 'TRUE_FALSE' ? (
                      <Button
                        disabled={data.used || values.options.length >= 6}
                        onClick={() =>
                          change('options', [
                            ...values.options,
                            { key: 'ABCDEF'[values.options.length]!, text: '', isCorrect: false },
                          ])
                        }
                        variant="outline"
                      >
                        Thêm phương án
                      </Button>
                    ) : null}
                  </section>
                ) : null}
                {data.qtype === 'FILL_BLANK' || data.qtype === 'SENTENCE_ORDER' ? (
                  <FormField
                    htmlFor="edit-correct-key"
                    label={data.qtype === 'SENTENCE_ORDER' ? 'Thứ tự đúng' : 'Đáp án đúng'}
                    required
                    error={errors.correctKey}
                  >
                    <Input
                      disabled={data.used}
                      value={values.correctKey ?? ''}
                      onChange={(event) =>
                        change('correctKey', limitQuestionInput(event.target.value, 50))
                      }
                    />
                  </FormField>
                ) : null}
                <FormField
                  htmlFor="edit-explanation"
                  label="Lời giải"
                  required
                  error={errors.explanation}
                >
                  <Textarea
                    value={values.explanation}
                    onChange={(event) =>
                      change('explanation', limitQuestionInput(event.target.value, 3000))
                    }
                  />
                </FormField>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField
                    htmlFor="edit-audio"
                    label="Đường dẫn âm thanh"
                    error={errors.audioUrl}
                  >
                    <Input
                      value={values.audioUrl ?? ''}
                      onChange={(event) =>
                        change('audioUrl', limitQuestionInput(event.target.value, 500))
                      }
                    />
                  </FormField>
                  <FormField htmlFor="edit-image" label="Đường dẫn ảnh" error={errors.imageUrl}>
                    <Input
                      value={values.imageUrl ?? ''}
                      onChange={(event) =>
                        change('imageUrl', limitQuestionInput(event.target.value, 500))
                      }
                    />
                  </FormField>
                </div>
                {nodesState === 'loading' ? <p role="status">Đang tải nút năng lực…</p> : null}
                {nodesState === 'error' ? (
                  <ErrorState
                    title="Không thể tải nút năng lực"
                    message={nodesError?.message ?? 'Vui lòng thử lại.'}
                    onRetry={onNodesRetry}
                    requestId={nodesError?.requestId}
                  />
                ) : null}
                {nodesState === 'ready' && nodes.length === 0 ? (
                  <p role="note">Chưa có nút năng lực. Cần nạp cây trước khi lưu câu hỏi.</p>
                ) : null}
                <FormField
                  htmlFor="edit-primary"
                  label="Nút năng lực chính"
                  required
                  error={errors.primaryKid}
                >
                  <Select
                    disabled={nodesState !== 'ready'}
                    value={values.primaryKid}
                    onChange={(event) => change('primaryKid', event.target.value)}
                  >
                    {nodeOptions(values.primaryKid)}
                  </Select>
                </FormField>
                <div className="grid gap-4 sm:grid-cols-2">
                  {[0, 1].map((index) => (
                    <FormField
                      htmlFor={`edit-secondary-${index}`}
                      key={index}
                      label={`Nút năng lực phụ ${index + 1}`}
                      error={index === 0 ? errors.secondaryKids : undefined}
                    >
                      <Select
                        disabled={nodesState !== 'ready'}
                        value={values.secondaryKids[index] ?? ''}
                        onChange={(event) => {
                          const next = [...values.secondaryKids]
                          next[index] = event.target.value
                          change('secondaryKids', next.filter(Boolean))
                        }}
                      >
                        {nodeOptions(values.secondaryKids[index] ?? '')}
                      </Select>
                    </FormField>
                  ))}
                </div>
              </fieldset>
              <Button
                disabled={blocked || nodesState !== 'ready' || nodes.length === 0}
                type="submit"
              >
                Lưu thay đổi
              </Button>
            </form>
          ) : (
            <QuestionContent data={data} />
          )}
          {canArchive && data.status !== 'ARCHIVED' ? (
            <Button disabled={blocked} onClick={onArchive} variant="danger">
              Lưu trữ câu hỏi
            </Button>
          ) : null}
          {action ? (
            <section
              aria-label={action === 'save' ? 'Xác nhận lưu thay đổi' : 'Xác nhận lưu trữ'}
              className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
            >
              <h2 className="text-h3">
                {action === 'save' ? 'Lưu thay đổi câu hỏi?' : 'Lưu trữ câu hỏi?'}
              </h2>
              <p>
                {action === 'save'
                  ? 'Lưu nội dung và nhãn mới. Trạng thái duyệt của câu hỏi được giữ nguyên.'
                  : 'Câu hỏi sẽ ngừng được sử dụng cho lượt mới. Lịch sử bài làm được giữ nguyên.'}
              </p>
              <div className="flex flex-wrap gap-3">
                <button
                  className="min-h-control rounded-sm border border-line px-4 text-label focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:opacity-50"
                  disabled={pending}
                  onClick={onCancelAction}
                  ref={confirmRef}
                  type="button"
                >
                  Huỷ
                </button>
                <Button
                  loading={pending}
                  onClick={onConfirmAction}
                  variant={action === 'archive' ? 'danger' : 'primary'}
                >
                  {action === 'save' ? 'Xác nhận lưu thay đổi' : 'Xác nhận lưu trữ'}
                </Button>
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  )
}
