import React from 'react';
import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {BookOpen, Container, Github, HardDrive, Laptop, Monitor, Star, Terminal} from 'lucide-react';
import {clamp, enter, pop, typed} from '../anim';
import {Heading, Logo, Pill, Scene, Wordmark} from '../components/ui';
import {Strings} from '../i18n';
import {C, GRADIENT, MONO} from '../theme';

const COMMAND = 'filebot-pro -script fn:amc /downloads --output /media --action move -non-strict';

// Output lines of the rename log ([MOVE] from [...] to [...]), split in two lines to fit the screen
const OUTPUT: [string, string][] = [
	['[MOVE] from ', '[/downloads/the.matrix.1999.2160p.uhd.bluray.x265.mkv]'],
	['         to ', '[/media/Movies/The Matrix (1999)/The Matrix (1999).mkv]'],
	['[MOVE] from ', '[/downloads/Breaking.Bad.S02E03.1080p.WEB-DL.mkv]'],
	['         to ', '[/media/TV Shows/Breaking Bad/Season 02/Breaking Bad - S02E03 - Bit by a Dead Bee.mkv]'],
	['[MOVE] from ', '[/downloads/Spirited.Away.2001.720p.BluRay.mkv]'],
	['         to ', '[/media/Movies/Spirited Away (2001)/Spirited Away (2001).mkv]'],
];

export const Cli: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const typeStart = 24;
	const speed = 1.6;
	const typedDone = typeStart + COMMAND.length / speed;
	const caret = Math.floor(frame / 15) % 2 === 0;
	const term = pop(frame, fps, 6);
	return (
		<Scene>
			<Heading top={60} sub={s.cli.subtitle}>{s.cli.title}</Heading>
			<div style={{
				position: 'absolute', left: 190, top: 300, width: 1540, height: 500, borderRadius: 16, overflow: 'hidden', background: '#0A0A0C',
				border: `1px solid ${C.border}`, boxShadow: '0 40px 120px rgba(0,0,0,0.7)', opacity: term,
				transform: `translateY(${interpolate(term, [0, 1], [60, 0])}px)`,
			}}>
				<div style={{height: 48, background: C.titleBar, display: 'flex', alignItems: 'center', gap: 10, padding: '0 18px'}}>
					{['#EF4444', '#EAB308', '#22C55E'].map((c) => <div key={c} style={{width: 14, height: 14, borderRadius: '50%', background: c}}/>)}
					<div style={{flex: 1, textAlign: 'center', fontSize: 18, color: C.dim, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 8}}>
						<Terminal size={18}/> bash
					</div>
				</div>
				<div style={{padding: '26px 30px', fontFamily: MONO, fontSize: 24, lineHeight: 1.7, whiteSpace: 'pre'}}>
					<div>
						<span style={{color: C.green}}>user@nas</span><span style={{color: C.dim}}>:~$ </span>
						<span>{typed(COMMAND, frame, typeStart, speed)}</span>
						{frame < typedDone + 8 && <span style={{background: caret ? C.text : 'transparent'}}> </span>}
					</div>
					{OUTPUT.map(([a, b], i) => {
						const at = typedDone + 12 + i * 9;
						if (frame < at) return null;
						return (
							<div key={i} style={{opacity: interpolate(frame, [at, at + 4], [0, 1], clamp)}}>
								<span style={{color: C.accent}}>{a}</span>
								<span style={{color: i % 2 ? C.text : C.muted}}>{b}</span>
							</div>
						);
					})}
					{frame >= typedDone + 12 + OUTPUT.length * 9 + 6 && <div style={{color: C.green, marginTop: 6}}>Processed 3 files</div>}
				</div>
			</div>
			<div style={{position: 'absolute', top: 870, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 18}}>
				{s.cli.tags.map((t, i) => (
					<div key={t} style={enter(frame, fps, 40 + i * 6, 20)}><Pill style={{fontSize: 24, padding: '10px 24px'}}>{t}</Pill></div>
				))}
			</div>
		</Scene>
	);
};

