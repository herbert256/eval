import { createRecognizer, resolveOrientation } from './vendor/fenshot/index';
import { env } from 'onnxruntime-web/wasm';

// Android WebView does not provide cross-origin isolation for WASM threads.
env.wasm.numThreads = 1;
const recognizer = createRecognizer({modelUrl: './chess-tiles-v2.onnx', wasmPaths: './'});
const state = window as any;
state.scanBoard = async (source: string) => {
  state.scanResult = null;
  try {
    const image = new Image();
    image.src = source;
    await image.decode();
    const result = await recognizer.recognize(image);
    state.scanResult = result?.plausible
      ? {...result, placement: resolveOrientation(result.placement).placement}
      : {empty: true};
  } catch (error) {
    state.scanResult = {error: String(error)};
  }
};
state.scannerReady = true;
