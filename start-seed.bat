@echo off
REM Start the Seed Node (GameWorld + GlobalView)
REM This MUST be started first!

echo Starting Seed Node...
sbt -Dakka.remote.artery.canonical.port=2551 "runMain it.unibo.agar.controller.SeedNode"
