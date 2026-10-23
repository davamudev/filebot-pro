#!/bin/bash
# Offline end-to-end checks of the command line: argument errors and the --conflict actions. Needs the installed
# distribution (./gradlew installDist). Usage: packaging/tests/e2e-cli.sh [path/to/filebot-pro]
set -u

bin="${1:-build/install/filebot-pro/bin/filebot-pro}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export FILEBOT_PRO_OPTS="-Dapplication.dir=$work/data"
failures=0

check() { # description, condition result (0 = ok)
	if [ "$2" -eq 0 ]; then echo "ok    $1"; else echo "FAIL  $1"; failures=$((failures + 1)); fi
}

# rename NAME (conflict action...) copies in/NAME to out/target.mkv
rename() {
	"$bin" -rename "$work/in/$1" --db file --action copy --output "$work/out" --format "target" "${@:2}" > "$work/log" 2>&1
}

mkdir -p "$work/in" "$work/out"
printf 'first' > "$work/in/a.mkv"
printf 'second version' > "$work/in/b.mkv"

# invalid values are reported as a plain message, not as a Java stack trace
for option in "--action nope" "--conflict nope" "--order nope"; do
	"$bin" -rename "$work/in/a.mkv" --db file $option --format target > "$work/log" 2>&1
	code=$?
	grep -q "Exception" "$work/log"; trace=$?
	check "$option fails cleanly" $([ "$code" -ne 0 ] && [ "$trace" -ne 0 ]; echo $?)
done

rename a.mkv --conflict skip
check "first copy" $([ "$(cat "$work/out/target.mkv" 2> /dev/null)" = "first" ]; echo $?)

rename b.mkv --conflict skip
check "skip keeps the existing file" $([ "$(cat "$work/out/target.mkv")" = "first" ]; echo $?)

rename b.mkv --conflict fail
check "fail returns an error" $([ $? -ne 0 ]; echo $?)
check "fail keeps the existing file" $([ "$(cat "$work/out/target.mkv")" = "first" ]; echo $?)

rename b.mkv --conflict index
check "index adds a second file" $([ "$(ls "$work/out" | wc -l)" -eq 2 ]; echo $?)

rename b.mkv --conflict override
check "override replaces the file" $([ "$(cat "$work/out/target.mkv")" = "second version" ]; echo $?)
check "override leaves no backup behind" $([ -z "$(ls "$work/out" | grep filebot-old)" ]; echo $?)

[ "$failures" -eq 0 ] || { echo "$failures check(s) failed"; exit 1; }
echo "all checks passed"
