#!/usr/bin/env bash

# Define the output file name
OUTPUT_FILE="a.txt"

# Clear the output file if it exists
echo "--- GATEKEEPER PROJECT SNAPSHOT (Created: $(date)) ---" >"$OUTPUT_FILE"

# Define the directories and file patterns to include
# We exclude build directories, .gradle, and .git to keep the file clean
find . -maxdepth 10 \
    -not -path '*/.*' \
    -not -path '*/build/*' \
    -not -path '*/.gradle/*' \
    -type f \( \
    -name "*.kt" -o \
    -name "*.kts" -o \
    -name "*.xml" -o \
    -name "*.properties" -o \
    -name "GEMINI.md" -o \
    -name "flake.nix" \
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
