// Presentation pieces of the CNHSK Arcade look (styles live in styles/arcade.css).
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
