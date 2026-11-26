/** Menu sections a mistake can be filed under, in menu order; the server names the section of each mistake. */
export const MISTAKE_SECTIONS = [
  { key: 'LISTENING', label: 'Nghe' },
  { key: 'READING', label: 'Đọc' },
  { key: 'WRITING', label: 'Viết' },
  { key: 'TOPIC', label: 'Chủ đề' },
  { key: 'GRAMMAR', label: 'Ngữ pháp' },
  { key: 'CHARACTER', label: 'Chữ Hán' },
  { key: 'REVIEW', label: 'Ôn bài' },
  { key: 'PLACEMENT', label: 'Kiểm tra đầu vào' },
  { key: 'MOCK_EXAM', label: 'Thi thử' },
  { key: 'GAME', label: 'Trò chơi' },
  { key: 'TARGETED', label: 'Luyện lỗi trước đây' },
] as const

export type MistakeSection = (typeof MISTAKE_SECTIONS)[number]['key']

export function mistakeSectionLabel(key: string | null | undefined): string | undefined {
  return MISTAKE_SECTIONS.find((section) => section.key === key)?.label
}
