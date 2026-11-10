export const fullDate = (iso) =>
  (iso ? new Date(iso).toLocaleString('en-US', { dateStyle: 'short', timeStyle: 'short' }) : '');

export function ago(iso) {
  if (!iso) return '';
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 45) return 'just now';
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h ago`;
  if (seconds < 7 * 86400) return `${Math.round(seconds / 86400)} d ago`;
  return new Date(iso).toLocaleDateString('en-US');
}
