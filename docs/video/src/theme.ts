import {loadFont as loadInter} from '@remotion/google-fonts/Inter';
import {loadFont as loadMono} from '@remotion/google-fonts/JetBrainsMono';

const inter = loadInter('normal', {weights: ['400', '500', '600', '700', '800', '900'], subsets: ['latin', 'latin-ext']});
const mono = loadMono('normal', {weights: ['400', '500', '700'], subsets: ['latin']});

export const FONT = inter.fontFamily;
export const MONO = mono.fontFamily;

// Colors taken from the dark theme of the app (FlatDarkLaf.properties) and the logo
export const C = {
	bg: '#0C0C0F',
	window: '#1C1C20',
	titleBar: '#161619',
	sidebar: '#151518',
	surface: '#232328',
	surfaceHi: '#2A2A30',
	border: '#33333B',
	text: '#F4F4F5',
	muted: '#A1A1AA',
	dim: '#71717A',
	accent: '#F97316',
	accentDeep: '#EA580C',
	accentSoft: 'rgba(249, 115, 22, 0.14)',
	accentLine: 'rgba(249, 115, 22, 0.45)',
	yellow: '#FDE047',
	green: '#22C55E',
	navy: '#0F172A',
};

export const GRADIENT = 'linear-gradient(135deg, #FF8A00 0%, #EA580C 100%)';
export const GOLD = 'linear-gradient(135deg, #FDE047 0%, #FDBA74 100%)';
