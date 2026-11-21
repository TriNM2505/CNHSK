// Presentation pieces of the CNHSK Arcade look (styles live in styles/arcade.css).
import { gameMeta } from './gameMeta'

export function Scene({ variant = 'brush' }: { variant?: 'brush' | 'dart' | 'rain' }) {
  return (
    <div className={`scene scene-${variant}`} aria-hidden="true">
      <div className="moon" /><div className="stars" /><div className="mountain mountain-back" /><div className="mountain mountain-front" />
      <div className="house"><div className="roof" /><div className="window" /><div className="door" /></div>
      <div className="panda">
        <div className="ear ear-left" /><div className="ear ear-right" />
        <div className="panda-face"><span className="eye eye-left" /><span className="eye eye-right" /><span className="nose" /></div>
        <div className="panda-body" /><div className="panda-brush" />
      </div>
      <div className="ground" />
    </div>
  )
}

/** The illustration shown on a game's start panel. */
export function GameArtwork({ code }: { code: string }) {
  if (code === 'PINYIN_MATCH') {
    return (
      <div className="game-art game-art-match" aria-hidden="true">
        <span className="art-spark art-spark-a">✦</span><span className="art-spark art-spark-b">✧</span>
        <div className="art-card art-card-left"><b>朋友</b><small>bạn bè</small></div>
        <svg viewBox="0 0 240 90"><path d="M5 70 C80 0 140 110 235 20" /><circle cx="5" cy="70" r="6" /><circle cx="235" cy="20" r="6" /></svg>
        <div className="art-card art-card-right"><b>péngyou</b><small>ghép đúng ✓</small></div>
      </div>
    )
  }
  if (code === 'RADICAL_MATCH') {
    return (
      <div className="game-art game-art-radical" aria-hidden="true">
        <span className="art-spark art-spark-a">✦</span>
        <div className="art-big-char">河</div>
        <div className="art-radical">氵 <span>＋</span> 可</div>
        <div className="art-chip">BỘ THỦ · 氵</div>
      </div>
    )
  }
  if (code === 'CHAR_CATCH') {
    return (
      <div className="game-art game-art-catch" aria-hidden="true">
        <span className="art-target">◎</span>
        <div className="art-cue">shān · núi</div>
        <div className="art-target-grid"><span>木</span><span>口</span><strong>山</strong><span>水</span><span>日</span><span>人</span></div>
        <span className="art-spark art-spark-b">✦</span>
      </div>
    )
  }
  return <div className="game-art" aria-hidden="true">{gameMeta(code).symbol}</div>
}
