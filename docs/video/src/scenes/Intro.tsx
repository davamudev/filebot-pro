import React from 'react';
import {AbsoluteFill, interpolate, random, useCurrentFrame, useVideoConfig} from 'remotion';
import {Check, Scale} from 'lucide-react';
import {clamp, ease, enter, fadeOut, pop} from '../anim';
import {Accent, Logo, Pill, Scene, Wordmark} from '../components/ui';
import {Strings} from '../i18n';
import {C, MONO} from '../theme';

const MESSY = [
	'Breaking.Bad.S02E03.720p.BluRay.x264.mkv',
	'the.matrix.1999.2160p.uhd.bluray.x265.mkv',
	'Firefly.1x05.Safe.avi',
	'[Fansub] Cowboy Bebop - 05 [1080p].mkv',
	'Severance.S01E01.Good.News.About.Hell.2160p.mkv',
	'Spirited.Away.2001.720p.BluRay.mkv',
	'The.Office.US.S03E10.HDTV.mkv',
	'Blade.Runner.2049.2017.2160p.UHD.BluRay.x265.mkv',
	'movie_final_FINAL(2).mkv',
	'The.Mandalorian.S02E01.1080p.WEB.mkv',
	'Dune.Part.Two.2024.1080p.WEB-DL.mkv',
	'New Folder (3)',
	'Doctor.Who.S01E01.mkv',
	'VTS_01_1.VOB',
	'Show.S01E01E02.mkv',
	'download_8f3a2c.part',
	'sample.mkv',
	'Untitled (copy).avi',
];

export const Chaos: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const suck = interpolate(frame, [118, 156], [0, 1], {...clamp, easing: ease});
	const textOut = fadeOut(frame, 118, 14);
	return (
		<Scene>
			{MESSY.map((name, i) => {
				const col = i % 3;
				const row = Math.floor(i / 3);
				const baseX = 120 + col * 600 + random(`x${i}`) * 220;
				const baseY = 70 + row * 165 + random(`y${i}`) * 50;
				const drift = Math.sin(frame / 25 + i) * 14;
				const x = interpolate(suck, [0, 1], [baseX + drift, 960]);
				const y = interpolate(suck, [0, 1], [baseY + Math.cos(frame / 30 + i) * 10, 540]);
				const appear = interpolate(frame, [i * 3, i * 3 + 14], [0, 1], clamp);
				const flicker = 0.55 + 0.45 * Math.abs(Math.sin(frame / 9 + i * 1.7));
				const rot = (random(`r${i}`) - 0.5) * 10 * (1 - suck);
				return (
					<div key={name} style={{
						position: 'absolute', left: x, top: y, fontFamily: MONO, fontSize: 24 + (i % 4) * 3,
						color: i % 5 === 0 ? '#FB923C' : C.dim, whiteSpace: 'nowrap',
						opacity: appear * flicker * (1 - suck), transform: `translate(-${suck * 50}%, -${suck * 50}%) rotate(${rot}deg) scale(${1 - suck * 0.7})`,
						padding: '8px 14px', borderRadius: 8, background: 'rgba(255,255,255,0.03)', border: `1px solid rgba(255,255,255,0.06)`,
					}}>{name}</div>
				);
			})}
			<AbsoluteFill style={{
				justifyContent: 'center', alignItems: 'center', textAlign: 'center', opacity: textOut,
				background: 'radial-gradient(ellipse 900px 360px at center, rgba(12,12,15,0.95) 30%, rgba(12,12,15,0) 100%)',
			}}>
				<div style={{fontSize: 84, fontWeight: 900, letterSpacing: -3, maxWidth: 1760, lineHeight: 1.05, ...enter(frame, fps, 14, 50)}}>{s.chaos.title}</div>
				<div style={{fontSize: 36, color: C.muted, marginTop: 28, ...enter(frame, fps, 40)}}>{s.chaos.subtitle}</div>
			</AbsoluteFill>
		</Scene>
	);
};

export const LogoReveal: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const p = pop(frame, fps, 4, {damping: 11, mass: 0.9, stiffness: 120});
	const ring = interpolate(frame, [4, 40], [0, 1], clamp);
	const glow = 0.5 + 0.2 * Math.sin(frame / 12);
	return (
		<Scene style={{justifyContent: 'center', alignItems: 'center'}}>
			<div style={{
				position: 'absolute', width: 900, height: 900, borderRadius: '50%', top: 40, left: 510,
				background: `radial-gradient(circle, rgba(249,115,22,${glow * 0.45}) 0%, rgba(249,115,22,0) 60%)`,
			}}/>
			<div style={{
				position: 'absolute', left: 960 - 400 * ring, top: 380 - 400 * ring, width: 800 * ring, height: 800 * ring, borderRadius: '50%',
				border: `3px solid rgba(249,115,22,${(1 - ring) * 0.8})`,
			}}/>
			<div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', marginTop: -40}}>
				<Logo size={300} style={{
					borderRadius: 40, transform: `scale(${interpolate(p, [0, 1], [0.3, 1])}) rotate(${interpolate(p, [0, 1], [-25, 0])}deg)`,
					opacity: interpolate(frame, [4, 12], [0, 1], clamp), boxShadow: '0 30px 90px rgba(249,115,22,0.35)',
				}}/>
				<div style={{marginTop: 50, ...enter(frame, fps, 22, 50)}}><Wordmark size={120}/></div>
				<div style={{fontSize: 38, color: C.muted, marginTop: 30, maxWidth: 1400, textAlign: 'center', lineHeight: 1.35, ...enter(frame, fps, 38)}}>
					{s.logo.tagline}
				</div>
			</div>
		</Scene>
	);
};

export const Free: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	return (
		<Scene style={{justifyContent: 'center', alignItems: 'center'}}>
			<div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6}}>
				{s.free.words.map((w, i) => {
					const p = pop(frame, fps, 6 + i * 16, {damping: 14, stiffness: 140});
					return (
						<div key={w} style={{
							fontSize: 150, fontWeight: 900, letterSpacing: -5, lineHeight: 1.05,
							opacity: interpolate(p, [0, 1], [0, 1], clamp),
							transform: `translateY(${interpolate(p, [0, 1], [90, 0])}px) scale(${interpolate(p, [0, 1], [0.85, 1])})`,
						}}>{i === 1 ? <Accent>{w}</Accent> : w}</div>
					);
				})}
				<div style={{display: 'flex', gap: 20, marginTop: 60}}>
					{s.free.badges.map((b, i) => (
						<div key={b} style={enter(frame, fps, 60 + i * 6, 30)}>
							<Pill active={i === 0}>{i === 0 ? <Scale size={28}/> : <Check size={28} color={C.green}/>} {b}</Pill>
						</div>
					))}
				</div>
			</div>
		</Scene>
	);
};
