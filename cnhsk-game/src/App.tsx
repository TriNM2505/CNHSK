import { useEffect, useMemo, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { Link, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { WRITING_LEVELS } from './data/levels'
import { arcadePool, AVAILABLE_HSK_LEVELS } from './data/words'
import type { HskLevel, Lexeme } from './data/words'
import { strokesOf } from './data/strokes'
import { drawQueue, fallDurationMs, stageConfig, strikePoints } from './engine/arcade'
import type { ArcadeMode } from './engine/arcade'
import { gradePinyin } from './engine/pinyin'
import { normalizePoints, resample, sampleStroke } from './engine/path'
import type { Point } from './engine/path'
import { gradeStroke } from './engine/strokeScore'
import CoreGames from './games/CoreGames'

type Game = 'rain' | 'brush' | 'dart'

const GAME_INFO: Record<Game, { name: string; eyebrow: string; detail: string; symbol: string }> = {
  rain: { name: 'Mưa Chữ', eyebrow: 'CHỌN NGHĨA', detail: 'Chọn đúng nghĩa tiếng Việt trước khi chữ Hán chạm đất.', symbol: '雨' },
  brush: { name: 'Thần Bút', eyebrow: 'VIẾT ĐỂ ĐÁNH TRÚNG', detail: 'Theo dấu chữ rơi trước khi nó chạm mặt đất.', symbol: '筆' },
  dart: { name: 'Phi Đao', eyebrow: 'GÕ PINYIN', detail: 'Nhập phiên âm chính xác để phóng dao hạ chữ.', symbol: '刀' },
}

function Scene({ game = 'brush' }: { game?: Game }) {
  return <div className={`scene scene-${game}`} aria-hidden="true">
    <div className="moon" /><div className="stars" /><div className="mountain mountain-back" /><div className="mountain mountain-front" />
    <div className="house"><div className="roof" /><div className="window" /><div className="door" /></div>
    <div className="panda"><div className="ear ear-left" /><div className="ear ear-right" /><div className="panda-face"><span className="eye eye-left" /><span className="eye eye-right" /><span className="nose" /></div><div className="panda-body" /><div className="panda-brush" /></div>
    <div className="ground" />
  </div>
}

function Header({ current }: { current?: Game }) {
  return <header className="topbar"><Link className="brand" to="/"><span className="seal">字</span><span>CNHSK <b>ARCADE</b></span></Link><nav><Link to="/">Chọn game</Link>{current && <span className="nav-current">/ {GAME_INFO[current].name}</span>}</nav><span className="demo-pill">BẢN CHƠI THỬ · DỮ LIỆU MẪU</span></header>
}

function Home() {
  const games = [
    { path: '/play/char-rain', name: 'Mưa Chữ', eyebrow: 'CHỌN NGHĨA', detail: 'Chọn nghĩa của chữ đang rơi.', symbol: '雨' },
    { path: '/play/pinyin-match', name: 'Ghép Pinyin', eyebrow: 'GHÉP CẶP', detail: 'Ghép chữ Hán với phiên âm qua ba bảng.', symbol: '音' },
    { path: '/play/radical-match', name: 'Ghép Bộ Thủ', eyebrow: 'NHẬN DIỆN BỘ', detail: 'Chọn bộ thủ đúng của từng chữ.', symbol: '部' },
    { path: '/play/char-catch', name: 'Bắt Chữ', eyebrow: 'SÁU LỰA CHỌN', detail: 'Bắt đúng chữ theo nghĩa và pinyin.', symbol: '字' },
    { path: '/arcade/brush', name: 'Thần Bút', eyebrow: 'LUYỆN NÉT', detail: 'Tô chữ Hán trước khi nó chạm đất.', symbol: '筆' },
    { path: '/arcade/dart', name: 'Phi Đao', eyebrow: 'GÕ PINYIN', detail: 'Nhập pinyin để hạ chữ đang rơi.', symbol: '刀' },
  ]
  return <><Header /><main className="home"><section className="hero"><Scene /><div className="hero-copy"><span className="eyebrow">CHƠI ĐỂ NHỚ · HỌC ĐỂ VUI</span><h1>Mỗi chữ Hán,<br /><em>một cuộc phiêu lưu.</em></h1><p>Sáu trò chơi luyện chữ Hán, pinyin, bộ thủ và từ vựng. Chọn một trò để bắt đầu.</p><a href="#games" className="primary-button">Khám phá trò chơi <span>↗</span></a></div><div className="hero-count"><b>06</b><span>TRÒ CHƠI<br />ĐANG MỞ</span></div></section><section id="games" className="games-section"><div className="section-heading"><div><span className="eyebrow">GÓC LUYỆN TẬP</span><h2>Chọn cuộc hành trình</h2></div><p>Thiết kế cho cả chuột, bàn phím và cảm ứng.</p></div><div className="game-grid">{games.map((game, i) => <Link to={game.path} className={`game-card game-${i}`} key={game.path}><div className="card-number">0{i + 1} / 06</div><div className="card-symbol">{game.symbol}</div><span className="eyebrow">{game.eyebrow}</span><h3>{game.name}</h3><p>{game.detail}</p><span className="card-link">Vào chơi <b>→</b></span></Link>)}</div></section><footer>CNHSK · Bản demo độc lập. Tiến độ chỉ lưu trong phiên chơi.</footer></main></>
}

function pointFromEvent(event: ReactPointerEvent<SVGSVGElement>): Point {
  const matrix = event.currentTarget.getScreenCTM()
  if (matrix) {
    const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse())
    return { x: Math.max(0, Math.min(1000, point.x)), y: Math.max(0, Math.min(1000, point.y)) }
  }
  const box = event.currentTarget.getBoundingClientRect()
  return { x: Math.max(0, Math.min(1000, (event.clientX - box.left) / box.width * 1000)), y: Math.max(0, Math.min(1000, (event.clientY - box.top) / box.height * 1000)) }
}

function pathOf(points: Point[]): string { return points.map((p, i) => `${i ? 'L' : 'M'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ') }

function StrokeBoard({ char, onComplete, resetKey }: { char: string; onComplete: () => void; resetKey: number }) {
  const strokes = strokesOf(char) ?? []
  const [done, setDone] = useState<Point[][]>([])
  const [current, setCurrent] = useState<Point[]>([])
  const [hint, setHint] = useState('Đi theo nét màu vàng, từ điểm tròn bắt đầu.')
  const drawing = useRef(false)
  const currentRef = useRef<Point[]>([])
  const pointerRef = useRef<number | null>(null)
  useEffect(() => { drawing.current = false; currentRef.current = []; pointerRef.current = null; setDone([]); setCurrent([]); setHint('Đi theo nét màu vàng, từ điểm tròn bắt đầu.') }, [char, resetKey])
  const target = strokes[done.length]
  const start = target ? sampleStroke(target, 2)[0] : null
  const finish = (event: ReactPointerEvent<SVGSVGElement>) => {
    if (!drawing.current || pointerRef.current !== event.pointerId || !target) return
    const points = [...currentRef.current, pointFromEvent(event)]
    drawing.current = false
    pointerRef.current = null
    currentRef.current = []
    const graded = gradeStroke(normalizePoints(sampleStroke(target, 32)), normalizePoints(resample(points, 32)))
    setCurrent([])
    if (!graded.ok) { setHint(graded.hint ?? 'Thử lại nét này.'); return }
    setHint(`Nét đúng! ${graded.score}/100`)
    if (done.length + 1 >= strokes.length) onComplete()
    else setDone((old) => [...old, points])
  }
  return <div className="board-shell"><div className="board-top"><span>{done.length}/{strokes.length} NÉT</span><span>✦ Theo thứ tự nét</span></div><svg className="stroke-board" viewBox="0 0 1000 1000" onPointerDown={(event) => { if (!target || drawing.current) return; const point = pointFromEvent(event); event.currentTarget.setPointerCapture(event.pointerId); pointerRef.current = event.pointerId; drawing.current = true; currentRef.current = [point]; setCurrent([point]) }} onPointerMove={(event) => { if (!drawing.current || pointerRef.current !== event.pointerId) return; const point = pointFromEvent(event); currentRef.current = [...currentRef.current, point]; setCurrent(currentRef.current) }} onPointerUp={finish} onPointerCancel={() => { drawing.current = false; pointerRef.current = null; currentRef.current = []; setCurrent([]) }} role="img" aria-label={`Tập viết chữ ${char}`}><path className="board-guide" d="M500 0V1000 M0 500H1000 M0 0L1000 1000 M1000 0L0 1000" />{strokes.map((path, i) => <path key={i} d={path} className={i < done.length ? 'stroke-done' : i === done.length ? 'stroke-next' : 'stroke-future'} />)}{start && <circle cx={start.x} cy={start.y} r="22" className="stroke-start" />}{done.map((points, i) => <path key={`drawn-${i}`} d={pathOf(points)} className="stroke-ink" />)}<path d={pathOf(current)} className="stroke-ink" /></svg><div className="board-hint" role="status">{hint}</div></div>
}

function Rain() {
  const navigate = useNavigate()
  const [levelId, setLevelId] = useState<number | null>(null)
  const [index, setIndex] = useState(0)
  const [resetKey, setResetKey] = useState(0)
  const [completed, setCompleted] = useState(false)
  const level = WRITING_LEVELS.find((entry) => entry.id === levelId)
  const entry = level?.chars[index]
  const choose = (id: number) => { setLevelId(id); setIndex(0); setCompleted(false); setResetKey((n) => n + 1) }
  return <><Header current="rain" /><main className="rain-page"><div className="page-intro"><span className="eyebrow">01 / MƯA CHỮ</span><h1>Viết từng nét.<br /><em>Ghi nhớ từng chữ.</em></h1><p>Chọn một màn có dữ liệu nét thật, rồi dùng chuột hoặc tay tô theo nét sáng.</p></div>{levelId === null ? <div className="level-grid">{WRITING_LEVELS.map((item) => <button className="level-card" key={item.id} onClick={() => choose(item.id)}><span className="level-badge">0{item.id}</span><h2>{item.name}</h2><p>{item.subtitle}</p><div className="level-chars">{item.chars.map((char) => char.char).join(' · ')}</div><span>Chơi màn này →</span></button>)}</div> : completed ? <section className="result-panel"><span className="result-star">✦ ✦ ✦</span><span className="eyebrow">HOÀN THÀNH</span><h2>{level?.name} đã xong!</h2><p>Bạn đã viết đúng {level?.chars.length} chữ. Chọn màn khác để tiếp tục luyện tập.</p><div className="button-row"><button className="primary-button" onClick={() => choose(levelId)}>Chơi lại</button><button className="secondary-button" onClick={() => setLevelId(null)}>Chọn màn</button></div></section> : entry && <div className="rain-layout"><section className="rain-side"><button className="text-button" onClick={() => setLevelId(null)}>← Chọn màn khác</button><div className="rain-progress"><span>MÀN {levelId} · CHỮ {index + 1}/{level?.chars.length}</span><div><i style={{ width: `${index / (level?.chars.length || 1) * 100}%` }} /></div></div><div className="rain-char">{entry.char}</div><div className="rain-pinyin">{entry.pinyin}</div><div className="rain-meaning">{entry.meaning}</div><p className="rain-tip">Chữ này có {entry.strokeCount} nét. Tô đúng từng nét theo hướng từ điểm tròn. Viết sai có thể thử lại ngay.</p><div className="jar"><span>✦</span><div className="jar-fill" style={{ height: `${(index / (level?.chars.length || 1)) * 100}%` }} /></div><span className="jar-caption">LỌ MỰC CỦA BẠN</span></section><StrokeBoard key={`${entry.char}-${resetKey}`} char={entry.char} resetKey={resetKey} onComplete={() => { if (index + 1 >= (level?.chars.length || 0)) setCompleted(true); else { setIndex((n) => n + 1); setResetKey((n) => n + 1) } }} /></div>}<button className="text-button home-back" onClick={() => navigate('/')}>← Về trang chọn game</button></main></>
}

function useArcadeWords(game: 'brush' | 'dart', hsk: HskLevel) { return useMemo(() => drawQueue(arcadePool(hsk, game === 'brush' ? 'CHAR' : 'all').filter((word) => game === 'dart' || !!strokesOf(word.text)), 80), [game, hsk]) }

function Arcade() {
  const param = useParams().game
  const game: 'brush' | 'dart' = param === 'brush' ? 'brush' : 'dart'
  const [hsk, setHsk] = useState<HskLevel>(1)
  const [mode, setMode] = useState<ArcadeMode>('stage')
  const [phase, setPhase] = useState<'setup' | 'playing' | 'paused' | 'result'>('setup')
  const [index, setIndex] = useState(0)
  const [hits, setHits] = useState(0)
  const [score, setScore] = useState(0)
  const [combo, setCombo] = useState(0)
  const [lives, setLives] = useState(3)
  const [stage, setStage] = useState(1)
  const [timeLeft, setTimeLeft] = useState(60)
  const [fall, setFall] = useState(0)
  const [input, setInput] = useState('')
  const [feedback, setFeedback] = useState('')
  const [resetKey, setResetKey] = useState(0)
  const queue = useArcadeWords(game, hsk)
  const word: Lexeme | undefined = queue[index % queue.length]
  const next = () => { setIndex((n) => n + 1); setFall(0); setInput(''); setResetKey((n) => n + 1) }
  const miss = () => { setCombo(0); setFeedback(`Chữ ${word?.text ?? ''} đã chạm đất. Đáp án: ${word?.pinyin ?? ''}`); if (mode === 'stage' && lives <= 1) setPhase('result'); else { if (mode === 'stage') setLives((n) => n - 1); next() } }
  const hit = () => { const earned = strikePoints(combo, 1 - fall / 100); setScore((n) => n + earned); setCombo((n) => n + 1); setFeedback(`Chính xác! +${earned} điểm · ${word?.pinyin ?? ''}`); const newHits = hits + 1; setHits(newHits); if (mode === 'stage' && newHits >= stageConfig(stage).quota) { if (stage >= 5) setPhase('result'); else { setStage((n) => n + 1); setHits(0); setLives(3); next() } } else next() }
  const start = () => { setIndex(0); setHits(0); setScore(0); setCombo(0); setLives(3); setStage(1); setTimeLeft(60); setFall(0); setFeedback(''); setResetKey((n) => n + 1); setPhase('playing') }
  useEffect(() => { if (phase !== 'playing') return; const interval = window.setInterval(() => setFall((old) => Math.min(100, old + 10000 / (game === 'brush' ? Math.max(fallDurationMs(index, mode), (strokesOf(word?.text ?? '')?.length ?? 0) * 2500) : fallDurationMs(index, mode)))), 100); return () => window.clearInterval(interval) }, [phase, index, mode, game, word?.text])
  useEffect(() => { if (phase === 'playing' && fall >= 100) miss() }, [fall, phase])
  useEffect(() => { if (phase !== 'playing' || mode !== 'time') return; const interval = window.setInterval(() => setTimeLeft((old) => { if (old <= 1) { setPhase('result'); return 0 } return old - 1 }), 1000); return () => window.clearInterval(interval) }, [phase, mode])
  const submit = () => { if (!word || phase !== 'playing') return; const result = gradePinyin(input, word.pinyin); if (result.ok) hit(); else { setCombo(0); setFeedback(result.verdict === 'wrong-tones' ? 'Sai thanh điệu. Bạn có thể gõ không dấu hoặc thêm số thanh.' : 'Chưa đúng pinyin — thử lại!'); setInput('') } }
  return <><Header current={game} /><main className="arcade-page"><div className="arcade-title"><span className="eyebrow">{game === 'brush' ? '02 / THẦN BÚT' : '03 / PHI ĐAO'}</span><h1>{GAME_INFO[game].name}</h1><p>{GAME_INFO[game].detail}</p></div>{phase === 'setup' ? <div className="setup-layout"><Scene game={game} /><section className="setup-panel"><span className="eyebrow">CHUẨN BỊ RA TRẬN</span><h2>Chọn bộ từ mang ra trận</h2><p>Chỉ các bộ có dữ liệu mẫu đủ để chơi mới được mở.</p><div className="choice-row">{AVAILABLE_HSK_LEVELS.map((level) => <button key={level} className={hsk === level ? 'selected' : ''} disabled={game === 'brush' && arcadePool(level, 'CHAR').filter((w) => !!strokesOf(w.text)).length < 8} onClick={() => setHsk(level)}>HSK {level}</button>)}</div><h3>Chế độ</h3><div className="choice-row"><button className={mode === 'stage' ? 'selected' : ''} onClick={() => setMode('stage')}>⚑ Vượt ải · 3 mạng</button><button className={mode === 'time' ? 'selected' : ''} onClick={() => setMode('time')}>◷ 60 giây</button></div><div className="setup-instructions">{game === 'brush' ? 'Dùng chuột hoặc ngón tay tô đủ nét của chữ trong khung. Chữ sẽ rơi xuống theo thời gian.' : 'Gõ pinyin vào ô rồi nhấn Enter. Có thể gõ không dấu hoặc thêm số thanh: nǐ hǎo → ni3hao3.'}</div><button className="primary-button start-button" onClick={start} disabled={queue.length === 0}>Vào trận <span>→</span></button></section></div> : phase === 'result' ? <section className="result-panel"><span className="result-star">✦ ✦ ✦</span><span className="eyebrow">KẾT THÚC VÁN</span><h2>{score} điểm</h2><p>Bạn đã hạ {mode === 'time' ? hits : (stage - 1) * 6 + hits} chữ. Luyện tiếp để nhớ thêm từ mới.</p><div className="button-row"><button className="primary-button" onClick={start}>Chơi lại</button><button className="secondary-button" onClick={() => setPhase('setup')}>Đổi chế độ</button><Link className="secondary-button" to="/">Chọn game</Link></div></section> : <div className="play-layout"><section className="arena"><Scene game={game} /><div className="arena-top"><span>{mode === 'stage' ? `MÀN ${stage} · ${hits}/${stageConfig(stage).quota} CHỮ` : `CÒN ${timeLeft} GIÂY`}</span><button onClick={() => setPhase(phase === 'paused' ? 'playing' : 'paused')}>{phase === 'paused' ? '▶ Tiếp tục' : 'Ⅱ Tạm dừng'}</button></div>{word && <div className="falling-word" style={{ top: `${10 + fall * 0.56}%` }}><strong>{word.text}</strong><small>{word.meaning}</small></div>}{phase === 'paused' && <div className="pause-cover"><h2>Tạm dừng</h2><button className="primary-button" onClick={() => setPhase('playing')}>Tiếp tục</button><button className="secondary-button" onClick={() => setPhase('setup')}>Thoát ván</button></div>}</section><section className="play-controls"><div className="stats"><div><span>ĐIỂM</span><b>{score}</b></div><div><span>COMBO</span><b>×{combo}</b></div><div><span>{mode === 'stage' ? 'MẠNG' : 'THỜI GIAN'}</span><b>{mode === 'stage' ? '♥'.repeat(lives) : `${timeLeft}s`}</b></div></div><div className="control-card">{game === 'brush' && word ? <><div className="prompt-label">TÔ CHỮ ĐANG RƠI</div><h2>{word.text} <small>{word.pinyin}</small></h2><StrokeBoard key={`${word.id}-${resetKey}`} char={word.text} resetKey={resetKey} onComplete={hit} /></> : <><div className="prompt-label">GÕ PINYIN ĐỂ PHÓNG ĐAO</div><h2>{word?.text} <small>{word?.meaning}</small></h2><form onSubmit={(event) => { event.preventDefault(); submit() }}><input value={input} onChange={(event) => setInput(event.target.value)} placeholder="Ví dụ: ni3hao3" autoComplete="off" aria-label="Nhập pinyin" autoFocus disabled={phase === 'paused'} /><button className="primary-button" disabled={!input.trim() || phase === 'paused'}>Phóng dao ↗</button></form><p className="input-hint">Không dấu cũng được. Nếu nhập số thanh, hãy nhập đủ cho từng âm tiết.</p></>}{feedback && <div className="feedback" role="status">{feedback}</div>}</div></section></div>}<Link className="text-button home-back" to="/">← Về trang chọn game</Link></main></>
}

export default function App() { return <Routes><Route path="/" element={<Home />} /><Route path="/play/:game" element={<CoreGames />} /><Route path="/rain" element={<CoreGames />} /><Route path="/arcade/rain" element={<CoreGames />} /><Route path="/practice/strokes" element={<Rain />} /><Route path="/arcade/:game" element={<Arcade />} /><Route path="*" element={<Home />} /></Routes> }
