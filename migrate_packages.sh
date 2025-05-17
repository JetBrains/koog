#!/bin/bash

# Script to migrate packages from ai.jetbrains.code., ai.grazie.code., and ai.jetbrains.embeddings to ai.koog.
# Exceptions: ai.jetbrains.code.files. and ai.grazie.code.files.

set -e  # Exit on error

# Function to log messages
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log "Starting package migration..."

# Step 1: Find all Kotlin and Java files
log "Finding all Kotlin and Java files..."
SOURCE_FILES=$(find . -type f \( -name "*.kt" -o -name "*.java" \) -not -path "*/\.*" -not -path "*/build/*")

# Step 2: Replace package declarations and imports in files
log "Replacing package declarations and imports in files..."
for file in $SOURCE_FILES; do
  log "Processing file: $file"

  # Create a temporary file
  temp_file=$(mktemp)

  # Replace package declarations and imports, but skip the exceptions
  sed -E '
    # Replace ai.jetbrains.code. with ai.koog. but not ai.jetbrains.code.files.
    s/([^.])ai\.jetbrains\.code\.([^f]|f[^i]|fi[^l]|fil[^e]|file[^s]|files[^.])/\1ai.koog.\2/g;
    s/^ai\.jetbrains\.code\.([^f]|f[^i]|fi[^l]|fil[^e]|file[^s]|files[^.])/ai.koog.\1/g;

    # Replace ai.grazie.code. with ai.koog. but not ai.grazie.code.files.
    s/([^.])ai\.grazie\.code\.([^f]|f[^i]|fi[^l]|fil[^e]|file[^s]|files[^.])/\1ai.koog.\2/g;
    s/^ai\.grazie\.code\.([^f]|f[^i]|fi[^l]|fil[^e]|file[^s]|files[^.])/ai.koog.\1/g;

    # Replace ai.jetbrains.embeddings. with ai.koog.embeddings.
    s/([^.])ai\.jetbrains\.embeddings\./\1ai.koog.embeddings./g;
    s/^ai\.jetbrains\.embeddings\./ai.koog.embeddings./g;
  ' "$file" > "$temp_file"

  # Check if the file was modified
  if ! cmp -s "$file" "$temp_file"; then
    mv "$temp_file" "$file"
    log "  Modified: $file"
  else
    rm "$temp_file"
    log "  No changes needed: $file"
  fi
done

# Step 3: Move files to their new locations based on package changes
log "Moving files to their new locations..."

# Process directory moves for ai.jetbrains.code. (excluding ai.jetbrains.code.files.)
log "Processing ai.jetbrains.code. directories..."
find . -type d -path "*/ai/jetbrains/code/*" -not -path "*/ai/jetbrains/code/files/*" -not -path "*/\.*" -not -path "*/build/*" | while read src_dir; do
  # Extract the path after ai/jetbrains/code/
  rel_path=$(echo "$src_dir" | sed -E 's|.*/ai/jetbrains/code/||')

  if [ -n "$rel_path" ]; then
    # Create the destination directory
    dst_dir=$(echo "$src_dir" | sed -E 's|/ai/jetbrains/code/|/ai/koog/|')

    # Skip if source and destination are the same
    if [ "$src_dir" = "$dst_dir" ]; then
      continue
    fi

    # Create the destination directory if it doesn't exist
    if [ ! -d "$dst_dir" ]; then
      mkdir -p "$dst_dir"
      log "  Created directory: $dst_dir"
    fi

    # Move all files from source to destination
    find "$src_dir" -maxdepth 1 -type f | while read src_file; do
      filename=$(basename "$src_file")
      dst_file="$dst_dir/$filename"

      # Move the file (using mv instead of cp)
      if [ ! -f "$dst_file" ]; then
        mv "$src_file" "$dst_file"
        log "  Moved: $src_file -> $dst_file"
      else
        log "  Skipped (already exists): $dst_file"
      fi
    done
  fi
done

# Process directory moves for ai.grazie.code. (excluding ai.grazie.code.files.)
log "Processing ai.grazie.code. directories..."
find . -type d -path "*/ai/grazie/code/*" -not -path "*/ai/grazie/code/files/*" -not -path "*/\.*" -not -path "*/build/*" | while read src_dir; do
  # Extract the path after ai/grazie/code/
  rel_path=$(echo "$src_dir" | sed -E 's|.*/ai/grazie/code/||')

  if [ -n "$rel_path" ]; then
    # Create the destination directory
    dst_dir=$(echo "$src_dir" | sed -E 's|/ai/grazie/code/|/ai/koog/|')

    # Skip if source and destination are the same
    if [ "$src_dir" = "$dst_dir" ]; then
      continue
    fi

    # Create the destination directory if it doesn't exist
    if [ ! -d "$dst_dir" ]; then
      mkdir -p "$dst_dir"
      log "  Created directory: $dst_dir"
    fi

    # Move all files from source to destination
    find "$src_dir" -maxdepth 1 -type f | while read src_file; do
      filename=$(basename "$src_file")
      dst_file="$dst_dir/$filename"

      # Move the file (using mv instead of cp)
      if [ ! -f "$dst_file" ]; then
        mv "$src_file" "$dst_file"
        log "  Moved: $src_file -> $dst_file"
      else
        log "  Skipped (already exists): $dst_file"
      fi
    done
  fi
done

# Process directory moves for ai.jetbrains.embeddings.
log "Processing ai.jetbrains.embeddings. directories..."
find . -type d -path "*/ai/jetbrains/embeddings/*" -not -path "*/\.*" -not -path "*/build/*" | while read src_dir; do
  # Extract the path after ai/jetbrains/embeddings/
  rel_path=$(echo "$src_dir" | sed -E 's|.*/ai/jetbrains/embeddings/||')

  if [ -n "$rel_path" ]; then
    # Create the destination directory
    dst_dir=$(echo "$src_dir" | sed -E 's|/ai/jetbrains/embeddings/|/ai/koog/embeddings/|')

    # Skip if source and destination are the same
    if [ "$src_dir" = "$dst_dir" ]; then
      continue
    fi

    # Create the destination directory if it doesn't exist
    if [ ! -d "$dst_dir" ]; then
      mkdir -p "$dst_dir"
      log "  Created directory: $dst_dir"
    fi

    # Move all files from source to destination
    find "$src_dir" -maxdepth 1 -type f | while read src_file; do
      filename=$(basename "$src_file")
      dst_file="$dst_dir/$filename"

      # Move the file (using mv instead of cp)
      if [ ! -f "$dst_file" ]; then
        mv "$src_file" "$dst_file"
        log "  Moved: $src_file -> $dst_file"
      else
        log "  Skipped (already exists): $dst_file"
      fi
    done
  fi
done

log "Package migration completed successfully!"
