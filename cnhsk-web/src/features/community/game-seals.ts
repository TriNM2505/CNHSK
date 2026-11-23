/** One seal character per game, so a tile reads at a glance like a stamp on a scroll. */
export const GAME_SEALS: Record<string, string> = {
  '': '总',
  CHAR_RAIN: '墨',
  PINYIN_MATCH: '拼',
  RADICAL_MATCH: '部',
  CHAR_CATCH: '抓',
  BRUSH_STROKE: '雨',
  FLYING_DART: '飞',
}

export function gameSeal(code: string, name = ''): string {
  return GAME_SEALS[code] ?? name.charAt(0)
}
