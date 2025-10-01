package it.unibo.agar.model

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import it.unibo.agar.view.LocalView
import scala.util.Random

/**
 * Actor representing a single player in the distributed game.
 * Each player runs on their own node and manages their LocalView.
 */
object PlayerActor:

  /**
   * Create a new PlayerActor
   * @param playerId Unique identifier for this player
   * @param gameWorldActor Reference to the central GameWorldActor
   * @return Behavior for the PlayerActor
   */
  def apply(playerId: String, gameWorldActor: ActorRef[GameMessage]): Behavior[PlayerMessage] =
    Behaviors.setup { context =>
      context.log.info(s"Starting PlayerActor for player: $playerId")

      // Create a message adapter to receive GameMessage as PlayerMessage
      val gameMessageAdapter: ActorRef[GameMessage] = context.messageAdapter[GameMessage] {
        case WorldStateUpdate(world) => PlayerWorldUpdate(world)
        case _ =>
          // Ignore other game messages
          null.asInstanceOf[PlayerMessage]
      }

      // Register this player actor to receive world updates
      gameWorldActor ! RegisterPlayer(playerId, gameMessageAdapter)

      inactive(playerId, gameWorldActor, None, None)
    }

  /**
   * Initial state - player not yet in game
   */
  private def inactive(
      playerId: String,
      gameWorldActor: ActorRef[GameMessage],
      localView: Option[LocalView],
      gameStateManager: Option[DistributedGameStateManager]
  ): Behavior[PlayerMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case StartPlayer(worldWidth, worldHeight) =>
          context.log.info(s"Starting player $playerId with world size ${worldWidth}x${worldHeight}")

          // GameStateManager should be passed from outside
          // For now we keep it None and expect it to be provided
          inactive(playerId, gameWorldActor, None, None)

        case JoinGame =>
          context.log.info(s"Player $playerId joining game")

          // Generate random starting position
          val startX = Random.nextInt(1000).toDouble
          val startY = Random.nextInt(1000).toDouble

          // Tell GameWorldActor that this player joined
          gameWorldActor ! PlayerJoined(playerId, startX, startY)

          active(playerId, gameWorldActor, startX, startY)

        case PlayerWorldUpdate(world) =>
          // Receive world updates even when inactive
          Behaviors.same

        case _ =>
          context.log.warn(s"Player $playerId received message while inactive: $message")
          Behaviors.same
    }

  /**
   * Active state - player is in the game
   */
  private def active(
      playerId: String,
      gameWorldActor: ActorRef[GameMessage],
      lastX: Double,
      lastY: Double
  ): Behavior[PlayerMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case PlayerWorldUpdate(world) =>
          // World state updated - this is received via broadcast from GameWorldActor
          // The DistributedGameStateManager will be updated via polling
          // Update player position tracking
          world.playerById(playerId) match
            case Some(player) =>
              active(playerId, gameWorldActor, player.x, player.y)
            case None =>
              context.log.warn(s"Player $playerId not found in world update")
              Behaviors.same

        case LeaveGame =>
          context.log.info(s"Player $playerId leaving game")

          // Notify GameWorldActor
          gameWorldActor ! PlayerLeft(playerId)
          gameWorldActor ! UnregisterPlayer(playerId)

          // Return to inactive state
          inactive(playerId, gameWorldActor, None, None)

        case GetPlayerStatus(replyTo) =>
          // Return current position as status
          replyTo ! PlayerStatusResponse(playerId, isActive = true, 0.0)
          Behaviors.same

        case _ =>
          context.log.warn(s"Player $playerId received unexpected message while active: $message")
          Behaviors.same
    }

