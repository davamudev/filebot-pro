import {Config} from '@remotion/cli/config';

Config.setEntryPoint('src/index.ts');
Config.setVideoImageFormat('jpeg');
Config.setJpegQuality(95);
Config.setCodec('h264');
Config.setCrf(18);
Config.setOverwriteOutput(true);
