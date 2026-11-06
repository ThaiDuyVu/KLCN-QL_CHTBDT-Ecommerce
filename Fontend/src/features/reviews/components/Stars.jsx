/** Read-only star rating, e.g. ★★★★☆ with an accessible label. */
export default function Stars({ value }) {
  const rounded = Math.round(Number(value) || 0);
  return <span className="review-stars" role="img" aria-label={`${value} trên 5 sao`}>{'★'.repeat(rounded)}{'☆'.repeat(5 - rounded)}</span>;
}
