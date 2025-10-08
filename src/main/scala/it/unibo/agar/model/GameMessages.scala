package it.unibo.agar.model

import akka.actor.typed.ActorRef

sealed trait GameMessage

case class MovePlayer(id: String, dx: Double, dy: Double) extends GameMessage

case class GetWorld(replyTo: ActorRef[World]) extends GameMessage

case object Tick extends GameMessage

case class PlayerJoined(id: String, x: Double, y: Double, mass: Double = 120.0) extends GameMessage

case class PlayerLeft(id: String) extends GameMessage

case class SpawnFood(food: Food) extends GameMessage

case class RemoveFood(foodIds: Seq[String]) extends GameMessage

case class WorldStateUpdate(world: World) extends GameMessage

case class RegisterPlayer(playerId: String, playerNode: ActorRef[GameMessage]) extends GameMessage

case class UnregisterPlayer(playerId: String) extends GameMessage

// ============= Player Actor Specific Messages =============

sealed trait PlayerMessage

case class MouseMoved(x: Double, y: Double) extends PlayerMessage

case class PlayerWorldUpdate(world: World) extends PlayerMessage

case class StartPlayer(worldWidth: Int, worldHeight: Int) extends PlayerMessage

case object JoinGame extends PlayerMessage

case object LeaveGame extends PlayerMessage