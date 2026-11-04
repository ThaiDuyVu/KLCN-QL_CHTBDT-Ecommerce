const MAX_EDGE = 1600;
const SKIP_BELOW_BYTES = 400 * 1024;
const JPEG_QUALITY = 0.85;

/**
 * Downscale a JPG/PNG so its longest edge is at most 1600px before upload.
 * Keeps the original type (backend accepts only JPG/PNG) and returns the original
 * file when it is already small, when shrinking does not help, or when the browser
 * cannot decode it (e.g. in tests).
 */
export async function shrinkImage(file) {
  if (file.size <= SKIP_BELOW_BYTES || typeof createImageBitmap !== 'function') return file;
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close?.();
    const type = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, type, JPEG_QUALITY));
    if (!blob || blob.size >= file.size) return file;
    return new File([blob], file.name, { type, lastModified: file.lastModified });
  } catch {
    return file;
  }
}
