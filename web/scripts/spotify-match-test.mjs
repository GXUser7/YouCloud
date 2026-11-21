import assert from 'node:assert/strict'
import {
  matchCandidateScore,
  popularYouTubeFallbackScore,
  resolveSpotifyPriorityBonuses,
} from '../src/lib/services/spotifyMatch.ts'

// 1. Gaming montages (like "unki, sly sheen - Jeff the Killer | VALORANT montage") must be rejected everywhere.
{
  const montageTitle = 'unki, sly sheen - Jeff the Killer | VALORANT montage'
  assert.equal(
    matchCandidateScore('Jeff the Killer', ['unki', 'sly sheen'], 103, montageTitle, 'desolated', 104, 15000, false, false),
    null,
    'YouTube strict should reject VALORANT montage',
  )
  assert.equal(
    matchCandidateScore('Jeff the Killer', ['unki', 'sly sheen'], 103, montageTitle, 'desolated', 104, 15000, true, false),
    null,
    'YouTube loose should reject VALORANT montage',
  )
  assert.equal(
    matchCandidateScore('Jeff the Killer', ['unki', 'sly sheen'], 103, montageTitle, 'desolated', 104, undefined, false, true),
    null,
    'SoundCloud with allowArtistMismatch should still reject VALORANT montage',
  )
  assert.equal(
    popularYouTubeFallbackScore('Jeff the Killer', ['unki', 'sly sheen'], 103, montageTitle, 'desolated', 104, 15000),
    null,
    'Popular YouTube fallback should reject VALORANT montage',
  )
}

// 2. SoundCloud uploader mismatch is allowed when title + duration match cleanly, while YouTube strict rejects it.
{
  const ytStrictScore = matchCandidateScore(
    'Jeff the Killer',
    ['unki', 'sly sheen'],
    103,
    'Jeff the Killer',
    'random_reuploader_99',
    103,
    undefined,
    false,
    false,
  )
  assert.equal(ytStrictScore, null, 'YouTube strict must still require matching artist')

  const scMismatchScore = matchCandidateScore(
    'Jeff the Killer',
    ['unki', 'sly sheen'],
    103,
    'Jeff the Killer',
    'random_reuploader_99',
    103,
    undefined,
    false,
    true,
  )
  assert.ok(scMismatchScore !== null && scMismatchScore > 0, 'SoundCloud must allow uploader mismatch')
}

// 3. Covers and slowed editions stay rejected on SoundCloud even when allowArtistMismatch = true.
{
  assert.equal(
    matchCandidateScore('Jeff the Killer', ['unki', 'sly sheen'], 103, 'Jeff the Killer (slowed)', 'reup', 103, undefined, false, true),
    null,
    'SoundCloud slowed edition mismatch must be rejected',
  )
  assert.equal(
    matchCandidateScore('Jeff the Killer', ['unki', 'sly sheen'], 103, 'Jeff the Killer guitar cover', 'reup', 103, undefined, false, true),
    null,
    'SoundCloud cover edition mismatch must be rejected',
  )
}

// 4. Source priority bonus ordering and disconnected-SoundCloud fallback.
{
  const scPref = resolveSpotifyPriorityBonuses('sc', true)
  assert.equal(scPref.preferSoundCloud, true)
  assert.ok(scPref.sc > scPref.ytSong, 'When SC prioritized and connected, SC bonus must beat YT')

  const scDisconnected = resolveSpotifyPriorityBonuses('sc', false)
  assert.equal(scDisconnected.preferSoundCloud, false)
  assert.ok(scDisconnected.ytSong > scDisconnected.sc, 'When SC disconnected, YT fallback bonus applies')
}

console.log('spotify-match-test: all assertions passed!')
