import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { drawQueue } from '../engine/arcade'
import type { HskLevel, Lexeme } from '../data/words'
import { choices, pinyinBoards, pool, radicalByChar, radicalLabels, radicalPool } from './coreData'

export type CoreGame = 'char-rain' | 'pinyin-match' | 'radical-match' | 'char-catch'

const info: Record<CoreGame, { name: string; desc: string; seconds: number; limit: number }> = {
  'char-rain': { name: 'Mưa Chữ', desc: 'Chọn nghĩa đúng của chữ Hán đang rơi.', seconds: 60, limit: 30 },
  'pinyin-match': { name: 'Ghép Pinyin', desc: 'Ghép sáu từ với sáu phiên âm tương ứng qua ba bảng.', seconds: 90, limit: 18 },
  'radical-match': { name: 'Ghép Bộ Thủ', desc: 'Tìm bộ thủ đúng của chữ Hán.', seconds: 90, limit: 15 },
  'char-catch': { name: 'Bắt Chữ', desc: 'Chọn chữ Hán khớp với nghĩa và pinyin trong lưới sáu ô.', seconds: 75, limit: 20 },
}

function GameArtwork({ game }: { game: CoreGame }) {
  if (game === 'pinyin-match') return <div className="game-art game-art-match" aria-hidden="true"><span className="art-spark art-spark-a">✦</span><span className="art-spark art-spark-b">✧</span><div className="art-card art-card-left"><b>朋友</b><small>bạn bè</small></div><svg viewBox="0 0 240 90"><path d="M5 70 C80 0 140 110 235 20" /><circle cx="5" cy="70" r="6" /><circle cx="235" cy="20" r="6" /></svg><div className="art-card art-card-right"><b>péngyou</b><small>ghép đúng ✓</small></div></div>
  if (game === 'radical-match') return <div className="game-art game-art-radical" aria-hidden="true"><span className="art-spark art-spark-a">✦</span><div className="art-big-char">河</div><div className="art-radical">氵 <span>＋</span> 可</div><div className="art-chip">BỘ THỦ · 氵</div></div>
  if (game === 'char-catch') return <div className="game-art game-art-catch" aria-hidden="true"><span className="art-target">◎</span><div className="art-cue">shān · núi</div><div className="art-target-grid"><span>木</span><span>口</span><strong>山</strong><span>水</span><span>日</span><span>人</span></div><span className="art-spark art-spark-b">✦</span></div>
  return <div className="game-art" aria-hidden="true">雨</div>
}

function useClock(active: boolean, start: number, onEnd: () => void) {
  const [left, setLeft] = useState(start)
  useEffect(() => { setLeft(start) }, [start])
  useEffect(() => {
    if (!active) return
    const id = window.setInterval(() => setLeft((old) => Math.max(0, old - 1)), 1000)
    return () => window.clearInterval(id)
  }, [active])
  useEffect(() => { if (active && left === 0) onEnd() }, [active, left, onEnd])
  return [left, setLeft] as const
}

