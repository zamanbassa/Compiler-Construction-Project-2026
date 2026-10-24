#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
submission="$root/submission"
build=$(mktemp -d)
classes="$build/classes"
test_classes="$build/test-classes"
jar_tmp="$submission/group-27.jar.tmp"
trap 'rm -rf "$build"; rm -f "$jar_tmp"' EXIT HUP INT TERM

mkdir -p "$classes" "$test_classes"
find "$root/src/main/java" -name '*.java' -print > "$build/main-sources.txt"
find "$root/test/java" -name '*.java' -print > "$build/test-sources.txt"
javac --release 21 -d "$classes" @"$build/main-sources.txt"
javac --release 21 -cp "$classes" -d "$test_classes" @"$build/test-sources.txt"

find "$root/test/java" -name '*Test.java' -print | sort | while IFS= read -r file; do
  test_class=${file#"$root/test/java/"}
  test_class=${test_class%.java}
  test_class=$(printf '%s' "$test_class" | tr '/' '.')
  echo "== $test_class =="
  java -ea -cp "$classes:$test_classes" "$test_class"
done

jar --create --file "$jar_tmp" --main-class spl.Main -C "$classes" .
mv "$jar_tmp" "$submission/group-27.jar"
echo "Created submission/group-27.jar."
