#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIDFILE="$DIR/target/discord-mcp.pid"

if [ -f "$PIDFILE" ]; then
    PID=$(cat "$PIDFILE" 2>/dev/null || true)
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        echo "Stopping Discord MCP (PID: $PID)..."
        kill "$PID" 2>/dev/null || true
        sleep 1
        kill -9 "$PID" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
fi

fuser -k 8085/tcp 2>/dev/null || true
pkill -9 -f "discord-mcp-1.0.0.jar" 2>/dev/null || true
echo "✅ Discord MCP stopped."