const PLATFORMS = [
	['Windows', Monitor, '10 / 11 · x64 · ARM64'],
	['macOS', Laptop, 'Apple Silicon · Intel'],
	['Linux', Terminal, '.deb · .rpm · portable'],
	['Docker', Container, 'Headless image'],
	['Synology', HardDrive, 'DSM 7 package'],
] as const;

export const Platforms: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	return (
		<Scene>
			<Heading top={260} sub={s.platforms.subtitle}>{s.platforms.title}</Heading>
			<div style={{position: 'absolute', top: 540, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 30}}>
				{PLATFORMS.map(([name, Icon, detail], i) => {
					const p = pop(frame, fps, 14 + i * 5, {damping: 13});
					return (
						<div key={name} style={{
							width: 300, height: 260, borderRadius: 22, background: 'rgba(28,28,32,0.85)', border: `1.5px solid ${C.border}`,
							display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16,
							opacity: p, transform: `translateY(${interpolate(p, [0, 1], [60, 0])}px)`,
						}}>
							<Icon size={64} color={C.accent} strokeWidth={1.6}/>
							<div style={{fontSize: 38, fontWeight: 800}}>{name}</div>
							<div style={{fontSize: 20, color: C.dim}}>{detail}</div>
						</div>
					);
				})}
			</div>
		</Scene>
	);
};

export const Cta: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const logo = pop(frame, fps, 0, {damping: 12});
	const shine = interpolate(frame % 90, [0, 40], [-30, 130], clamp);
	return (
		<Scene style={{alignItems: 'center'}}>
			<div style={{marginTop: 110, display: 'flex', alignItems: 'center', gap: 40, opacity: logo, transform: `scale(${interpolate(logo, [0, 1], [0.7, 1])})`}}>
				<Logo size={170} style={{borderRadius: 26, boxShadow: '0 20px 70px rgba(249,115,22,0.35)'}}/>
				<Wordmark size={110}/>
			</div>
			<div style={{fontSize: 96, fontWeight: 900, letterSpacing: -3, marginTop: 70, ...enter(frame, fps, 12)}}>{s.cta.title}</div>
			<div style={{fontSize: 34, color: C.muted, marginTop: 16, ...enter(frame, fps, 20)}}>{s.cta.subtitle}</div>
			<div style={{display: 'flex', gap: 28, marginTop: 60, ...enter(frame, fps, 30)}}>
				<div style={{
					position: 'relative', overflow: 'hidden', display: 'flex', alignItems: 'center', gap: 16, padding: '24px 44px', borderRadius: 18,
					background: GRADIENT, fontSize: 38, fontWeight: 800, boxShadow: '0 20px 60px rgba(249,115,22,0.4)',
				}}>
					<BookOpen size={38}/>filebotpro.davamu.dev
					<div style={{
						position: 'absolute', top: 0, bottom: 0, width: 120, left: `${shine}%`,
						background: 'linear-gradient(90deg, rgba(255,255,255,0) 0%, rgba(255,255,255,0.35) 50%, rgba(255,255,255,0) 100%)', transform: 'skewX(-20deg)',
					}}/>
				</div>
				<div style={{
					display: 'flex', alignItems: 'center', gap: 16, padding: '24px 40px', borderRadius: 18, background: 'rgba(255,255,255,0.05)',
					border: `1.5px solid ${C.border}`, fontSize: 34, fontWeight: 700,
				}}>
					<Github size={36}/>github.com/davamudev/filebot-pro
				</div>
			</div>
			<div style={{display: 'flex', alignItems: 'center', gap: 12, marginTop: 44, fontSize: 28, color: C.text, ...enter(frame, fps, 42)}}>
				<Star size={30} color={C.yellow} fill={C.yellow} style={{transform: `rotate(${Math.sin(frame / 10) * 12}deg) scale(${1 + Math.sin(frame / 7) * 0.08})`}}/>
				{s.cta.star}
			</div>
			<div style={{position: 'absolute', bottom: 50, fontSize: 22, color: C.dim, ...enter(frame, fps, 50, 10)}}>{s.cta.footer}</div>
		</Scene>
	);
};
