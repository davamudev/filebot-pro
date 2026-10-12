import React from 'react';
import {AbsoluteFill, Audio, interpolate, staticFile, useVideoConfig} from 'remotion';
import {linearTiming, TransitionPresentation, TransitionSeries} from '@remotion/transitions';
import {fade} from '@remotion/transitions/fade';
import {slide} from '@remotion/transitions/slide';
import {wipe} from '@remotion/transitions/wipe';
import {Background} from './components/ui';
import {Lang, STRINGS, Strings} from './i18n';
import {Chaos, Free, LogoReveal} from './scenes/Intro';
import {Demo} from './scenes/Demo';
import {Databases, Examples} from './scenes/Library';
import {Features, Format} from './scenes/Power';
import {Cli, Cta, Platforms} from './scenes/Outro';
import timeline from './timeline.json';

type SceneKey = keyof typeof timeline.scenes;

const SCENES: [SceneKey, React.FC<{s: Strings}>][] = [
	['chaos', Chaos],
	['logo', LogoReveal],
	['free', Free],
	['demo', Demo],
	['databases', Databases],
	['examples', Examples],
	['format', Format],
	['features', Features],
	['cli', Cli],
	['platforms', Platforms],
	['cta', Cta],
];

// Transition into each scene (index 1..n)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const TRANSITIONS: TransitionPresentation<any>[] = [
	fade(), fade(), slide({direction: 'from-right'}), fade(), slide({direction: 'from-bottom'}),
	fade(), slide({direction: 'from-right'}), wipe({direction: 'from-left'}), fade(), fade(),
];

export const TOTAL_FRAMES = Object.values(timeline.scenes).reduce((a, b) => a + b, 0) - timeline.transition * (SCENES.length - 1);

export const Promo: React.FC<{lang: Lang; music: boolean}> = ({lang, music}) => {
	const s = STRINGS[lang];
	const {durationInFrames} = useVideoConfig();
	const children: React.ReactNode[] = [];
	SCENES.forEach(([key, Component], i) => {
		if (i > 0) {
			children.push(
				<TransitionSeries.Transition key={`t-${key}`} presentation={TRANSITIONS[i - 1]} timing={linearTiming({durationInFrames: timeline.transition})}/>,
			);
		}
		children.push(
			<TransitionSeries.Sequence key={key} durationInFrames={timeline.scenes[key]}>
				<Component s={s}/>
			</TransitionSeries.Sequence>,
		);
	});
	return (
		<AbsoluteFill>
			<style>{'* { box-sizing: border-box; font-feature-settings: "calt" 0; }'}</style>
			<Background/>
			<TransitionSeries>{children}</TransitionSeries>
			{music && (
				<Audio src={staticFile('music.wav')} volume={(f) => interpolate(f, [0, 10, durationInFrames - 40, durationInFrames], [0, 0.9, 0.9, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'})}/>
			)}
		</AbsoluteFill>
	);
};
