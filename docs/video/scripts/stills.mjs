// Renders single frames for a quick visual check: node scripts/stills.mjs <composition> <frame> [frame...]
import path from 'node:path';
import {bundle} from '@remotion/bundler';
import {renderStill, selectComposition} from '@remotion/renderer';

const [id, ...frames] = process.argv.slice(2);
const serveUrl = await bundle({entryPoint: path.resolve('src/index.ts')});
const composition = await selectComposition({serveUrl, id});
for (const frame of frames) {
	const output = `out/${id}-${String(frame).padStart(4, '0')}.png`;
	await renderStill({serveUrl, composition, frame: Number(frame), output, imageFormat: 'png', scale: 0.5});
	console.log(output);
}
