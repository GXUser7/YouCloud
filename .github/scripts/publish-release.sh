#!/usr/bin/env bash
set -euo pipefail

tag=$1
apk="$2/YouCloud.${tag#v}.apk"
git rev-parse --verify "refs/tags/$tag^{commit}" > /dev/null
if [ ! -f "$apk" ]; then
  echo "::error::Tag $tag does not match the built APK version"
  exit 1
fi

# Surface publishing errors as annotations, too, so they are visible in the run summary.
gh_checked() {
  local output
  if output=$(gh "$@" 2>&1); then
    printf '%s\n' "$output"
  else
    output=${output//'%'/'%25'}
    output=${output//$'\r'/'%0D'}
    output=${output//$'\n'/'%0A'}
    printf '::error::%s\n' "$output"
    return 1
  fi
}

if gh release view "$tag" --json assets --jq '.assets[].name' > release-assets.txt 2>/dev/null; then
  # Keep published assets on reruns and finish uploads interrupted after release creation.
  if ! grep -Fxq "$(basename "$apk")" release-assets.txt; then
    gh_checked release upload "$tag" "$apk"
  fi
else
  gh_checked release create "$tag" "$apk" \
    --verify-tag --title "YouCloud ${tag#v}" --notes-from-tag --latest
fi
