import {Easing, interpolate, spring, SpringConfig} from 'remotion';

export const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

export const ease = Easing.bezier(0.22, 1, 0.36, 1);

export function pop(frame: number, fps: number, delay = 0, config: Partial<SpringConfig> = {damping: 200}) {
	return spring({frame: frame - delay, fps, config});
}

// Fades and slides an element in, starting at `delay`
export function enter(frame: number, fps: number, delay = 0, distance = 40) {
	const p = pop(frame, fps, delay, {damping: 200, mass: 0.8});
	return {
		opacity: interpolate(p, [0, 1], [0, 1]),
		transform: `translateY(${interpolate(p, [0, 1], [distance, 0])}px)`,
	};
}

export function fadeOut(frame: number, start: number, length = 12) {
	return interpolate(frame, [start, start + length], [1, 0], clamp);
}

export function progress(frame: number, start: number, end: number) {
	return interpolate(frame, [start, end], [0, 1], {...clamp, easing: ease});
}

// Number of characters to show when typing `text` from `start` at `speed` characters per frame
export function typed(text: string, frame: number, start: number, speed = 1) {
	return text.slice(0, Math.max(0, Math.floor((frame - start) * speed)));
}
