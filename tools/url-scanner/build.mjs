import { build } from 'esbuild';
import { copyFile } from 'node:fs/promises';
const output = '../../app/src/main/assets/board-scanner/';
await build({entryPoints: ['scanner.ts'], bundle: true, format: 'esm', minify: true,
  target: 'chrome100', outfile: output + 'scanner.js'});
for (const name of ['ort-wasm-simd-threaded.mjs', 'ort-wasm-simd-threaded.wasm']) {
  await copyFile('node_modules/onnxruntime-web/dist/' + name, output + name);
}
await copyFile('ONNX-LICENSE.txt', output + 'ONNX-LICENSE.txt');
await copyFile('DETECTOR-LICENSE.txt', output + 'DETECTOR-LICENSE.txt');
await copyFile('vendor/fenshot/LICENSE', output + 'FENSHOT-LICENSE.txt');
