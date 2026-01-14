#!/bin/bash
set -e

contributors=$(<CONTRIBUTORS.md)

for readme in README*.md; do
  if grep -q "<!-- CONTRIBUTORS -->" "$readme"; then
    sed -i.bak "/<!-- CONTRIBUTORS -->/{
        r /dev/stdin
        d
    }" "$readme" <<< "$contributors"
    rm "$readme.bak"
    echo "$readme updated."
  else
    echo "No marker in $readme, skipping."
  fi
done
