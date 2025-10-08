package it.unibo.agar.model

object EatingManager:

  private val MASS_MARGIN = 1.1 

  private def collides(e1: Entity, e2: Entity): Boolean =
    e1.distanceTo(e2) < (e1.radius + e2.radius)

  def canEatFood(player: Player, food: Food): Boolean =
    collides(player, food) && player.mass > food.mass

  def canEatPlayer(player: Player, other: Player): Boolean =
    collides(player, other) && player.mass > other.mass * MASS_MARGIN
