import React from 'react';
import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {ArrowLeftRight, ArrowRight, CircleCheck, Loader} from 'lucide-react';
import {clamp, ease, enter, pop, progress} from '../anim';
import {AppWindow, Cursor, ExtBadge, Heading, Scene} from '../components/ui';
import {Strings} from '../i18n';
import {C} from '../theme';

// Same files and results as the screenshots in the docs
const FILES = [
	['Breaking.Bad.S02E03.720p.BluRay.x264', 'Breaking Bad - 2x03 - Bit by a Dead Bee', 'mkv'],
	['Firefly.1x05.Safe', 'Firefly - 1x05 - Out of Gas', 'avi'],
	['Severance.S01E01.Good.News.About.Hell.2160p', 'Severance - 1x01 - Good News About Hell', 'mkv'],
	['The.Mandalorian.S02E01.1080p.WEB', 'The Mandalorian - 2x01 - Chapter 9: The Marshal', 'mkv'],
	['The.Office.US.S03E10.HDTV', 'The Office - 3x10 - A Benihana Christmas (1)', 'mkv'],
];

const WIN = {x: 210, y: 236, w: 1500, h: 800};
const CONTENT = {x: WIN.x + 104, y: WIN.y + 44 + 100};
const MATCH = {x: 614, y: 170, w: 150, h: 84};
const RENAME = {x: 614, y: 280, w: 150, h: 84};

const T = {match: 112, matched: 136, rename: 222, renamed: 232};

const ListBox: React.FC<{x: number; w: number; label: string; focused?: boolean; children: React.ReactNode}> = ({x, w, label, focused, children}) => (
	<>
		<div style={{position: 'absolute', left: x, top: 16, fontSize: 18, fontWeight: 700}}>{label}</div>
		<div style={{
			position: 'absolute', left: x, top: 50, width: w, height: 580, borderRadius: 10, background: C.surface, padding: 8,
			border: `1.5px solid ${focused ? C.accent : C.border}`, overflow: 'hidden',
		}}>{children}</div>
	</>
);

const Row: React.FC<{name: string; ext: string; style?: React.CSSProperties; icon?: React.ReactNode; selected?: boolean}> = ({name, ext, style, icon, selected}) => (
	<div style={{
		height: 48, display: 'flex', alignItems: 'center', gap: 10, padding: '0 12px', borderRadius: 7, fontSize: 20, whiteSpace: 'nowrap',
		background: selected ? 'rgba(249,115,22,0.30)' : 'transparent', ...style,
	}}>
		{icon}
		<span style={{flex: 1, overflow: 'hidden', textOverflow: 'ellipsis'}}>{name}</span>
		<ExtBadge ext={ext}/>
	</div>
);

const Button: React.FC<{box: typeof MATCH; label: string; primary?: boolean; click: number; children: React.ReactNode}> = ({box, label, primary, click, children}) => {
	const frame = useCurrentFrame();
	const press = interpolate(frame, [click, click + 3, click + 9], [1, 0.92, 1], clamp);
	const glow = primary ? 0.35 + 0.25 * Math.sin(frame / 6) : 0;
	return (
		<div style={{
			position: 'absolute', left: box.x, top: box.y, width: box.w, height: box.h, borderRadius: 10, transform: `scale(${press})`,
			display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6, fontSize: 18, fontWeight: 600,
			background: primary ? C.accent : C.surfaceHi, border: primary ? 'none' : `1px solid ${C.border}`,
			boxShadow: primary && frame > T.matched && frame < T.renamed ? `0 0 40px rgba(249,115,22,${glow})` : 'none',
		}}>{children}<span>{label}</span></div>
	);
};

