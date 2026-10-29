/**
 * Pinyin normalisation for CNHSK Arcade (Phi Dao).
 *
 * Pure module: no DOM, no network. Everything here is unit-tested.
 *
 * Rules (agreed for the demo):
 *  - A target pinyin is stored WITH tone marks, e.g. "nǐ hǎo" or "xuéxí".
 *  - `pinyinKey` = toNumbered(pinyin), no spaces, e.g. "ni3hao3" (spec 007 FR-021).
 *  - A player may answer in three equivalent ways:
 *      1. tone marks   "nǐhǎo"
 *      2. tone numbers "ni3hao3" / "ni3 hao3"
 *      3. no tones     "nihao"
 *  - If the player gives tones at all they must give one for EVERY syllable.
 *    A partial attempt ("ni3hao") is rejected with verdict 'partial-tones' so
 *    the UI can explain instead of silently accepting a wrong tone.
 *  - "ü" is accepted as "u" or "v" (both normalise to "u"), which makes lǜ/lù
 *    ambiguous — a known demo limitation, listed in BACKEND_DEPENDENCIES.
 *
 * Word data is not guaranteed to separate syllables with spaces, so both the
 * target and the attempt are segmented with the same greedy longest-match rule
 * over the valid Mandarin syllable table below. That keeps "xuéxí" and
 * "xué xí" equivalent.
 */

/** Toned vowel -> [base letter, tone number]. */
const TONED_VOWELS: Record<string, [string, number]> = {
  ā: ['a', 1], á: ['a', 2], ǎ: ['a', 3], à: ['a', 4],
  ē: ['e', 1], é: ['e', 2], ě: ['e', 3], è: ['e', 4],
  ī: ['i', 1], í: ['i', 2], ǐ: ['i', 3], ì: ['i', 4],
  ō: ['o', 1], ó: ['o', 2], ǒ: ['o', 3], ò: ['o', 4],
  ū: ['u', 1], ú: ['u', 2], ǔ: ['u', 3], ù: ['u', 4],
  ǖ: ['u', 1], ǘ: ['u', 2], ǚ: ['u', 3], ǜ: ['u', 4],
  ü: ['u', 0],
}

/** Longest first — greedy matching depends on this order. */
const INITIALS = [
  'zh', 'ch', 'sh',
  'b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k', 'h',
  'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w', '',
]

const FINALS = [
  'iang', 'iong', 'uang', 'ueng', 'ian', 'iao', 'ing', 'uai', 'uan',
  'ang', 'eng', 'ong', 'ai', 'ei', 'ao', 'ou', 'an', 'en', 'er',
  'ia', 'ie', 'iu', 'in', 'ua', 'uo', 'ue', 'ui', 'un', 'ong',
  'a', 'e', 'i', 'o', 'u', 'v', 'n', 'ng',
]

const MAX_SYLLABLE = 6

/** All plausible Mandarin syllables, used only for segmentation. */
const SYLLABLES: Set<string> = (() => {
  const set = new Set<string>()
  for (const initial of INITIALS) {
    for (const final of FINALS) set.add(initial + final)
  }
  return set
})()

function baseOf(ch: string): string | null {
  const lower = ch.toLowerCase()
  const hit = TONED_VOWELS[lower]
  if (hit) return hit[0]
  if (lower >= 'a' && lower <= 'z') return lower === 'v' ? 'u' : lower
  return null
}

/** Lowercase letters only, tone marks folded to base letters, v -> u, ü -> u. */
export function lettersOf(input: string): string {
  let out = ''
  for (const ch of input) {
    const b = baseOf(ch)
    if (b) out += b
  }
  return out
}

/** Split a bare letter string ("nihao") into syllables ("ni" + "hao"). */
export function segmentLetters(letters: string): string[] {
  const out: string[] = []
  let i = 0
  while (i < letters.length) {
    let matched = ''
    for (let len = Math.min(MAX_SYLLABLE, letters.length - i); len >= 1; len -= 1) {
      const candidate = letters.slice(i, i + len)
      if (SYLLABLES.has(candidate)) {
        matched = candidate
        break
      }
    }
    if (matched === '') matched = letters[i]
    out.push(matched)
    i += matched.length
  }
  return out
}

export interface PinyinSyllable {
  text: string
  /** null = the speaker did not say which tone. */
  tone: number | null
}

