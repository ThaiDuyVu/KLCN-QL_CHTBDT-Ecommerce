import { useState } from 'react';

export default function ProductImage({ src, alt }) {
  const [failedUrl, setFailedUrl] = useState(null);
  if (!src || failedUrl === src) {
    return <div className="product-image-placeholder" role="img" aria-label={`Chưa có ảnh: ${alt}`}>Chưa có ảnh</div>;
  }
  return <img className="product-image" src={src} alt={alt} loading="lazy" onError={() => setFailedUrl(src)} />;
}