export const Demo: React.FC<{s: Strings}> = ({s}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const win = pop(frame, fps, 4);
	const step = frame < T.match - 6 ? 0 : frame < T.rename - 6 ? 1 : 2;

	// Cursor path: enters, goes to Match, then to Rename
	const toMatch = progress(frame, 78, T.match - 2);
	const toRename = progress(frame, 176, T.rename - 2);
	const target = (b: typeof MATCH) => ({x: CONTENT.x + b.x + b.w / 2 - 6, y: CONTENT.y + b.y + b.h / 2 - 3});
	const m = target(MATCH);
	const r = target(RENAME);
	const cx = interpolate(toRename, [0, 1], [interpolate(toMatch, [0, 1], [1500, m.x]), r.x]);
	const cy = interpolate(toRename, [0, 1], [interpolate(toMatch, [0, 1], [1000, m.y]), r.y]);
	const cursorOpacity = interpolate(frame, [76, 86, T.renamed + 20, T.renamed + 30], [0, 1, 1, 0], clamp);

	const matching = frame >= T.match + 4 && frame < T.matched;
	const toast = pop(frame, fps, T.renamed + 22, {damping: 13});

	return (
		<Scene>
			<Heading size={56} top={46}>{s.demo.title}</Heading>
			<div style={{position: 'absolute', top: 140, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 18, ...enter(frame, fps, 8, 20)}}>
				{s.demo.steps.map((label, i) => (
					<div key={label} style={{
						display: 'flex', alignItems: 'center', gap: 12, padding: '10px 24px 10px 12px', borderRadius: 999, fontSize: 24, fontWeight: 600,
						background: i === step ? C.accentSoft : 'rgba(255,255,255,0.03)', border: `1.5px solid ${i === step ? C.accent : C.border}`,
						color: i <= step ? C.text : C.dim, transform: `scale(${i === step ? 1.06 : 1})`,
					}}>
						<span style={{
							width: 36, height: 36, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 800,
							background: i < step ? C.green : i === step ? C.accent : C.surfaceHi, color: '#fff',
						}}>{i < step ? '✓' : i + 1}</span>
						{label}
					</div>
				))}
			</div>

			<div style={{
				position: 'absolute', left: WIN.x, top: WIN.y, opacity: win,
				transform: `translateY(${interpolate(win, [0, 1], [60, 0])}px) scale(${interpolate(win, [0, 1], [0.94, 1])})`,
			}}>
				<AppWindow width={WIN.w} height={WIN.h} title="Rename" subtitle="Match your files with movie, episode and music data, then rename them all at once.">
					<ListBox x={24} w={570} label="Original Files" focused={frame < T.match}>
						{FILES.map(([orig, renamed, ext], i) => {
							const d = 30 + i * 7;
							const p = pop(frame, fps, d, {damping: 15});
							const done = interpolate(frame, [T.renamed + i * 5, T.renamed + i * 5 + 8], [0, 1], clamp);
							return (
								<div key={orig} style={{position: 'relative', opacity: p, transform: `translateY(${interpolate(p, [0, 1], [-80, 0])}px)`}}>
									<Row name={orig} ext={ext} selected={i === 0 && done === 0 && frame > T.matched} style={{opacity: 1 - done, fontFamily: undefined}}/>
									<Row name={renamed} ext={ext} icon={<CircleCheck size={22} color={C.green}/>} style={{position: 'absolute', inset: 0, opacity: done, transform: `translateX(${(1 - done) * 30}px)`}}/>
								</div>
							);
						})}
					</ListBox>

					<Button box={MATCH} label="Match" click={T.match}><ArrowLeftRight size={26}/></Button>
					<Button box={RENAME} label="Rename" primary click={T.rename}><ArrowRight size={26}/></Button>

					<ListBox x={784} w={588} label="New Names">
						{matching && (
							<div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12, color: C.muted, fontSize: 22}}>
								<Loader size={26} style={{transform: `rotate(${frame * 12}deg)`}}/>{s.demo.matching}
							</div>
						)}
						{FILES.map(([orig, renamed, ext], i) => {
							const d = T.matched + i * 8;
							const p = interpolate(frame, [d, d + 10], [0, 1], {...clamp, easing: ease});
							const flash = interpolate(frame, [d, d + 6, d + 24], [0, 0.5, 0], clamp);
							const out = interpolate(frame, [T.renamed + i * 5, T.renamed + i * 5 + 8], [1, 0.35], clamp);
							return (
								<Row key={orig} name={renamed} ext={ext} style={{
									opacity: p * out, transform: `translateX(${(1 - p) * -40}px)`, background: `rgba(249,115,22,${flash})`,
								}}/>
							);
						})}
					</ListBox>
				</AppWindow>
			</div>

			<Cursor x={cx} y={cy} click={[T.match, T.rename]} opacity={cursorOpacity}/>

			<div style={{
				position: 'absolute', left: 960, top: 930, transform: `translate(-50%, 0) translateY(${(1 - toast) * 40}px) scale(${0.8 + toast * 0.2})`, opacity: toast,
				display: 'flex', alignItems: 'center', gap: 14, padding: '18px 34px', borderRadius: 14, background: '#14361F', border: `1.5px solid ${C.green}`,
				fontSize: 28, fontWeight: 700, boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
			}}>
				<CircleCheck size={32} color={C.green}/>{s.demo.done}
			</div>
		</Scene>
	);
};
