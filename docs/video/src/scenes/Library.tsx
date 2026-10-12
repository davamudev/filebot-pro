import React from 'react';
import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {ArrowDown, ChevronRight, Film, Folder, Sparkles, Tv} from 'lucide-react';
import {clamp, enter, pop} from '../anim';
import {Heading, Scene} from '../components/ui';
import {Strings} from '../i18n';
import {C, MONO} from '../theme';

const SERVICES = [
	['TheMovieDB', 'Movies & TV'],
	['TheTVDB', 'TV episodes'],
	['TVmaze', 'TV episodes'],
	['AniDB', 'Anime'],
	['OMDb', 'Movies'],
	['AcoustID', 'Music'],
	['OpenSubtitles', 'Subtitles'],
	['Fanart.tv', 'Artwork'],
];

export const Databases: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	return (
		<Scene>
			<Heading top={250} sub={s.databases.subtitle}>{s.databases.title}</Heading>
			<div style={{position: 'absolute', top: 540, left: 160, right: 160, display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 28}}>
				{SERVICES.map(([name, what], i) => {
					const p = pop(frame, fps, 16 + i * 4, {damping: 14});
					const float = Math.sin(frame / 20 + i) * 4;
					return (
						<div key={name} style={{
							height: 130, borderRadius: 18, background: 'rgba(255,255,255,0.035)', border: `1.5px solid ${C.border}`,
							display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8,
							opacity: p, transform: `translateY(${interpolate(p, [0, 1], [50, 0]) + float}px) scale(${interpolate(p, [0, 1], [0.8, 1])})`,
						}}>
							<div style={{fontSize: 36, fontWeight: 800}}>{name}</div>
							<div style={{fontSize: 20, color: C.accent, fontWeight: 600, textTransform: 'uppercase', letterSpacing: 2}}>{what}</div>
						</div>
					);
				})}
			</div>
		</Scene>
	);
};

const EXAMPLES = [
	{key: 'movies', Icon: Film, before: 'the.matrix.1999.2160p.uhd.bluray.x265.mkv', after: ['Movies', 'The Matrix (1999)', 'The Matrix (1999).mkv']},
	{key: 'tv', Icon: Tv, before: 'Breaking.Bad.S02E03.1080p.WEB-DL.mkv', after: ['TV Shows', 'Breaking Bad', 'Season 02', 'Breaking Bad - S02E03 - Bit by a Dead Bee.mkv']},
	{key: 'anime', Icon: Sparkles, before: '[Fansub] Cowboy Bebop - 05 [1080p].mkv', after: ['Anime', 'Cowboy Bebop', 'Cowboy Bebop - 005 - Ballad of Fallen Angels.mkv']},
] as const;

export const Examples: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	return (
		<Scene>
			<Heading top={70}>{s.examples.title}</Heading>
			<div style={{position: 'absolute', top: 200, bottom: 50, left: 100, right: 100, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 44}}>
				{EXAMPLES.map(({key, Icon, before, after}, i) => {
					const d = 14 + i * 62;
					const card = enter(frame, fps, d, 60);
					const strike = interpolate(frame, [d + 18, d + 30], [0, 100], clamp);
					return (
						<div key={key} style={{
							borderRadius: 22, padding: '38px 40px', background: 'rgba(28,28,32,0.85)', border: `1.5px solid ${C.border}`, display: 'flex', gap: 32, ...card,
						}}>
							<div style={{width: 150, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, paddingTop: 6}}>
								<div style={{width: 84, height: 84, borderRadius: 20, background: C.accentSoft, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
									<Icon size={44} color={C.accent}/>
								</div>
								<div style={{fontSize: 26, fontWeight: 700}}>{s.examples[key]}</div>
							</div>
							<div style={{flex: 1, display: 'flex', flexDirection: 'column', gap: 12, minWidth: 0}}>
								<div style={{display: 'flex', alignItems: 'center', gap: 16}}>
									<span style={{fontSize: 18, color: C.dim, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 2, width: 130}}>{s.examples.before}</span>
									<span style={{position: 'relative', fontFamily: MONO, fontSize: 30, color: C.muted}}>
										{before}
										<span style={{position: 'absolute', left: 0, top: '52%', height: 2.5, width: `${strike}%`, background: '#EF4444'}}/>
									</span>
								</div>
								<ArrowDown size={26} color={C.accent} style={{marginLeft: 150, opacity: interpolate(frame, [d + 26, d + 32], [0, 1], clamp)}}/>
								<div style={{display: 'flex', alignItems: 'center', gap: 16}}>
									<span style={{fontSize: 18, color: C.accent, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 2, width: 130}}>{s.examples.after}</span>
									<div style={{display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'nowrap'}}>
										{after.map((seg, j) => {
											const last = j === after.length - 1;
											const sp = pop(frame, fps, d + 32 + j * 5, {damping: 16});
											return (
												<React.Fragment key={seg}>
													{j > 0 && <ChevronRight size={22} color={C.dim} style={{opacity: sp, flexShrink: 0}}/>}
													<span style={{
														display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 14px', borderRadius: 9, whiteSpace: 'nowrap',
														fontSize: 24, fontWeight: last ? 700 : 500, color: last ? C.text : '#FDBA74',
														background: last ? 'rgba(34,197,94,0.12)' : 'rgba(249,115,22,0.10)',
														border: `1px solid ${last ? 'rgba(34,197,94,0.45)' : 'rgba(249,115,22,0.25)'}`,
														opacity: sp, transform: `translateY(${(1 - sp) * 14}px)`,
													}}>
														{!last && <Folder size={20}/>}{seg}
													</span>
												</React.Fragment>
											);
										})}
									</div>
								</div>
							</div>
						</div>
					);
				})}
			</div>
		</Scene>
	);
};
