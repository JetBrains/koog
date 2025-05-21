#!/bin/bash
set -e

DOCS_REPO=../koog-api-docs

# Build docs
./gradlew dokkaGenerate

# Clone or update docs repo
if [ ! -d "$DOCS_REPO" ]; then
  git clone git@github.com:JetBrains/koog-api-docs.git "$DOCS_REPO"
fi

cd "$DOCS_REPO"
cp -r ../koog-agents/build/dokka/html/* .
touch .nojekyll

git add .
git commit -m "Update docs on $(date)"
git push origin main