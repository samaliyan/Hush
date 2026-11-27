#!/usr/bin/env bash
# Runs the pure-Java unit tests (no Android SDK, no network). Exit code != 0 on failure.
set -euo pipefail
export LC_ALL=C.UTF-8
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
S=app/src/main/java/io/github/samaliyan/hush
T=tests/java/io/github/samaliyan/hush
# all plain-Java classes of the app (those that use nothing from Android)
javac -encoding UTF-8 -Xlint:all -Werror -d "$OUT" $(grep -L "^import android" "$S"/*.java) "$T"/*.java
for t in "$T"/*.java; do
  java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$OUT" "io.github.samaliyan.hush.$(basename "$t" .java)"
done
