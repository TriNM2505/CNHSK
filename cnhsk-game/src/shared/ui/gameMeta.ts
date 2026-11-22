// Display metadata and links for the CNHSK Arcade look.

export type GameLayout = 'core' | 'arcade' | 'rain'

export type GameMeta = {
  slug: string
  name: string
  eyebrow: string
  symbol: string
  detail: string
  layout: GameLayout
}

/** Display metadata per server game code; names and rules still come from the catalog API. */
export const GAME_META: Readonly<Record<string, GameMeta>> = {
  CHAR_RAIN: { slug: 'char-rain', name: 'Lọ Mực', eyebrow: 'GIỮ MỰC', symbol: '墨', layout: 'rain',
    detail: 'Viết đúng thứ tự nét; sai ba lần là hết mực.' },
  PINYIN_MATCH: { slug: 'pinyin-match', name: 'Ghép Pinyin', eyebrow: 'GHÉP CẶP', symbol: '音', layout: 'core',
    detail: 'Ghép chữ Hán với phiên âm qua năm bảng.' },
  RADICAL_MATCH: { slug: 'radical-match', name: 'Ghép Bộ Thủ', eyebrow: 'NHẬN DIỆN BỘ', symbol: '部', layout: 'core',
    detail: 'Chọn bộ thủ đúng của từng chữ.' },
  CHAR_CATCH: { slug: 'char-catch', name: 'Bắt Chữ', eyebrow: 'SÁU LỰA CHỌN', symbol: '字', layout: 'core',
    detail: 'Bắt đúng chữ theo gợi ý.' },
  BRUSH_STROKE: { slug: 'brush-stroke', name: 'Mưa Chữ', eyebrow: 'VIẾT TỰ DO', symbol: '雨', layout: 'arcade',
    detail: 'Viết chữ đang rơi trước khi nó chạm đất.' },
  FLYING_DART: { slug: 'flying-dart', name: 'Phi Đao', eyebrow: 'ÂM HÁN VIỆT', symbol: '刀', layout: 'arcade',
    detail: 'Gõ âm Hán Việt để gấu phi dao hạ chữ.' },
}

export function gameMeta(code: string): GameMeta {
  return GAME_META[code] ?? {
    slug: code.toLowerCase().replace(/_/g, '-'), name: code, eyebrow: 'TRÒ CHƠI', symbol: '字', layout: 'core', detail: '',
  }
}

/** A page on the learning web app (leaderboards, home); the game app only links there. */
export function webHref(path: string): string {
  return new URL(path, import.meta.env.VITE_WEB_ORIGIN || 'http://localhost:5173').href
}
