import assert from 'node:assert/strict'
import test from 'node:test'
import { isChapterPageUrl } from '../src/utils/comicImage.ts'
import { getCachedChapter, saveCachedChapter } from '../src/utils/localCache.ts'

test('a chapter page without an HTML suffix is rejected as an image', () => {
  assert.equal(isChapterPageUrl('https://example.com/read/12', 'https://example.com/read/12'), true)
  assert.equal(isChapterPageUrl('https://example.com/read/12,{"headers":{"x":"y"}}', 'https://example.com/read/12'), true)
  assert.equal(isChapterPageUrl('https://example.com/images/12.jpg', 'https://example.com/read/12'), false)
  assert.equal(isChapterPageUrl('https://example.com/images/12.jpg', undefined), false)
})

test('cached chapters keep their page URL for offline comic filtering', async () => {
  const url = 'https://example.com/read/12'
  await saveCachedChapter('fixture', 12, 'Chapter', url, url)
  const cached = await getCachedChapter('fixture', 12)
  assert.equal(cached?.url, url)
  assert.equal(isChapterPageUrl(cached!.content, cached!.url), true)
  await saveCachedChapter('legacy', 12, 'Chapter', 'text')
  assert.equal((await getCachedChapter('legacy', 12))?.url, undefined)
})
