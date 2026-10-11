import { renderFrames } from './src/render.js';
const D = '/tmp/claude-0/-home-user/1aa30d49-56f8-5824-83e0-00fb58d4972c/scratchpad/aiwa3';
console.log(JSON.stringify(await renderFrames({ page: '../docs/video-vision/video.html', outDir: `${D}/gp`, seconds: 159.75, fps: 30, width: 1080, height: 1920, workers: 4, from: 480 })));
console.log('P-DONE');
