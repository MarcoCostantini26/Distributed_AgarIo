#!/bin/bash
# Start a Player Node
# Usage: ./start-player.sh <playerName> <port>
# Example: ./start-player.sh Alice 2552

PLAYER_NAME=${1:-"Player"}
PORT=${2:-2552}

echo "🎮 Starting Player: $PLAYER_NAME on port $PORT..."
sbt -Dakka.remote.artery.canonical.port=$PORT "runMain it.unibo.agar.controller.PlayerNode $PLAYER_NAME"
