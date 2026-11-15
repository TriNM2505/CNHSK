import { emptyForm, formFromExam } from '../examForm'
import { examFixture } from '../test-fixtures'
import type { ExamEditorViewProps } from './ExamEditorView'
const noop = () => undefined
const base: ExamEditorViewProps = {
  form: formFromExam(examFixture),
  saved: examFixture,
  dirty: false,
  busy: false,
  errors: [],
  publicationErrors: [],
  onChange: noop,
  onSave: noop,
  onPublish: noop,
  onArchive: noop,
  onClone: noop,
  onReload: noop,
  onBack: noop,
  onPicker: noop,
}
const legacy = {
  ...examFixture,
  sections: [{ name: 'Phần nhập từ tệp', type: null, durationMin: null }],
  publicationErrors: [
    {
      code: 'SECTION_TYPE_MISSING',
      message: 'Phần thi chưa có loại; cần bổ sung qua màn sửa đề.',
      sectionIndex: 0,
    },
  ],
}
export const editorPreviews: Record<string, ExamEditorViewProps> = {
  new: { ...base, form: emptyForm(), saved: null },
  draft: base,
  legacy: {
    ...base,
    form: formFromExam(legacy),
    saved: legacy,
    publicationErrors: legacy.publicationErrors,
  },
  readonly: { ...base, saved: { ...examFixture, status: 'PUBLISHED' } },
  archived: { ...base, saved: { ...examFixture, status: 'ARCHIVED' } },
  'publication-errors': {
    ...base,
    publicationErrors: [
      { code: 'EMPTY_SECTION', message: 'Phần thi chưa có câu hỏi.', sectionIndex: 0 },
      { code: 'QUESTION_NOT_APPROVED', message: 'Câu hỏi chưa được duyệt.', questionId: 901 },
      {
        code: 'LISTENING_AUDIO_MISSING',
        message: 'Câu hỏi phần nghe chưa có audio.',
        questionId: 901,
      },
      { code: 'SCORE_MISMATCH', message: 'Tổng điểm câu hỏi không khớp tổng điểm đề.' },
    ],
  },
  conflict: {
    ...base,
    dirty: true,
    locked: true,
    form: { ...base.form, title: 'Bản đang sửa được giữ nguyên khi có xung đột' },
    errors: ['Đề vừa được người khác sửa, tải lại'],
  },
}