/** Split on the separators the data set actually uses. */
function chunksOf(pinyin: string): string[] {
  return pinyin.trim().split(/[\s'’·-]+/).filter(Boolean)
}

/**
 * Read tone marks and attach each one to the syllable that contains its vowel.
 * Works whether or not the data separates syllables with spaces.
 */
export function syllableTones(pinyin: string): PinyinSyllable[] {
  const out: PinyinSyllable[] = []
  for (const chunk of chunksOf(pinyin)) {
    const letters = lettersOf(chunk)
    const segments = segmentLetters(letters)
    const tones: (number | null)[] = segments.map(() => null)

    let letterIndex = 0
    for (const ch of chunk) {
      const base = baseOf(ch)
      if (!base) continue
      // which segment does this letter belong to?
      let cursor = 0
      let segmentIndex = segments.length - 1
      for (let s = 0; s < segments.length; s += 1) {
        if (letterIndex < cursor + segments[s].length) {
          segmentIndex = s
          break
        }
        cursor += segments[s].length
      }
      const marked = TONED_VOWELS[ch.toLowerCase()]
      if (marked && marked[1] > 0) tones[segmentIndex] = marked[1]
      letterIndex += 1
    }

    segments.forEach((text, index) => out.push({ text, tone: tones[index] }))
  }
  return out
}

/**
 * Read what the player typed. Tone marks and tone digits are both understood;
 * a digit closes the syllable it follows.
 */
export function parseAttempt(raw: string): PinyinSyllable[] {
  const marked = syllableTones(raw)
  if (marked.some((s) => s.tone !== null)) return marked

  // No tone marks: fall back to digits, which close the syllable before them.
  const out: PinyinSyllable[] = []
  let letters = ''
  let pendingTone: number | null = null

  const flush = () => {
    if (letters === '') return
    const segments = segmentLetters(letters)
    segments.forEach((text, index) => {
      const isLast = index === segments.length - 1
      out.push({ text, tone: isLast ? pendingTone : null })
    })
    letters = ''
    pendingTone = null
  }

  for (const ch of raw) {
    if (ch >= '1' && ch <= '5') {
      pendingTone = Number(ch)
      flush()
      continue
    }
    const base = baseOf(ch)
    if (base) letters += base
  }
  flush()
  return out
}

export function toNumberedPinyin(pinyin: string): string {
  return syllableTones(pinyin)
    .map((s) => s.text + (s.tone === null ? '' : String(s.tone)))
    .join('')
}

/** Tone sequence of the target, one entry per syllable (0 = unknown/neutral). */
export function targetTones(pinyin: string): number[] {
  return syllableTones(pinyin).map((s) => s.tone ?? 0)
}

export type PinyinVerdict = 'ok' | 'wrong-letters' | 'wrong-tones' | 'partial-tones' | 'empty'

export interface PinyinGrade {
  ok: boolean
  verdict: PinyinVerdict
  /** What the player typed, normalised to bare letters. */
  attempt: string
  /** "nǐ hǎo" — for the "correct answer" reveal. */
  expectedMarked: string
  /** "ni3hao3" — for the tone-number hint. */
  expectedNumbered: string
}

export function gradePinyin(raw: string, targetPinyin: string): PinyinGrade {
  const expectedMarked = targetPinyin
  const expectedNumbered = toNumberedPinyin(targetPinyin)
  const attempt = lettersOf(raw)
  const target = syllableTones(targetPinyin)
  const wantedLetters = target.map((s) => s.text).join('')

  const fail = (verdict: PinyinVerdict): PinyinGrade => ({
    ok: false,
    verdict,
    attempt,
    expectedMarked,
    expectedNumbered,
  })

  if (attempt.length === 0) return fail('empty')
  if (attempt !== wantedLetters) return fail('wrong-letters')

  const given = parseAttempt(raw)
  if (given.length !== target.length) return fail('wrong-letters')

  const known = given.filter((s) => s.tone !== null).length
  // Tone-less attempt: accepted.
  if (known === 0) return { ok: true, verdict: 'ok', attempt, expectedMarked, expectedNumbered }
  // Half-tones: explained instead of guessed.
  if (known < target.length) return fail('partial-tones')

  for (let i = 0; i < target.length; i += 1) {
    const g = given[i].tone
    if (g !== null && g !== target[i].tone) return fail('wrong-tones')
  }
  return { ok: true, verdict: 'ok', attempt, expectedMarked, expectedNumbered }
}
