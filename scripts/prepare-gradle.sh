#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

# Bootstrap separately so transient distribution-download failures are retried
# without retrying compilation, tests, signing, or release publication.
for attempt in 1 2 3; do
  if bash ./gradlew --no-daemon --version; then
    exit 0
  else
    status=$?
  fi

  if (( attempt == 3 )); then
    echo "::error::Gradle bootstrap failed after $attempt attempts."
    exit "$status"
  fi

  delay=$((attempt * 10))
  echo "::warning::Gradle bootstrap failed (attempt $attempt/3); retrying in ${delay}s."
  sleep "$delay"
done
