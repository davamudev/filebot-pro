import React from 'react';
import {AbsoluteFill} from 'remotion';
import {Logo, Scene, Wordmark} from './components/ui';
import {STRINGS} from './i18n';
import {C} from './theme';

// Banner of r/filebotpro, laid out at Reddit's minimum desktop size (1072x128) and rendered at 2x.
// Reddit crops it to the width of the window, so the content stays in the middle and clear of the edges.
export const RedditBanner: React.FC = () => (
	<Scene>
		<AbsoluteFill style={{background: C.bg, overflow: 'hidden'}}>
			<div style={{
				position: 'absolute', width: 900, height: 900, borderRadius: '50%', left: 620, top: -620,
				background: 'radial-gradient(circle, rgba(249,115,22,0.16) 0%, rgba(249,115,22,0) 65%)',
			}}/>
			<div style={{
				position: 'absolute', width: 700, height: 700, borderRadius: '50%', left: -340, top: -160,
				background: 'radial-gradient(circle, rgba(234,88,12,0.10) 0%, rgba(234,88,12,0) 65%)',
			}}/>
			<div style={{
				position: 'absolute', width: 260, height: 260, borderRadius: '50%', left: 101, top: -66,
				background: 'radial-gradient(circle, rgba(249,115,22,0.32) 0%, rgba(249,115,22,0) 65%)',
			}}/>
			<AbsoluteFill style={{
				backgroundImage: 'linear-gradient(rgba(255,255,255,0.035) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.035) 1px, transparent 1px)',
				backgroundSize: '32px 32px',
				maskImage: 'radial-gradient(ellipse at center, black 30%, transparent 80%)',
				WebkitMaskImage: 'radial-gradient(ellipse at center, black 30%, transparent 80%)',
			}}/>
		</AbsoluteFill>
		<AbsoluteFill style={{flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 22}}>
			<Logo size={68} style={{borderRadius: 14}}/>
			<Wordmark size={46}/>
			<div style={{width: 1, height: 40, background: C.border, margin: '0 6px'}}/>
			<div style={{fontSize: 17, color: C.muted, lineHeight: 1.35, width: 300}}>{STRINGS.en.logo.tagline}</div>
		</AbsoluteFill>
	</Scene>
);
