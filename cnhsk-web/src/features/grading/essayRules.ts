import type { GradingPolicy } from './types'

// Match Java Character.isWhitespace || Character.isSpaceChar.
function isBoundaryWhitespace(character: string): boolean {
  const point = character.codePointAt(0)!
  return /\p{Z}/u.test(character) || (point >= 9 && point <= 13) || (point >= 28 && point <= 31)
}
export function trimmedEssay(text: string): string {
  const characters = Array.from(text)
  let start = 0,
    end = characters.length
  while (start < end && isBoundaryWhitespace(characters[start]!)) start++
  while (end > start && isBoundaryWhitespace(characters[end - 1]!)) end--
  return characters.slice(start, end).join('')
}
export function codePointLength(text: string): number {
  return Array.from(text).length
}
export function countHanzi(text: string): number {
  return Array.from(text).filter((character) => {
    const point = character.codePointAt(0)!
    return point >= 0x4e00 && point <= 0x9fff
  }).length
}

export function validSubmission(essay: string, prompt: string, policy?: GradingPolicy): boolean {
  if (!policy) return false
  const text = trimmedEssay(essay)
  const count = countHanzi(text)
  return (
    count >= policy.minHanzi &&
    count <= policy.maxHanzi &&
    codePointLength(text) <= policy.maxChars &&
    codePointLength(prompt) <= 500
  )
}
