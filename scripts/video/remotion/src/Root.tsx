import React from "react";
import { CalculateMetadataFunction, Composition } from "remotion";
import { Closing, ClosingProps, Opening, OpeningProps, Punchline, PunchlineProps } from "./Cards";
import { FPS, HEIGHT, WIDTH } from "./theme";

// La durée d'un carton vient de ses props (`seconds`), donc du fichier du teaser, pas d'ici.
function bySeconds<T extends { seconds: number }>(): CalculateMetadataFunction<T> {
  return ({ props }) => ({ durationInFrames: Math.max(1, Math.round(props.seconds * FPS)) });
}

const frame = { fps: FPS, width: WIDTH, height: HEIGHT, durationInFrames: 1 };

export const RemotionRoot: React.FC = () => (
  <>
    <Composition<any, OpeningProps>
      id="Opening"
      component={Opening}
      {...frame}
      defaultProps={{ line: "Vos flux métier, du courrier à la signature.", seconds: 4 }}
      calculateMetadata={bySeconds<OpeningProps>()}
    />
    <Composition<any, PunchlineProps>
      id="Punchline"
      component={Punchline}
      {...frame}
      defaultProps={{ word: "flow", tagline: "Décrivez le parcours, Myrmacta l'orchestre.", seconds: 3.5 }}
      calculateMetadata={bySeconds<PunchlineProps>()}
    />
    <Composition<any, ClosingProps>
      id="Closing"
      component={Closing}
      {...frame}
      defaultProps={{
        lines: [
          "Des flux métier configurés, pas développés.",
          "Conçus par l'intégrateur, ajustés par le métier.",
          "Avec une IA qui peut tourner sur vos postes.",
        ],
        url: "myrmacta.be",
        seconds: 6,
      }}
      calculateMetadata={bySeconds<ClosingProps>()}
    />
  </>
);
