import React from 'react';
import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Captions, FileCheck2, Filter, Image, ListVideo, SunMoon, X} from 'lucide-react';
import {clamp, enter, pop, typed} from '../anim';
import {Heading, Logo, Pill, Scene} from '../components/ui';
import {Strings} from '../i18n';
import {C, MONO} from '../theme';

// Expressions and results from the format editor in the app
const FORMATS = [
	['{n} - {s00e00} - {t}', 'Firefly - S01E01 - Serenity'],
	['{n}/Season {s}/{n} - {sxe} - {t}', 'Firefly/Season 1/Firefly - 1x01 - Serenity'],
	['{plex}', 'TV Shows/Firefly/Season 01/Firefly - S01E01 - Serenity'],
];
const CYCLE = 72;

const Expression: React.FC<{text: string}> = ({text}) => (
	<>
		{text.split(/(\{[^}]*\}?)/).map((part, i) => (
			<span key={i} style={{color: part.startsWith('{') ? '#FDBA74' : C.text}}>{part}</span>
		))}
	</>
);

const Path: React.FC<{text: string}> = ({text}) => (
	<>
		{text.split('/').map((seg, i, all) => (
			<React.Fragment key={i}>
				<span>{seg}</span>
				{i < all.length - 1 && <span style={{color: C.accent, margin: '0 6px'}}>/</span>}
			</React.Fragment>
		))}
	</>
);

export const Format: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const local = frame - 20;
	const idx = Math.max(0, Math.min(FORMATS.length - 1, Math.floor(local / CYCLE)));
	const start = 20 + idx * CYCLE;
	const [expr, result] = FORMATS[idx];
	const shown = typed(expr, frame, start, 0.9);
	const doneAt = start + expr.length / 0.9;
	const resultIn = interpolate(frame, [doneAt, doneAt + 8], [0, 1], clamp);
	const prev = idx > 0 ? FORMATS[idx - 1][1] : '';
	const caret = Math.floor(frame / 15) % 2 === 0;
	const card = pop(frame, fps, 4);

	return (
		<Scene>
			<div style={{
				position: 'absolute', left: 110, top: 290, width: 960, borderRadius: 16, overflow: 'hidden', background: C.window,
				border: `1px solid ${C.border}`, boxShadow: '0 40px 120px rgba(0,0,0,0.6)', opacity: card,
				transform: `translateX(${interpolate(card, [0, 1], [-80, 0])}px)`,
			}}>
				<div style={{height: 50, background: C.titleBar, display: 'flex', alignItems: 'center', gap: 12, padding: '0 18px', fontSize: 19, color: C.muted}}>
					<Logo size={22} style={{borderRadius: 4}}/>Episode Format<div style={{flex: 1}}/><X size={20}/>
				</div>
				<div style={{padding: 30}}>
					<div style={{
						height: 84, borderRadius: 10, border: `2px solid ${C.accent}`, background: C.surface, display: 'flex', alignItems: 'center',
						padding: '0 24px', fontFamily: MONO, fontSize: 32, whiteSpace: 'pre',
					}}>
						<Expression text={shown}/>
						<span style={{width: 3, height: 38, background: caret ? C.text : 'transparent', marginLeft: 2}}/>
					</div>
					<div style={{marginTop: 28, fontSize: 18, fontWeight: 700, color: C.dim, textTransform: 'uppercase', letterSpacing: 2}}>{s.format.preview}</div>
					<div style={{
						marginTop: 12, minHeight: 110, borderRadius: 10, background: 'rgba(249,115,22,0.07)', border: `1px solid rgba(249,115,22,0.25)`,
						padding: '22px 24px', fontSize: 32, fontWeight: 600, position: 'relative',
					}}>
						<div style={{opacity: 1 - resultIn, position: 'absolute', left: 24, top: 22}}><Path text={prev}/></div>
						<div style={{opacity: resultIn, transform: `translateY(${(1 - resultIn) * 12}px)`}}><Path text={result}/></div>
					</div>
					<div style={{marginTop: 26, display: 'flex', gap: 12, flexWrap: 'wrap', fontSize: 20, color: C.muted}}>
						{[['{n}', 'name'], ['{s}', 'season'], ['{e}', 'episode'], ['{t}', 'title'], ['{y}', 'year'], ['{vf}', 'resolution']].map(([b, d]) => (
							<span key={b} style={{padding: '6px 12px', borderRadius: 8, background: C.surface, border: `1px solid ${C.border}`}}>
								<span style={{fontFamily: MONO, color: '#FDBA74', fontWeight: 700}}>{b}</span> {d}
							</span>
						))}
					</div>
				</div>
			</div>

			<div style={{position: 'absolute', left: 1140, top: 300, width: 680}}>
				<div style={{fontSize: 72, fontWeight: 900, letterSpacing: -2, lineHeight: 1.05, ...enter(frame, fps, 10)}}>{s.format.title}</div>
				<div style={{fontSize: 30, color: C.muted, marginTop: 28, lineHeight: 1.45, ...enter(frame, fps, 18)}}>{s.format.subtitle}</div>
				<div style={{fontSize: 24, color: C.dim, marginTop: 50, lineHeight: 1.4, ...enter(frame, fps, 30)}}>{s.format.presets}</div>
				<div style={{display: 'flex', flexWrap: 'wrap', gap: 14, marginTop: 20}}>
					{['{plex}', '{jellyfin}', '{emby}', '{kodi}'].map((p, i) => (
						<div key={p} style={enter(frame, fps, 36 + i * 5, 20)}>
							<Pill active={i === 0 && idx === 2} style={{fontFamily: MONO, fontSize: 26}}>{p}</Pill>
						</div>
					))}
				</div>
			</div>
		</Scene>
	);
};

const FEATURE_ICONS = [Captions, FileCheck2, Filter, ListVideo, Image, SunMoon];

export const Features: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	return (
		<Scene>
			<Heading top={70}>{s.features.title}</Heading>
			<div style={{position: 'absolute', top: 215, left: 130, right: 130, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 32}}>
				{s.features.items.map(({title, text}, i) => {
					const Icon = FEATURE_ICONS[i];
					const p = pop(frame, fps, 14 + i * 7, {damping: 15});
					const lift = Math.sin(frame / 22 + i * 1.3) * 3;
					return (
						<div key={title} style={{
							height: 365, borderRadius: 22, padding: 40, background: 'rgba(28,28,32,0.85)', border: `1.5px solid ${C.border}`,
							opacity: p, transform: `translateY(${interpolate(p, [0, 1], [70, 0]) + lift}px) scale(${interpolate(p, [0, 1], [0.9, 1])})`,
						}}>
							<div style={{width: 84, height: 84, borderRadius: 20, background: C.accentSoft, border: `1.5px solid ${C.accentLine}`, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
								<Icon size={44} color={C.accent} strokeWidth={1.8}/>
							</div>
							<div style={{fontSize: 40, fontWeight: 800, marginTop: 30}}>{title}</div>
							<div style={{fontSize: 25, color: C.muted, marginTop: 14, lineHeight: 1.4}}>{text}</div>
						</div>
					);
				})}
			</div>
		</Scene>
	);
};
