// Small pictures of each game being played, drawn in the arcade palette. Decorative: the card names the game.
import type { ReactNode } from 'react'

import { gameMeta } from './gameMeta'

const ink = '#223443'
const cream = '#fff4e2'
const coral = '#ff7a52'
const gold = '#f3ba6f'
const green = '#5fd39a'
const navy = '#22384a'

function Hanzi({ x, y, size, fill = ink, children }: { x: number; y: number; size: number; fill?: string; children: string }) {
  return <text x={x} y={y} fontSize={size} fill={fill} fontFamily="'Noto Serif SC', serif" fontWeight="700"
    textAnchor="middle" dominantBaseline="central">{children}</text>
}

function Label({ x, y, size, fill = ink, children }: { x: number; y: number; size: number; fill?: string; children: string }) {
  return <text x={x} y={y} fontSize={size} fill={fill} fontWeight="700" textAnchor="middle" dominantBaseline="central">{children}</text>
}

function Tile({ x, y, width, height = 34, fill = cream, stroke, children }: {
  x: number; y: number; width: number; height?: number; fill?: string; stroke?: string; children?: ReactNode
}) {
  return (
    <g>
      <rect x={x} y={y + 4} width={width} height={height} rx="9" fill="#0b1620" opacity=".45" />
      <rect x={x} y={y} width={width} height={height} rx="9" fill={fill} stroke={stroke} strokeWidth={stroke ? 3 : 0} />
      {children}
    </g>
  )
}

function Check({ x, y, r = 11 }: { x: number; y: number; r?: number }) {
  return (
    <g>
      <circle cx={x} cy={y} r={r} fill={green} />
      <path d={`M${x - r * 0.45} ${y}l${r * 0.33} ${r * 0.36} ${r * 0.6} ${-r * 0.72}`} fill="none" stroke="#fff" strokeWidth="3"
        strokeLinecap="round" strokeLinejoin="round" />
    </g>
  )
}

function Spark({ x, y, size = 6, fill = gold }: { x: number; y: number; size?: number; fill?: string }) {
  const s = size / 3.2
  return <path fill={fill} d={`M${x} ${y - size}l${s} ${size - s} ${size - s} ${s} ${s - size} ${s} ${-s} ${size - s} ${-s} ${s - size} ${s - size} ${-s} ${size - s} ${-s}z`} />
}

function Panda({ x, y, scale = 1, children }: { x: number; y: number; scale?: number; children?: ReactNode }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
      <circle cx="-23" cy="-28" r="13" fill="#1b2530" /><circle cx="23" cy="-28" r="13" fill="#1b2530" />
      <path d="M-28 62c0-30 10-44 28-44s28 14 28 44z" fill="#2c3b48" /><path d="M-9 20h18v42H-9z" fill={cream} />
      <ellipse cx="0" cy="-6" rx="35" ry="31" fill="#fffdf6" />
      <ellipse cx="-14" cy="-8" rx="9" ry="12" fill="#1b2530" transform="rotate(16 -14 -8)" />
      <ellipse cx="14" cy="-8" rx="9" ry="12" fill="#1b2530" transform="rotate(-16 14 -8)" />
      <circle cx="-13" cy="-10" r="3.4" fill="#fff" /><circle cx="13" cy="-10" r="3.4" fill="#fff" />
      <path d="M-5 5h10l-5 6z" fill="#1b2530" />
      {children}
    </g>
  )
}

