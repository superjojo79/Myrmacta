// Les cinq cartons du teaser (TODO §148, D-VIDEO-REMOTION, issue #59) : ouverture, punchline « Myrmacta <mot> » (trois
// fois) et fin. Chaque valeur animée dérive de useCurrentFrame() : Remotion rend chaque image séparément, une animation
// CSS ne serait pas rendue. Les textes arrivent en props (scripts/video/cards.py les lit dans t00-teaser.json).
import React from "react";
import {
  AbsoluteFill,
  Easing,
  Img,
  interpolate,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { background, colors, EASE_OUT, fontFamily, LOGO } from "./theme";

const ease = Easing.bezier(...EASE_OUT);

/** 0 → 1 entre `from` et `from + duration` (secondes), avec la sortie douce. */
function useReveal(from: number, duration = 0.6): number {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return interpolate(frame, [from * fps, (from + duration) * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: ease,
  });
}

const Stage: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <AbsoluteFill
    style={{
      background,
      color: colors.text,
      fontFamily,
      justifyContent: "center",
      alignItems: "center",
      textAlign: "center",
    }}
  >
    {children}
  </AbsoluteFill>
);

/** Une ligne qui monte de quelques pixels en apparaissant. */
const Rise: React.FC<{ at: number; style?: React.CSSProperties; children: React.ReactNode }> = ({
  at,
  style,
  children,
}) => {
  const p = useReveal(at);
  return <div style={{ opacity: p, translate: `0px ${(1 - p) * 24}px`, ...style }}>{children}</div>;
};

const Logo: React.FC<{ at: number; width: number }> = ({ at, width }) => {
  const p = useReveal(at, 0.9);
  return (
    <Img
      src={staticFile(LOGO)}
      style={{ width, height: "auto", opacity: p, scale: `${0.94 + 0.06 * p}` }}
    />
  );
};

export type OpeningProps = { line: string; seconds: number };

/** Ouverture : le logo, puis la promesse du teaser. */
export const Opening: React.FC<OpeningProps> = ({ line }) => (
  <Stage>
    <Logo at={0.1} width={760} />
    <Rise at={1.0} style={{ marginTop: 48, fontSize: 64, fontWeight: 600, maxWidth: 1500, lineHeight: 1.2 }}>
      {line}
    </Rise>
  </Stage>
);

export type PunchlineProps = { word: string; tagline: string; seconds: number };

/** Punchline : « Myrmacta » posé, puis le mot du thème tapé à sa suite, puis l'accroche. */
export const Punchline: React.FC<PunchlineProps> = ({ word, tagline }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const brand = useReveal(0, 0.5);
  const typingStart = 0.45 * fps;
  const perChar = 0.07 * fps;
  const typed = Math.max(0, Math.min(word.length, Math.floor((frame - typingStart) / perChar) + 1));
  const typingDone = typingStart + word.length * perChar;
  // Curseur : visible pendant la frappe, puis clignote deux fois et s'efface.
  const cursorOn = frame < typingDone || (frame < typingDone + 1.2 * fps && Math.floor((frame - typingDone) / (0.3 * fps)) % 2 === 1);
  return (
    <Stage>
      <div style={{ fontSize: 168, fontWeight: 700, letterSpacing: "-0.02em", lineHeight: 1 }}>
        <span style={{ opacity: brand }}>Myrmacta</span>
        <span style={{ color: colors.accent }}>
          {frame >= typingStart ? " " + word.slice(0, typed) : ""}
        </span>
        <span
          style={{
            display: "inline-block",
            width: 10,
            height: 140,
            marginLeft: 8,
            verticalAlign: "-0.06em",
            background: colors.accent,
            opacity: cursorOn && frame >= typingStart ? 1 : 0,
          }}
        />
      </div>
      <Rise at={(typingDone / fps) + 0.15} style={{ marginTop: 40, fontSize: 56, color: colors.muted, maxWidth: 1500 }}>
        {tagline}
      </Rise>
    </Stage>
  );
};

export type ClosingProps = { lines: string[]; url: string; seconds: number };

/** Fin : le logo, le positionnement ligne à ligne, puis l'adresse du site. */
export const Closing: React.FC<ClosingProps> = ({ lines, url }) => {
  const last = 0.9 + lines.length * 0.7;
  return (
    <Stage>
      <Logo at={0} width={560} />
      <div style={{ marginTop: 56, display: "flex", flexDirection: "column", gap: 22 }}>
        {lines.map((text, i) => (
          <Rise key={i} at={0.9 + i * 0.7} style={{ fontSize: 54, fontWeight: i === 0 ? 700 : 500, maxWidth: 1600 }}>
            {text}
          </Rise>
        ))}
      </div>
      <Rise at={last} style={{ position: "absolute", bottom: 72, fontSize: 36, color: colors.accent, letterSpacing: "0.06em" }}>
        {url}
      </Rise>
    </Stage>
  );
};
