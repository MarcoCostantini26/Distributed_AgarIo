package it.unibo.agar.model

import akka.actor.typed.ActorRef
import it.unibo.agar.Message

/** Messages for the distributed game system */
sealed trait GameMessage extends Message

/** Message to move a player in a specific direction */
case class MovePlayer(id: String, dx: Double, dy: Double) extends GameMessage

/** Message to request the current world state */
case class GetWorld(replyTo: ActorRef[World]) extends GameMessage

/** Message to advance the game by one tick (called every 30ms) */
case object Tick extends GameMessage

/** Message when a new player joins the game */
case class PlayerJoined(id: String, x: Double, y: Double, mass: Double = 120.0) extends GameMessage

/** Message when a player leaves the game */
case class PlayerLeft(id: String) extends GameMessage

/** Message to spawn new food in the world */
case class SpawnFood(food: Food) extends GameMessage

/** Message to remove food from the world (when eaten) */
case class RemoveFood(foodIds: Seq[String]) extends GameMessage

/** Message to check if game end condition is met */
case class CheckGameEnd(replyTo: ActorRef[GameEndResult]) extends GameMessage

/** Response for game end check */
sealed trait GameEndResult extends Message
case object GameContinues extends GameEndResult
case class GameEnded(winnerId: String, finalMass: Double) extends GameEndResult

/** Message to broadcast world state to all players */
case class WorldStateUpdate(world: World) extends GameMessage

/** Message to register a new player node */
case class RegisterPlayer(playerId: String, playerNode: ActorRef[GameMessage]) extends GameMessage

/** Message to unregister a player node */
case class UnregisterPlayer(playerId: String) extends GameMessage

// ============= Player Actor Specific Messages =============

/** Messages specific to PlayerActor */
sealed trait PlayerMessage extends Message

/** Message from UI when mouse moves (for player movement) */
case class MouseMoved(x: Double, y: Double) extends PlayerMessage

/** Message to notify player of world state update */
case class PlayerWorldUpdate(world: World) extends PlayerMessage

/** Message to start the player actor */
case class StartPlayer(worldWidth: Int, worldHeight: Int) extends PlayerMessage

/** Message when player wants to join the game */
case object JoinGame extends PlayerMessage

/** Message when player wants to leave the game */
case object LeaveGame extends PlayerMessage

/** Message to get the player's current status */
case class GetPlayerStatus(replyTo: ActorRef[PlayerStatusResponse]) extends PlayerMessage

/** Response with player status */
case class PlayerStatusResponse(playerId: String, isActive: Boolean, currentMass: Double) extends PlayerMessage