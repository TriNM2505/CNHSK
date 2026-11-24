import type { GradingMode, RubricCode } from './types'

export type RubricCriterion = {
  code: string
  label: string
  /** What a score looks like; R-NÓI describes every level, R-VIẾT only the anchors 1, 3 and 5. */
  levels: Readonly<Record<number, string>>
}

export type RubricDefinition = {
  code: RubricCode
  label: string
  max: number
  note?: string
  criteria: readonly RubricCriterion[]
}

/** Speaking rubric of the competency tree v2.0 (appendix E.1), scale 0–3. */
export const SPEAKING_RUBRIC: RubricDefinition = {
  code: 'R_NOI',
  label: 'R-NÓI',
  max: 3,
  criteria: [
    {
      code: 'C1',
      label: 'Hoàn thành nhiệm vụ',
      levels: {
        0: 'Không nói, hoặc lạc đề hoàn toàn',
        1: 'Nói được 1–2 ý liên quan, thiếu phần chính của nhiệm vụ',
        2: 'Đủ ý chính, có trình tự; thiếu chi tiết thời gian, nơi chốn',
        3: 'Đủ ý, có trình tự, có chi tiết; người nghe hiểu ngay',
      },
    },
    {
      code: 'C2',
      label: 'Phát âm, thanh điệu',
      levels: {
        0: 'Phần lớn không nghe hiểu được',
        1: 'Sai thanh thường xuyên, người nghe phải đoán',
        2: 'Sai thanh rải rác, không ảnh hưởng hiểu; biến điệu đôi chỗ chưa đúng',
        3: 'Thanh điệu và biến điệu gần như chính xác',
      },
    },
    {
      code: 'C3',
      label: 'Từ vựng',
      levels: {
        0: 'Chỉ dùng từ rời, không thành ý',
        1: 'Từ cấp 1, lặp nhiều, thiếu từ chủ đề của cấp',
        2: 'Dùng đúng phần lớn từ chủ đề',
        3: 'Từ chủ đề đúng và đa dạng, có từ cấp trên phù hợp',
      },
    },
    {
      code: 'C4',
      label: 'Ngữ pháp',
      levels: {
        0: 'Không thành câu',
        1: 'Câu đơn, sai trật tự hoặc sai cấu trúc mục tiêu thường xuyên',
        2: 'Cấu trúc mục tiêu đúng phần lớn; lỗi không gây hiểu sai',
        3: 'Câu đa dạng, cấu trúc mục tiêu đúng, lỗi không đáng kể',
      },
    },
    {
      code: 'C5',
      label: 'Trôi chảy, liên kết',
      levels: {
        0: 'Ngắt quãng liên tục',
        1: 'Dừng lâu giữa các câu, không có từ nối',
        2: 'Dừng ngắn, tự sửa được; có từ nối cơ bản',
        3: 'Liền mạch, tốc độ tự nhiên, từ nối phù hợp',
      },
    },
  ],
}

/** Writing rubric of the competency tree v2.0 (appendix E.2), scale 0–5 with anchors at 1, 3 and 5. */
export const WRITING_RUBRIC: RubricDefinition = {
  code: 'R_VIET',
  label: 'R-VIẾT',
  max: 5,
  note: 'Mức 0: không viết hoặc lạc đề hoàn toàn. Mức 2 và 4 dùng khi bài nằm giữa hai mức neo.',
  criteria: [
    {
      code: 'C1',
      label: 'Hoàn thành nhiệm vụ',
      levels: {
        1: 'Dưới một nửa độ dài yêu cầu, hoặc chỉ một phần nhỏ đúng đề',
        3: 'Đủ ý chính, gần đủ độ dài, trình tự chưa rõ',
        5: 'Đủ ý, đủ độ dài, trình tự rõ ràng',
      },
    },
    {
      code: 'C2',
      label: 'Ngữ pháp',
      levels: {
        1: 'Phần lớn câu sai cấu trúc',
        3: 'Cấu trúc mục tiêu đúng khoảng một nửa',
        5: 'Cấu trúc mục tiêu đúng; lỗi nhỏ không ảnh hưởng nghĩa',
      },
    },
    {
      code: 'C3',
      label: 'Từ vựng',
      levels: {
        1: 'Lặp từ cấp 1, thiếu từ chủ đề',
        3: 'Từ chủ đề đúng, đôi chỗ sai nghĩa, sai kết hợp',
        5: 'Từ chủ đề đúng, đa dạng, kết hợp tự nhiên',
      },
    },
    {
      code: 'C4',
      label: 'Chữ Hán, trình bày',
      levels: {
        1: 'Nhiều chữ sai hoặc viết pinyin thay chữ',
        3: 'Sai 3–5 chữ, dấu câu chưa đúng',
        5: 'Sai không quá 1 chữ, dấu câu đúng',
      },
    },
  ],
}

export function rubricForMode(mode: GradingMode | undefined): RubricDefinition {
  return mode === 'SPEAKING' ? SPEAKING_RUBRIC : WRITING_RUBRIC
}

export function rubricByCode(code: RubricCode): RubricDefinition {
  return code === 'R_NOI' ? SPEAKING_RUBRIC : WRITING_RUBRIC
}

/** The description of the anchor at or just below a score, for the level actually given. */
export function levelText(criterion: RubricCriterion, score: number): string | undefined {
  for (let value = score; value >= 0; value -= 1) {
    const text = criterion.levels[value]
    if (text) return text
  }
  return undefined
}

/** Severity names of appendix D: 1 is feedback only, 2 and 3 count against the node. */
export const SEVERITY_LABELS: Readonly<Record<number, string>> = { 1: 'Nhẹ', 2: 'Vừa', 3: 'Nặng' }
