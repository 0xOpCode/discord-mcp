#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIDFILE="$DIR/target/discord-mcp.pid"
LOGFILE="$DIR/target/logs/discord-http.log"

export DISCORD_TOKEN="${DISCORD_TOKEN:-MTUxMzg5ODQ0MTc0NTYzMzUyMg.GCnFsF.N5BQRcRmFYY9Jgc545_59vHj_vPgxZS0v0hpd4}"
export DISCORD_GUILD_ID="${DISCORD_GUILD_ID:-1540398107504680961}"
export SPRING_PROFILES_ACTIVE="http"

mkdir -p "$DIR/target/logs"

# Stop existing instance if running
if [ -f "$PIDFILE" ]; then
    OLD_PID=$(cat "$PIDFILE" 2>/dev/null || true)
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Stopping existing Discord MCP (PID: $OLD_PID)..."
        kill "$OLD_PID" 2>/dev/null || true
        sleep 1
        kill -9 "$OLD_PID" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
fi

# Kill any lingering instances
fuser -k 8085/tcp 2>/dev/null || true
pkill -9 -f "discord-mcp-1.0.0.jar" 2>/dev/null || true

echo "Starting Discord MCP Server in HTTP mode on port 8085..."
setsid java -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xverify:none -Xms64m -Xmx256m \
    -jar "$DIR/target/discord-mcp-1.0.0.jar" </dev/null > "$LOGFILE" 2>&1 &

NEW_PID=$!
disown -h $NEW_PID 2>/dev/null || true
echo "$NEW_PID" > "$PIDFILE"

# If called with --daemon / -d / background, exit immediately without waiting
if [ "$1" = "--daemon" ] || [ "$1" = "-d" ]; then
    exit 0
fi

echo "Process started with PID $NEW_PID. Waiting for health check..."

for i in {1..25}; do
    if curl -s -f http://127.0.0.1:8085/actuator/health >/dev/null 2>&1; then
        echo "✅ Discord MCP HTTP Server is UP and READY on http://127.0.0.1:8085/mcp (PID: $NEW_PID)"
        exit 0
    fi
    sleep 1
done

echo "⚠️ Server started (PID: $NEW_PID), check logs at $LOGFILE"
