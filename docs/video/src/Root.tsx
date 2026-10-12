import React from 'react';
import {AbsoluteFill, Composition, Still} from 'remotion';
import {Promo, TOTAL_FRAMES} from './Promo';
import {Lang} from './i18n';
import {RedditBanner} from './RedditBanner';
import timeline from './timeline.json';

// The video is laid out at 1920x1080; the 720p version scales it down (small enough for a GitHub README, max 10 MB)
const Promo720: React.FC<{lang: Lang; music: boolean}> = (props) => (
	<AbsoluteFill>
		<div style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, transform: 'scale(0.6666667)', transformOrigin: 'top left'}}>
			<Promo {...props}/>
		</div>
	</AbsoluteFill>
);

export const Root: React.FC = () => (
	<>
		{(['en', 'es'] as const).map((lang) => (
			<React.Fragment key={lang}>
				<Composition
					id={`promo-${lang}`}
					component={Promo}
					durationInFrames={TOTAL_FRAMES}
					fps={timeline.fps}
					width={1920}
					height={1080}
					defaultProps={{lang, music: true}}
				/>
				<Composition
					id={`promo-${lang}-720p`}
					component={Promo720}
					durationInFrames={TOTAL_FRAMES}
					fps={timeline.fps}
					width={1280}
					height={720}
					defaultProps={{lang, music: true}}
				/>
			</React.Fragment>
		))}
		<Still id="reddit-banner" component={RedditBanner} width={1072} height={128}/>
	</>
);
