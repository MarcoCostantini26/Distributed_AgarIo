package it.unibo.agar.model

trait GameStateManager:
  def getWorld: World
  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit
  def tick(): Unit