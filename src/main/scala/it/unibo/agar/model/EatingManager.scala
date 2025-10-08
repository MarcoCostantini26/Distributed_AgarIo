package it.unibo.agar.model

object EatingManager:

  private val MASS_MARGIN = 1.1 // 10% bigger to eat

  // Check if two entities collide
  private def collides(e1: Entity, e2: Entity): Boolean =
    e1.distanceTo(e2) < (e1.radius + e2.radius)

  // Determines if a player can eat a food
  def canEatFood(player: Player, food: Food): Boolean =
    collides(player, food) && player.mass > food.mass

  // Determines if a player can eat another player
  def canEatPlayer(player: Player, other: Player): Boolean =
    collides(player, other) && player.mass > other.mass * MASS_MARGIN