/** A practice square with the first strokes of 木 written. */
function WritingSquare({ x, y, size, last = true }: { x: number; y: number; size: number; last?: boolean }) {
  const k = size / 100
  return (
    <g transform={`translate(${x} ${y}) scale(${k})`}>
      <rect y="5" width="100" height="100" rx="10" fill="#0b1620" opacity=".4" />
      <rect width="100" height="100" rx="10" fill="#fffaf0" stroke="#e4d4bc" strokeWidth="2" />
      <path d="M50 4v92M4 50h92M6 6l88 88M94 6 6 94" stroke="#e8d6bd" strokeWidth="1.2" strokeDasharray="4 4" />
      <path d="M49 45C43 60 34 70 20 80M51 45c7 14 16 25 30 33" fill="none" stroke={last ? ink : '#22344330'} strokeWidth="8" strokeLinecap="round" />
      <path d="M22 37c20 1 38-1 57-4M50 15v73" fill="none" stroke={ink} strokeWidth="8" strokeLinecap="round" />
    </g>
  )
}

function InkJarScene() {
  return (
    <>
      <g transform="rotate(-4 100 85)"><WritingSquare x={42} y={26} size={116} last={false} /></g>
      <g transform="rotate(38 150 90)">
        <path d="M144 24h12v62h-12z" fill="#c9974f" /><path d="M143 82h14v12h-14z" fill="#8a4b32" />
        <path d="M143 94h14c0 14-3 22-7 30-4-8-7-16-7-30z" fill={ink} />
      </g>
      <path d="M214 44h52v14h-6v8c16 6 22 16 22 32v28c0 14-8 20-22 20h-40c-14 0-22-6-22-20V98c0-16 6-26 22-32v-8h-6z" fill="#ffffff26" stroke="#e9f3fb" strokeWidth="3" />
      <path d="M201 102c12-7 24 6 39 0s26-6 39 0v24c0 12-7 17-19 17h-40c-12 0-19-5-19-17z" fill="#101a24" />
      <path d="M212 36h56v12h-56z" rx="4" fill={coral} />
      <path d="M208 92v26" stroke="#fff" strokeWidth="4" strokeLinecap="round" opacity=".5" />
      <path d="M296 52c7 9 10 14 10 20a10 10 0 0 1-20 0c0-6 3-11 10-20z" fill="#101a24" stroke="#e9f3fb" strokeWidth="2" />
      <path d="M296 92c7 9 10 14 10 20a10 10 0 0 1-20 0c0-6 3-11 10-20z" fill="none" stroke="#e9f3fb80" strokeWidth="2" strokeDasharray="4 4" />
      <Spark x={22} y={40} /><Spark x={182} y={26} fill={coral} />
    </>
  )
}

function PinyinScene() {
  const words = ['朋友', '老师', '你好']
  const readings = ['nǐ hǎo', 'péngyou', 'lǎoshī']
  return (
    <>
      <path d="M112 34C150 34 168 85 208 85" fill="none" stroke={green} strokeWidth="4" />
      <path d="M112 85c40 0 56 51 96 51" fill="none" stroke={green} strokeWidth="4" />
      <path d="M112 136c30 0 44-20 58-48" fill="none" stroke={coral} strokeWidth="4" strokeDasharray="3 9" strokeLinecap="round" />
      {words.map((word, i) => (
        <Tile key={word} x={30} y={17 + i * 51} width={82} fill={i === 2 ? coral : cream}>
          <Hanzi x={71} y={35 + i * 51} size={21} fill={i === 2 ? '#fff' : ink}>{word}</Hanzi>
        </Tile>
      ))}
      {readings.map((reading, i) => (
        <Tile key={reading} x={208} y={17 + i * 51} width={88} fill={i === 0 ? '#fffaf0' : cream} stroke={i === 0 ? gold : undefined}>
          <Label x={252} y={35 + i * 51} size={16}>{reading}</Label>
        </Tile>
      ))}
      <Check x={160} y={60} /><Check x={160} y={111} />
      <circle cx="170" cy="88" r="5" fill={coral} />
      <Spark x={14} y={78} /><Spark x={309} y={64} fill={coral} /><Spark x={160} y={18} />
    </>
  )
}

