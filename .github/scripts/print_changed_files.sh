#!/usr/bin/env bash

# Used to print action results for observability
# https://github.com/tj-actions/changed-files

printf "Matching file filters:\n\n"

for f in .github/outputs/*_all_changed_files.txt; do
    [ -f "$f" ] || continue
    key=$(basename "$f" _all_changed_files.txt)
    count=$(wc -w < "$f" | tr -d ' ')
    echo "== $key ($count files) =="
    cat "$f" | tr ' ' '\n'
    printf "\n\n"
done
