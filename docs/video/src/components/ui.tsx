import React from 'react';
import {AbsoluteFill, Img, interpolate, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {
	Captions, CircleHelp, Filter, FilePen, List, Minus, Moon, ShieldCheck, Square, Tv, X,
} from 'lucide-react';
import {clamp, enter} from '../anim';
import {C, FONT, GRADIENT} from '../theme';

// Persistent backdrop: dark base, two slow orange glows and a faint grid
export const Background: React.FC = () => {
	const frame = useCurrentFrame();
	const a = frame / 90;
	return (
		<AbsoluteFill style={{background: C.bg, overflow: 'hidden'}}>
			<div style={{
				position: 'absolute', width: 1400, height: 1400, borderRadius: '50%',
				left: 900 + Math.sin(a) * 180, top: -700 + Math.cos(a * 0.8) * 120,
				background: 'radial-gradient(circle, rgba(249,115,22,0.16) 0%, rgba(249,115,22,0) 65%)',
			}}/>
			<div style={{
				position: 'absolute', width: 1200, height: 1200, borderRadius: '50%',
				left: -500 + Math.cos(a * 0.7) * 160, top: 300 + Math.sin(a * 0.9) * 140,
				background: 'radial-gradient(circle, rgba(234,88,12,0.10) 0%, rgba(234,88,12,0) 65%)',
			}}/>
			<AbsoluteFill style={{
				backgroundImage: 'linear-gradient(rgba(255,255,255,0.035) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.035) 1px, transparent 1px)',
				backgroundSize: '64px 64px',
				backgroundPosition: `0 ${(frame * 0.4) % 64}px`,
				maskImage: 'radial-gradient(ellipse at center, black 20%, transparent 75%)',
				WebkitMaskImage: 'radial-gradient(ellipse at center, black 20%, transparent 75%)',
			}}/>
		</AbsoluteFill>
	);
};

export const Scene: React.FC<{children: React.ReactNode; style?: React.CSSProperties}> = ({children, style}) => (
	<AbsoluteFill style={{fontFamily: FONT, color: C.text, ...style}}>{children}</AbsoluteFill>
);

export const Heading: React.FC<{
	children: React.ReactNode; delay?: number; size?: number; top?: number; sub?: React.ReactNode; width?: number;
}> = ({children, delay = 0, size = 64, top = 70, sub, width = 1600}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	return (
		<div style={{position: 'absolute', top, left: 0, right: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center'}}>
			<div style={{fontSize: size, fontWeight: 800, letterSpacing: -1.5, maxWidth: width, lineHeight: 1.1, ...enter(frame, fps, delay)}}>{children}</div>
			{sub && (
				<div style={{fontSize: 30, color: C.muted, marginTop: 18, maxWidth: 1500, lineHeight: 1.4, ...enter(frame, fps, delay + 8)}}>{sub}</div>
			)}
		</div>
	);
};

export const Accent: React.FC<{children: React.ReactNode}> = ({children}) => (
	<span style={{background: GRADIENT, WebkitBackgroundClip: 'text', backgroundClip: 'text', color: 'transparent'}}>{children}</span>
);

export const Logo: React.FC<{size: number; style?: React.CSSProperties}> = ({size, style}) => (
	<Img src={staticFile('logo.svg')} style={{width: size, height: size, ...style}}/>
);

export const Wordmark: React.FC<{size: number}> = ({size}) => (
	<div style={{fontSize: size, fontWeight: 900, letterSpacing: -size * 0.03, lineHeight: 1}}>
		FileBot <Accent>Pro</Accent>
	</div>
);

export const Pill: React.FC<{children: React.ReactNode; style?: React.CSSProperties; active?: boolean}> = ({children, style, active}) => (
	<div style={{
		display: 'inline-flex', alignItems: 'center', gap: 12, padding: '14px 28px', borderRadius: 999,
		background: active ? C.accentSoft : 'rgba(255,255,255,0.04)',
		border: `1.5px solid ${active ? C.accentLine : C.border}`,
		fontSize: 28, fontWeight: 600, color: active ? C.text : C.muted, ...style,
	}}>{children}</div>
);

const NAV = [
	{label: 'Rename', Icon: FilePen},
	{label: 'Episodes', Icon: Tv},
	{label: 'Subtitles', Icon: Captions},
	{label: 'SFV', Icon: ShieldCheck},
	{label: 'Filter', Icon: Filter},
	{label: 'List', Icon: List},
];

// Recreation of the FileBot Pro main window (dark theme)
export const AppWindow: React.FC<{
	width: number; height: number; active?: string; title: string; subtitle: string; children: React.ReactNode; style?: React.CSSProperties;
}> = ({width, height, active = 'Rename', title, subtitle, children, style}) => (
	<div style={{
		width, height, borderRadius: 14, overflow: 'hidden', background: C.window, display: 'flex', flexDirection: 'column',
		border: `1px solid ${C.border}`, boxShadow: '0 40px 120px rgba(0,0,0,0.6), 0 0 0 1px rgba(255,255,255,0.03)', ...style,
	}}>
		<div style={{height: 44, background: C.titleBar, display: 'flex', alignItems: 'center', padding: '0 16px', gap: 12, fontSize: 17, color: C.muted}}>
			<Logo size={20} style={{borderRadius: 4}}/>
			<span>FileBot Pro 1.0.0</span>
			<div style={{flex: 1}}/>
			<div style={{display: 'flex', gap: 34, paddingRight: 8}}>
				<Minus size={18}/><Square size={15}/><X size={18}/>
			</div>
		</div>
		<div style={{flex: 1, display: 'flex', minHeight: 0}}>
			<div style={{width: 104, background: C.sidebar, display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '12px 0', gap: 8, borderRight: `1px solid ${C.border}`}}>
				{NAV.map(({label, Icon}) => {
					const on = label === active;
					return (
						<div key={label} style={{
							width: 84, height: 74, borderRadius: 10, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6,
							background: on ? 'rgba(249,115,22,0.18)' : 'transparent', color: on ? C.accent : C.muted, fontSize: 14, fontWeight: on ? 700 : 500, position: 'relative',
						}}>
							{on && <div style={{position: 'absolute', left: -10, top: 18, bottom: 18, width: 4, borderRadius: 4, background: C.accent}}/>}
							<Icon size={26} strokeWidth={1.8}/>
							<span>{label}</span>
						</div>
					);
				})}
				<div style={{flex: 1}}/>
				<Moon size={24} color={C.muted}/>
				<div style={{height: 22}}/>
				<CircleHelp size={24} color={C.muted}/>
				<div style={{height: 12}}/>
			</div>
			<div style={{flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0}}>
				<div style={{height: 100, padding: '22px 30px 0', borderBottom: `1px solid ${C.border}`}}>
					<div style={{fontSize: 28, fontWeight: 700}}>{title}</div>
					<div style={{fontSize: 17, color: C.dim, marginTop: 6}}>{subtitle}</div>
				</div>
				<div style={{flex: 1, position: 'relative', minHeight: 0}}>{children}</div>
			</div>
		</div>
	</div>
);

// Mouse pointer with a click ripple; `click` is the frame of the click
export const Cursor: React.FC<{x: number; y: number; click?: number[]; opacity?: number}> = ({x, y, click = [], opacity = 1}) => {
	const frame = useCurrentFrame();
	const pressed = click.some((c) => frame >= c && frame < c + 6);
	return (
		<div style={{position: 'absolute', left: x, top: y, opacity, pointerEvents: 'none', zIndex: 50}}>
			{click.map((c) => {
				const p = interpolate(frame, [c, c + 18], [0, 1], clamp);
				if (p <= 0 || p >= 1) return null;
				return (
					<div key={c} style={{
						position: 'absolute', left: -40 * p, top: -40 * p, width: 80 * p, height: 80 * p, borderRadius: '50%',
						border: `3px solid rgba(249,115,22,${1 - p})`,
					}}/>
				);
			})}
			<svg width="38" height="38" viewBox="0 0 24 24" style={{transform: `scale(${pressed ? 0.85 : 1})`, filter: 'drop-shadow(0 4px 8px rgba(0,0,0,0.5))'}}>
				<path d="M4 2 L4 19 L8.5 15 L11.5 22 L14.5 20.7 L11.6 14 L18 14 Z" fill="#fff" stroke="#111" strokeWidth="1.3" strokeLinejoin="round"/>
			</svg>
		</div>
	);
};

export const ExtBadge: React.FC<{ext: string}> = ({ext}) => (
	<span style={{fontSize: 15, fontWeight: 600, color: '#FDBA74', background: 'rgba(249,115,22,0.16)', padding: '3px 10px', borderRadius: 6}}>{ext}</span>
);
