/** A raw chapter page is a failed extraction result, not a comic image. */
export function isChapterPageUrl(rawUrl: string, chapterUrl?: string | null): boolean {
  if (!chapterUrl) return false
  const normalize = (url: string) => url.split(/,\s*\{/)[0].trim().toLowerCase()
  return normalize(rawUrl) === normalize(chapterUrl)
}
