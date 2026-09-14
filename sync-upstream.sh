#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "🔄 [1/4] Fetching latest changes from upstream (SaseQ/discord-mcp)..."
git fetch upstream main

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "🔀 [2/4] Merging upstream/main into local $CURRENT_BRANCH..."
git merge upstream/main -m "chore: merge upstream updates from SaseQ/discord-mcp" || {
    echo "⚠️ Merge conflict detected! Please resolve conflicts manually and commit."
    exit 1
}

echo "⬆️ [3/4] Pushing merged changes to your fork (origin/$CURRENT_BRANCH)..."
git push origin "$CURRENT_BRANCH"

echo "📦 [4/4] Recompiling and repackaging JAR..."
mvn clean package -DskipTests

echo "✅ Upstream sync and rebuild completed successfully!"
echo "To restart the running server with the new JAR, run: ./start-http.sh"