export default function CoreGames() {
  const { game: routeGame } = useParams()
  const game: CoreGame = routeGame && routeGame in info ? routeGame as CoreGame : 'char-rain'
  const config = info[game]
  const reducedMotion = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  const [level, setLevel] = useState<HskLevel>(1)
  const [phase, setPhase] = useState<'setup' | 'play' | 'pause' | 'result'>('setup')
  const [round, setRound] = useState(0)
  const [index, setIndex] = useState(0)
  const [correct, setCorrect] = useState(0)
  const [wrong, setWrong] = useState(0)
  const [feedback, setFeedback] = useState('')
  const [reveal, setReveal] = useState<{ choice: string; correct: boolean } | null>(null)
  const revealTimer = useRef<number | null>(null)
  const answerLock = useRef(false)
  useEffect(() => () => { if (revealTimer.current !== null) window.clearTimeout(revealTimer.current) }, [])
  const [pairSelection, setPairSelection] = useState<{ side: 'word' | 'pin'; id: string } | null>(null)
  const [matched, setMatched] = useState<string[]>([])
  const [board, setBoard] = useState(0)
  const [fall, setFall] = useState(0)
  const [questionLeft, setQuestionLeft] = useState(4)
  const queue = useMemo(() => {
    if (game === 'pinyin-match') return pinyinBoards(level).flat()
    if (game === 'radical-match') return drawQueue(radicalPool(level), config.limit)
    return drawQueue(pool(level), config.limit)
  }, [game, level, round, config.limit])
  const word = queue[index]
  const options = useMemo(() => {
    if (!word) return []
    if (game === 'radical-match') {
      const answer = radicalByChar.get(word.text) ?? ''
      return choices(answer, radicalLabels, 4, (value) => value)
    }
    const count = game === 'char-catch' ? 6 : 4
    const source = game === 'char-rain' ? pool(level).filter((item) => item.meaning !== word.meaning) : pool(level)
    return choices(word, source, count, (item) => game === 'char-rain' ? item.meaning : item.text)
  }, [word, game, level])
  const boards = useMemo(() => [queue.slice(0, 6), queue.slice(6, 12), queue.slice(12, 18)], [queue])
  const pins = useMemo(() => boards.map((items) => drawQueue(items, items.length)), [boards])
  const finish = () => setPhase('result')
  const [left, setLeft] = useClock(phase === 'play' && !reveal, config.seconds, finish)
  const start = () => { if (revealTimer.current !== null) window.clearTimeout(revealTimer.current); answerLock.current = false; setRound((old) => old + 1); setIndex(0); setCorrect(0); setWrong(0); setFeedback(''); setReveal(null); setMatched([]); setPairSelection(null); setBoard(0); setFall(0); setQuestionLeft(game === 'char-catch' ? 4 : 6); setLeft(config.seconds); setPhase('play') }
  const advance = () => { answerLock.current = false; setFeedback(''); setFall(0); setQuestionLeft(game === 'char-catch' ? 4 : 6); setIndex((old) => old + 1) }
  const answer = (ok: boolean, choice = '') => {
    if (phase !== 'play' || !word || answerLock.current) return
    answerLock.current = true
    setFeedback(ok ? `Đúng! ${word.text} · ${word.pinyin} · ${word.meaning}` : game === 'radical-match' ? `Chưa đúng. Bộ thủ của ${word.text} là ${radicalByChar.get(word.text)}.` : `Chưa đúng. Đáp án: ${word.text} · ${word.meaning}`)
    setReveal({ choice, correct: ok })
    if (ok) setCorrect((old) => old + 1); else setWrong((old) => old + 1)
    revealTimer.current = window.setTimeout(() => { setReveal(null); if (index + 1 >= queue.length) finish(); else advance() }, 650)
  }
  useEffect(() => {
    if (phase !== 'play' || game !== 'char-rain' || reducedMotion || reveal) return
    const id = window.setInterval(() => setFall((old) => old + 100 / Math.max(3000, 6000 - index * 150) * 100), 100)
    return () => window.clearInterval(id)
  }, [phase, game, index, reducedMotion, reveal])
  useEffect(() => { if (phase === 'play' && game === 'char-rain' && fall >= 100) answer(false) }, [fall, phase, game])
  useEffect(() => {
    if (phase !== 'play' || reveal || !(game === 'char-catch' || (game === 'char-rain' && reducedMotion))) return
    const id = window.setInterval(() => setQuestionLeft((old) => Math.max(0, old - 1)), 1000)
    return () => window.clearInterval(id)
  }, [phase, game, index, reducedMotion, reveal])
  useEffect(() => { if (phase === 'play' && (game === 'char-catch' || (game === 'char-rain' && reducedMotion)) && questionLeft === 0) answer(false) }, [questionLeft, phase, game, reducedMotion])
  const choosePair = (side: 'word' | 'pin', id: string) => {
    if (phase !== 'play' || matched.includes(id)) return
    if (!pairSelection || pairSelection.side === side) { setPairSelection({ side, id }); return }
    const ok = pairSelection.id === id
    setPairSelection(null)
    if (!ok) { setWrong((old) => old + 1); setFeedback('Sai cặp, thử lại.'); return }
    const nextMatched = [...matched, id]
    setMatched(nextMatched); setCorrect((old) => old + 1); setFeedback('Ghép đúng!')
    if (nextMatched.length === boards[board].length) {
      if (board === 2) finish()
      else { setBoard((old) => old + 1); setMatched([]); setFeedback('') }
    }
  }
  const score = correct * 10
  return <main className={`core-page core-page-${game} ${phase === 'play' || phase === 'pause' ? 'core-page-playing' : ''}`}>
    <div className="core-header"><Link to="/" className="text-button">← CNHSK Arcade</Link><span className="demo-pill">BẢN DEMO · DỮ LIỆU MẪU</span></div>
    <div className="core-title"><span className="eyebrow">GAME BOX / {game.toUpperCase()}</span><h1>{config.name}</h1><p>{config.desc}</p></div>
    {phase === 'setup' && <section className="core-panel core-setup"><div className="core-setup-copy"><span className="core-kicker">✦ MỘT VÁN NHANH · MỘT KỸ NĂNG MỚI</span><h2>Chọn bộ từ<br /><em>mang ra trận.</em></h2><p>{config.desc} Chơi bằng dữ liệu mẫu HSK 1–2; điểm chỉ tính trong phiên chơi.</p><div className="core-field-label">CẤP ĐỘ TỪ VỰNG</div><div className="choice-row"><button className={level === 1 ? 'selected' : ''} onClick={() => setLevel(1)}>HSK 1</button><button className={level === 2 ? 'selected' : ''} onClick={() => setLevel(2)}>HSK 2</button></div><div className="core-rules"><span>◷ {config.seconds} giây</span><span>✦ {config.limit} lượt</span><span>＋10 điểm / đúng</span></div><button className="primary-button" onClick={start}>Bắt đầu chơi <span aria-hidden="true">↗</span></button></div><GameArtwork game={game} /></section>}
    {(phase === 'play' || phase === 'pause') && <div className="core-play"><div className="core-stats"><span className="stat-level">HSK {level}</span><span className={left <= 10 ? 'stat-time urgent' : 'stat-time'}>◷ {left}s</span><span className="stat-score">✦ {score} điểm</span><button disabled={!!reveal} onClick={() => setPhase(phase === 'pause' ? 'play' : 'pause')}>{phase === 'pause' ? '▶ Tiếp tục' : 'Ⅱ Tạm dừng'}</button></div><div className="core-timebar"><span style={{ width: `${left / config.seconds * 100}%` }} /></div>{phase === 'pause' ? <section className="core-panel core-center"><h2>Đã tạm dừng</h2><button className="primary-button" onClick={() => setPhase('play')}>Tiếp tục</button><button className="secondary-button" onClick={() => setPhase('setup')}>Thoát ván</button></section> : <>
      {game === 'pinyin-match' ? <section className="core-panel core-game-panel match-panel"><div className="core-panel-heading"><div><span className="core-progress">BẢNG {board + 1} / 3</span><h2>Nối chữ với âm đọc</h2><p>Chọn một thẻ chữ Hán, rồi chọn pinyin tương ứng.</p></div><div className="board-score">{matched.length}<small>/ 6 cặp</small></div></div><div className="pair-progress">{Array.from({ length: 6 }, (_, i) => <span key={i} className={i < matched.length ? 'filled' : ''} />)}</div><div className="match-headings"><span>CHỮ HÁN</span><span>PINYIN</span></div><div className="match-columns"><div>{boards[board]?.map((item, i) => <button key={item.id} disabled={matched.includes(item.id)} className={`match-tile ${pairSelection?.id === item.id && pairSelection.side === 'word' ? 'active' : ''}`} onClick={() => choosePair('word', item.id)}><span className="tile-index">0{i + 1}</span><span className="tile-main">{item.text}<small>{item.meaning}</small></span><span className="tile-node" /></button>)}</div><div>{pins[board]?.map((item, i) => <button key={item.id} disabled={matched.includes(item.id)} className={`match-tile ${pairSelection?.id === item.id && pairSelection.side === 'pin' ? 'active' : ''}`} onClick={() => choosePair('pin', item.id)}><span className="tile-node" /><span className="tile-main">{item.pinyin}</span><span className="tile-index">0{i + 1}</span></button>)}</div></div></section> : <section className={`core-panel core-center core-game-panel ${game === 'radical-match' ? 'radical-panel' : game === 'char-catch' ? 'catch-panel' : ''}`}><div className="core-progress">CÂU {Math.min(index + 1, queue.length)} / {queue.length}{(game === 'char-catch' || (game === 'char-rain' && reducedMotion)) && ` · ${questionLeft}s cho câu này`}</div><div className="question-progress"><span style={{ width: `${(index + 1) / queue.length * 100}%` }} /></div>{game === 'char-rain' ? <><div className="core-fall-track"><strong style={{ top: reducedMotion ? '40%' : `${Math.min(fall, 85)}%` }}>{word?.text}</strong></div><h2>Chữ này có nghĩa là gì?</h2><div className="answer-grid">{(options as Lexeme[]).map((item, i) => <button key={item.id} disabled={!!reveal} className={reveal?.choice === item.meaning ? reveal.correct ? 'answer-correct' : 'answer-wrong' : ''} onClick={() => answer(item.meaning === word?.meaning, item.meaning)}><span className="answer-index">{String.fromCharCode(65 + i)}</span>{item.meaning}</button>)}</div></> : game === 'radical-match' ? <><span className="question-kicker">CHỌN BỘ THỦ ĐÚNG</span><div className="radical-orbit"><span className="orbit-mark orbit-a">✦</span><div className="core-hanzi">{word?.text}</div><span className="orbit-mark orbit-b">✧</span></div><p className="character-meta">{word?.pinyin} <span>·</span> {word?.meaning}</p><h2>Chữ này có bộ thủ nào?</h2><div className="answer-grid radical-grid">{(options as string[]).map((item, i) => <button key={item} disabled={!!reveal} className={reveal?.choice === item ? reveal.correct ? 'answer-correct' : 'answer-wrong' : ''} onClick={() => answer(item === radicalByChar.get(word?.text ?? ''), item)}><span className="answer-index">{String.fromCharCode(65 + i)}</span><b>{item}</b></button>)}</div></> : <><span className="question-kicker">NHÌN GỢI Ý · BẮT ĐÚNG CHỮ</span><div className="catch-prompt"><span className="catch-target">◎</span><div><strong>{word?.meaning}</strong><small>{word?.pinyin}</small></div><span className="catch-clock">{questionLeft}s</span></div><h2>Chữ nào khớp với gợi ý?</h2><div className="answer-grid catch-grid">{(options as Lexeme[]).map((item, i) => <button key={item.id} disabled={!!reveal} className={reveal?.choice === item.id ? reveal.correct ? 'answer-correct' : 'answer-wrong' : ''} onClick={() => answer(item.id === word?.id, item.id)}><span className="answer-index">{i + 1}</span><b>{item.text}</b></button>)}</div></>}</section>}
      <div className="core-feedback" role="status">{feedback || 'Chọn đáp án để ghi điểm.'}</div></>}</div>}
    {phase === 'result' && <section className="core-panel core-center core-result"><span className="eyebrow">KẾT THÚC VÁN</span><h2>{score} điểm</h2><p>Đúng {correct} · Sai {wrong} · {game === 'pinyin-match' ? `Hoàn thành ${board + 1}/3 bảng` : `Đã xem ${Math.min(index + 1, queue.length)} câu`}</p><div className="button-row"><button className="primary-button" onClick={start}>Chơi lại</button><button className="secondary-button" onClick={() => setPhase('setup')}>Đổi bộ từ</button><Link className="secondary-button" to="/">Chọn game</Link></div></section>}
  </main>
}
