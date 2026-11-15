import type { AdminContest, ContestWrite } from './types'

export type ContestDraft = {
  name: string
  description: string
  examId: string
  startsAt: string
  endsAt: string
  maxParticipants: string
  prizes: { fromRank: string; toRank: string; description: string }[]
}
export function vietnamInput(iso: string): string {
  const shifted = new Date(Date.parse(iso) + 7 * 60 * 60_000)
  return shifted.toISOString().slice(0, 16)
}
export function vietnamIso(input: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(input)) return null
  const iso = `${input}:00+07:00`
  const parsed = Date.parse(iso)
  return Number.isFinite(parsed) && vietnamInput(iso) === input ? iso : null
}
export function draftOf(contest: AdminContest | null): ContestDraft {
  return contest
    ? {
        name: contest.name,
        description: contest.description ?? '',
        examId: String(contest.examId ?? ''),
        startsAt: vietnamInput(contest.startsAt),
        endsAt: vietnamInput(contest.endsAt),
        maxParticipants: String(contest.maxParticipants ?? ''),
        prizes: contest.prizes.map((prize) => ({
          ...prize,
          fromRank: String(prize.fromRank),
          toRank: String(prize.toRank),
        })),
      }
    : {
        name: '',
        description: '',
        examId: '',
        startsAt: '',
        endsAt: '',
        maxParticipants: '',
        prizes: [],
      }
}
function positiveInteger(value: string): boolean {
  return /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) > 0
}
export function validateDraft(draft: ContestDraft): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!draft.name.trim() || draft.name.trim().length > 200)
    errors.name = 'Tên cuộc thi phải có 1–200 ký tự.'
  if (draft.description.length > 5000) errors.description = 'Mô tả tối đa 5.000 ký tự.'
  const start = vietnamIso(draft.startsAt)
  const end = vietnamIso(draft.endsAt)
  if (!start) errors.startsAt = 'Nhập thời gian bắt đầu hợp lệ theo giờ Việt Nam.'
  if (!end) errors.endsAt = 'Nhập thời gian kết thúc hợp lệ theo giờ Việt Nam.'
  if (start && end && Date.parse(start) >= Date.parse(end))
    errors.endsAt = 'Thời gian kết thúc phải sau thời gian bắt đầu.'
  if (draft.examId && !positiveInteger(draft.examId)) errors.examId = 'Chọn đề thi hợp lệ.'
  if (
    draft.maxParticipants &&
    (!positiveInteger(draft.maxParticipants) || Number(draft.maxParticipants) > 100000)
  )
    errors.maxParticipants = 'Số thí sinh tối đa từ 1 đến 100.000, hoặc để trống.'
  if (draft.prizes.length > 10) errors.prizes = 'Tối đa 10 giải thưởng.'
  draft.prizes.forEach((prize, index) => {
    if (
      !positiveInteger(prize.fromRank) ||
      !positiveInteger(prize.toRank) ||
      prize.description.length > 200
    )
      errors[`prizes.${index}`] = 'Hạng phải là số nguyên từ 1; mô tả tối đa 200 ký tự.'
  })
  return errors
}
export function writeOf(draft: ContestDraft): ContestWrite {
  return {
    name: draft.name.trim(),
    description: draft.description || null,
    examId: draft.examId ? Number(draft.examId) : null,
    startsAt: vietnamIso(draft.startsAt)!,
    endsAt: vietnamIso(draft.endsAt)!,
    maxParticipants: draft.maxParticipants ? Number(draft.maxParticipants) : null,
    prizes: draft.prizes.map((prize) => ({
      ...prize,
      fromRank: Number(prize.fromRank),
      toRank: Number(prize.toRank),
    })),
  }
}
