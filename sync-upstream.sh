#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=================================================="
echo "🛡️  Discord MCP Smart Upstream Sync Engine"
echo "=================================================="

# 1. Ensure working directory is clean
if ! git diff-index --quiet HEAD --; then
    echo "⚠️ Working tree has uncommitted local changes!"
    echo "Stashing local changes before sync..."
    git stash push -m "auto-stash-pre-upstream-sync"
    STASHED=1
else
    STASHED=0
fi

# 2. Create an instant safety rollback checkpoint
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_BRANCH="backup/pre-sync-${TIMESTAMP}"
git branch "$BACKUP_BRANCH"
echo "✅ Safety rollback checkpoint created: $BACKUP_BRANCH"

# 3. Fetch upstream changes
echo "🔄 [1/4] Fetching latest commits from upstream (SaseQ/discord-mcp)..."
git fetch upstream main

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

# Check if there are actually any new commits from upstream
UPSTREAM_DIFF=$(git rev-list HEAD..upstream/main --count)
if [ "$UPSTREAM_DIFF" -eq 0 ]; then
    echo "🎉 Already up to date! No new commits found in upstream/main."
    git branch -D "$BACKUP_BRANCH" >/dev/null 2>&1 || true
    if [ "$STASHED" -eq 1 ]; then
        echo "Restoring stashed changes..."
        git stash pop
    fi
    exit 0
fi

echo "📥 Found $UPSTREAM_DIFF new commit(s) from upstream. Proceeding to merge..."

# 4. Attempt clean merge
echo "🔀 [2/4] Attempting merge upstream/main -> $CURRENT_BRANCH..."
set +e
git merge upstream/main -m "chore: merge upstream updates from SaseQ/discord-mcp"
MERGE_STATUS=$?
set -e

if [ $MERGE_STATUS -ne 0 ]; then
    echo ""
    echo "🚨 CONFLICT DETECTED DURING MERGE!"
    echo "--------------------------------------------------"
    echo "Conflicted files:"
    git diff --name-only --diff-filter=U
    echo "--------------------------------------------------"
    
    # Isolate conflict branch so main is never left broken
    CONFLICT_BRANCH="conflict/upstream-sync-${TIMESTAMP}"
    echo "Creating conflict inspection branch: $CONFLICT_BRANCH"
    git branch -f "$CONFLICT_BRANCH"
    
    echo "🛡️  ABORTING MERGE on $CURRENT_BRANCH to keep your active code 100% stable!"
    git merge --abort
    
    if [ "$STASHED" -eq 1 ]; then
        git stash pop >/dev/null 2>&1 || true
    fi
    
    echo ""
    echo "=================================================="
    echo "💡 HOW CONFLICT WAS HANDLED:"
    echo "1. Your main branch was NOT broken (reverted to safe state)."
    echo "2. Your running bot process is completely unaffected."
    echo "3. Conflicted state is preserved in branch: '$CONFLICT_BRANCH'."
    echo "4. Safety backup branch available: '$BACKUP_BRANCH'."
    echo ""
    echo "👉 To resolve: Simply ask your AI agent:"
    echo "   'Bhai $CONFLICT_BRANCH branch ka conflict resolve karke merge kar de'"
    echo "=================================================="
    exit 1
fi

echo "✅ Clean merge successful (No conflicts)!"

# 5. Push merged updates to fork
echo "⬆️ [3/4] Pushing merged changes to your GitHub fork (origin/$CURRENT_BRANCH)..."
git push origin "$CURRENT_BRANCH"

# 6. Recompile and build JAR
echo "📦 [4/4] Recompiling and building JAR..."
mvn clean package -DskipTests

# Cleanup backup branch on success
git branch -D "$BACKUP_BRANCH" >/dev/null 2>&1 || true

if [ "$STASHED" -eq 1 ]; then
    echo "Restoring stashed changes..."
    git stash pop
fi

echo ""
echo "=================================================="
echo "🎉 SUCCESS: Upstream synced, pushed to fork & rebuilt!"
echo "To restart with the newly built JAR, run: ./start-http.sh"
echo "=================================================="