function RadicalScene() {
  return (
    <>
      <Tile x={30} y={14} width={120} height={120}>
        <path d="M30 23a9 9 0 0 1 9-9h36v120H39a9 9 0 0 1-9-9z" fill={coral} opacity=".22" />
        <path d="M75 18v112" stroke={coral} strokeWidth="2.5" strokeDasharray="6 6" />
        <Hanzi x={90} y={74} size={88}>河</Hanzi>
      </Tile>
      <path d="M158 74h22m-8-8 9 8-9 8" fill="none" stroke={gold} strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      {['氵', '艹', '讠', '木'].map((radical, i) => {
        const x = 194 + (i % 2) * 58
        const y = 18 + Math.floor(i / 2) * 60
        return (
          <Tile key={radical} x={x} y={y} width={50} height={50} fill={i === 0 ? coral : cream} stroke={i === 0 ? '#ffe3cc' : undefined}>
            <Hanzi x={x + 25} y={y + 26} size={28} fill={i === 0 ? '#fff' : ink}>{radical}</Hanzi>
          </Tile>
        )
      })}
      <Check x={242} y={20} />
      <g fill={coral}>
        {[48, 70, 92].map((x, i) => <path key={x} opacity={i === 2 ? 0.28 : 1} d={`M${x} 160c-12-8-9-16-4-16 2 0 4 2 4 4 0-2 2-4 4-4 5 0 8 8-4 16z`} />)}
      </g>
      <Spark x={166} y={30} /><Spark x={306} y={148} fill={coral} /><Spark x={168} y={124} size={5} fill={coral} />
    </>
  )
}

function CatchScene() {
  return (
    <>
      <path d="M28 20h86a9 9 0 0 1 9 9v24a9 9 0 0 1-9 9H66L52 76l-4-14H28a9 9 0 0 1-9-9V29a9 9 0 0 1 9-9z" fill={cream} />
      <path d="M31 37h6l8-7v22l-8-7h-6z" fill={coral} /><path d="M50 34c4 4 4 10 0 14M55 30c7 6 7 16 0 22" fill="none" stroke={coral} strokeWidth="2.6" strokeLinecap="round" />
      <Label x={89} y={41} size={17}>shān</Label>
      <circle cx="70" cy="124" r="28" fill="#fffaf0" stroke={gold} strokeWidth="6" />
      <path d="M70 124V105M70 124l12 8" stroke={ink} strokeWidth="4" strokeLinecap="round" /><path d="M62 90h16" stroke={gold} strokeWidth="7" strokeLinecap="round" />
      {['木', '口', '水', '日', '山', '人'].map((char, i) => {
        const x = 138 + (i % 3) * 56
        const y = 26 + Math.floor(i / 3) * 62
        return (
          <Tile key={char} x={x} y={y} width={48} height={50} fill={i === 4 ? coral : cream}>
            <Hanzi x={x + 24} y={y + 26} size={28} fill={i === 4 ? '#fff' : ink}>{char}</Hanzi>
          </Tile>
        )
      })}
      <g fill="none" stroke="#fff" strokeWidth="3.5"><circle cx="218" cy="113" r="35" /><path d="M218 70v16M218 140v16M175 113h16M245 113h16" strokeLinecap="round" /></g>
      <Spark x={126} y={96} /><Spark x={309} y={18} fill={coral} />
    </>
  )
}

