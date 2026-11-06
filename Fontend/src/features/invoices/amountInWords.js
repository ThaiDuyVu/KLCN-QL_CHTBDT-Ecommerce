const DIGITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
const GROUPS = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ'];

/** Reads a group of three digits; `full` forces "không trăm" when a higher group exists. */
function readTriple(value, full) {
  const hundreds = Math.floor(value / 100);
  const tens = Math.floor((value % 100) / 10);
  const ones = value % 10;
  const words = [];
  if (hundreds || full) words.push(DIGITS[hundreds], 'trăm');
  if (tens > 1) words.push(DIGITS[tens], 'mươi');
  else if (tens === 1) words.push('mười');
  else if (ones && (hundreds || full)) words.push('lẻ');
  if (ones === 1 && tens > 1) words.push('mốt');
  else if (ones === 5 && tens > 0) words.push('lăm');
  else if (ones === 4 && tens > 1) words.push('tư');
  else if (ones) words.push(DIGITS[ones]);
  return words.join(' ');
}

/** Vietnamese amount in words for invoices, e.g. 1250000 → "Một triệu hai trăm năm mươi nghìn đồng". */
export function amountInWords(amount) {
  let value = Math.round(Number(amount) || 0);
  if (value === 0) return 'Không đồng';
  const groups = [];
  while (value > 0) { groups.push(value % 1000); value = Math.floor(value / 1000); }
  const parts = [];
  for (let index = groups.length - 1; index >= 0; index -= 1) {
    if (!groups[index]) continue;
    parts.push(`${readTriple(groups[index], index < groups.length - 1)} ${GROUPS[index]}`.trim());
  }
  const text = `${parts.join(' ')} đồng`;
  return text.charAt(0).toUpperCase() + text.slice(1);
}
