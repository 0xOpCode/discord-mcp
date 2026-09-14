#!/bin/bash
export DISCORD_TOKEN="${DISCORD_TOKEN:-MTUxMzg5ODQ0MTc0NTYzMzUyMg.GCnFsF.N5BQRcRmFYY9Jgc545_59vHj_vPgxZS0v0hpd4}"
export DISCORD_GUILD_ID="${DISCORD_GUILD_ID:-1540398107504680961}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-stdio}"

exec java -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xss256k -Xms32m -Xmx256m -jar /root/discord-mcp/target/discord-mcp-1.0.0.jar "$@"
