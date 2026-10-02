// A code on screen, and a code read by the camera: shared by the wallet (payments) and by the door apps use (pairing).

/** Draws `text` as a QR code into `container` (an element). */
export async function drawQr(container, text, width = 240) {
  const QRCode = (await import('qrcode')).default;
  const canvas = document.createElement('canvas');
  await QRCode.toCanvas(canvas, text, { width, errorCorrectionLevel: 'L' });
  container.replaceChildren(canvas);
}

/** True when this browser can read a QR code with the camera. */
export const canScan = () => 'BarcodeDetector' in window && !!navigator.mediaDevices?.getUserMedia;

/**
 * Reads one QR code with the camera, showing it in `video`. Resolves with its text, or with null when `signal` aborts or
 * the camera is not available. Never throws: a scan that cannot happen is a code to paste instead.
 */
export async function scanQr(video, signal) {
  if (!canScan()) return null;
  let stream;
  try { stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } }); } catch { return null; }
  video.srcObject = stream;
  video.hidden = false;
  try { await video.play(); } catch { /* the first frame decides */ }
  const detector = new BarcodeDetector({ formats: ['qr_code'] });
  return new Promise((resolve) => {
    const stop = (value) => { clearInterval(timer); stream.getTracks().forEach((t) => t.stop()); video.hidden = true; resolve(value); };
    const timer = setInterval(async () => {
      const codes = await detector.detect(video).catch(() => []);
      if (codes.length > 0) stop(codes[0].rawValue);
    }, 300);
    signal?.addEventListener('abort', () => stop(null), { once: true });
  });
}
