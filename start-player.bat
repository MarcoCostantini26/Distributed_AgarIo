@echo off
REM Start a Player Node
REM Usage: start-player.bat <playerName> <port>
REM Example: start-player.bat Alice 2552

set PLAYER_NAME=%1
set PORT=%2

if "%PLAYER_NAME%"=="" set PLAYER_NAME=Player
if "%PORT%"=="" set PORT=2552

echo Starting Player: %PLAYER_NAME% on port %PORT%...
sbt -Dakka.remote.artery.canonical.port=%PORT% "runMain it.unibo.agar.controller.PlayerNode %PLAYER_NAME%"
