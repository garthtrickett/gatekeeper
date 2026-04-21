#!/usr/bin/env bash

# 42069-1418
# Define the output file name
OUTPUT_FILE="a.txt"

# Clear the output file if it exists
echo "--- GATEKEEPER PROJECT SNAPSHOT (Created: $(date)) ---" >"$OUTPUT_FILE"

# Define the directories and file patterns to include
# Added *.nix to capture flake.nix and NixOS modules.
# Depth remains 10 which is plenty for KMP nested src folders.
find . -maxdepth 10 \
    -not -path '*/.*' \
    -not -path '*/build/*' \
    -not -path '*/.gradle/*' \
    -type f \( \
    -name "*.kt" -o \
    -name "*.sq" -o \
    -name "*.kts" -o \
    -name "*.xml" -o \
    -name "*.properties" -o \
    -name "*.nix" -o \
    -name "GEMINI.md" \
    \) | sort | while read -r file; do

    # Print a clear header for each file
    echo "" >>"$OUTPUT_FILE"
    echo "############################################################" >>"$OUTPUT_FILE"
    echo "##########          START $file          ##########" >>"$OUTPUT_FILE"
    echo "############################################################" >>"$OUTPUT_FILE"
    echo "" >>"$OUTPUT_FILE"

    # Append the content of the file
    cat "$file" >>"$OUTPUT_FILE"

    echo "" >>"$OUTPUT_FILE"
    echo "##########          END $file          ##########" >>"$OUTPUT_FILE"
done

echo "✅ Project aggregated into $OUTPUT_FILE"