function RainScene() {
  return (
    <>
      <circle cx="42" cy="34" r="20" fill="#f7e4b8" />
      <path d="M-300 160l90-74 70 50 60-62 80 66 46-48 34 34 34-46 76 66 90-60 90 74z" fill="#1e4150" opacity=".8" />
      <path d="M20 20v22M98 12v26M150 30v20M74 62v20M186 6v24M128 88v20M12 92v18M170 78v22" stroke="#cfe6ff" strokeWidth="1.6" opacity=".55" strokeLinecap="round" />
      <path d="M-300 152c180-8 420 6 920-3v400h-920z" fill="#31566a" />
      <path d="M138 26v-14M152 22V6M88 62V46M102 66V48" stroke={gold} strokeWidth="2.4" opacity=".7" strokeLinecap="round" />
      <Hanzi x={145} y={50} size={42} fill="#fff8e9">木</Hanzi>
      <Hanzi x={95} y={88} size={34} fill="#fff8e9">大</Hanzi>
      <Hanzi x={38} y={84} size={26} fill="#fff8e9b0">口</Hanzi>
      <Panda x={62} y={118} scale={0.62}><path d="M26 34 46 8" stroke="#c9974f" strokeWidth="6" strokeLinecap="round" /><path d="M44 12 52-4" stroke={ink} strokeWidth="7" strokeLinecap="round" /></Panda>
      <WritingSquare x={196} y={28} size={108} />
      <Check x={300} y={32} r={13} />
      <Spark x={184} y={150} fill={coral} />
    </>
  )
}

function DartScene() {
  return (
    <>
      <path d="M-300 152c270-6 600 6 920-2v400h-920z" fill="#0f2c37" />
      <g stroke="#ffffff30" strokeWidth="2" fill="none"><circle cx="78" cy="70" r="48" /><circle cx="78" cy="70" r="62" strokeDasharray="4 8" /></g>
      <g transform="rotate(-8 70 60)">
        <clipPath id="dart-left"><path d="M20 10h52L60 62l16 12-10 60H20z" /></clipPath>
        <g clipPath="url(#dart-left)"><Hanzi x={76} y={70} size={84} fill="#fff3df">学</Hanzi></g>
      </g>
      <g transform="translate(9 3) rotate(9 90 70)">
        <clipPath id="dart-right"><path d="M72 10h60v124H66l10-60-16-12z" /></clipPath>
        <g clipPath="url(#dart-right)"><Hanzi x={76} y={70} size={84} fill="#fff3df">学</Hanzi></g>
      </g>
      <path d="m52 16 5 8-9 2zM118 120l8 4-7 6zM28 104l7 2-4 7zM122 28l7-3-1 8z" fill={gold} />
      <path d="M128 66h52M136 80h34M140 54h26" stroke={gold} strokeWidth="3" strokeLinecap="round" opacity=".75" />
      <g transform="translate(112 68) rotate(6)"><path d="M0 0 36-8l8 8-8 8z" fill="#e8f0f4" /><path d="M43-4h16v8H43z" fill="#8a4b32" /><path d="m59 0 9-8v16z" fill={coral} /></g>
      <Panda x={248} y={78} scale={1}><path d="M-24 36-50 14" stroke="#2c3b48" strokeWidth="13" strokeLinecap="round" /><circle cx="-52" cy="12" r="8" fill="#fffdf6" /></Panda>
      <Tile x={108} y={118} width={92} height={32}>
        <Label x={146} y={135} size={17}>học</Label><path d="M170 126v16" stroke={coral} strokeWidth="2.5" />
        <path d="M188 128v6h-9m4-4-4 4 4 4" fill="none" stroke={navy} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </Tile>
      <Spark x={196} y={26} /><Spark x={304} y={40} fill={coral} />
    </>
  )
}

const scenes: Readonly<Record<string, () => JSX.Element>> = {
  CHAR_RAIN: InkJarScene,
  PINYIN_MATCH: PinyinScene,
  RADICAL_MATCH: RadicalScene,
  CHAR_CATCH: CatchScene,
  BRUSH_STROKE: RainScene,
  FLYING_DART: DartScene,
}

export function GameScene({ code }: { code: string }) {
  const Drawing = scenes[code]
  return (
    <div className={`game-scene game-scene-${gameMeta(code).slug}`} aria-hidden="true">
      {Drawing
        ? <svg viewBox="0 0 320 170" focusable="false"><Drawing /></svg>
        : <span className="game-scene-symbol">{gameMeta(code).symbol}</span>}
    </div>
  )
}