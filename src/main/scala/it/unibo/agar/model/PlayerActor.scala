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

      // Note: We'll handle registration differently since we have mixed message types
      // For now, we skip the registration and handle communication directly

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

          // Create DistributedGameStateManager for this player
          val manager = new DistributedGameStateManager(gameWorldActor)(context.system)

          // Create LocalView for this player
          val view = new LocalView(manager, playerId)

          // Set up mouse movement handling
          view.contents.head.reactions += {
            case e: scala.swing.event.MouseMoved =>
              context.self ! MouseMoved(e.point.x.toDouble, e.point.y.toDouble)
          }

          inactive(playerId, gameWorldActor, Some(view), Some(manager))

        case JoinGame =>
          context.log.info(s"Player $playerId joining game")

          // Generate random starting position
          val startX = Random.nextInt(1000).toDouble
          val startY = Random.nextInt(1000).toDouble

          // Tell GameWorldActor that this player joined
          gameWorldActor ! PlayerJoined(playerId, startX, startY)

          // Open LocalView window
          localView.foreach(_.open())

          active(playerId, gameWorldActor, localView.get, gameStateManager.get, startX, startY)

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
      localView: LocalView,
      gameStateManager: DistributedGameStateManager,
      lastX: Double,
      lastY: Double
  ): Behavior[PlayerMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case MouseMoved(mouseX, mouseY) =>
          // Calculate movement direction based on mouse position
          val viewSize = localView.size
          val centerX = viewSize.width / 2.0
          val centerY = viewSize.height / 2.0

          // Convert mouse position to movement direction
          val dx = (mouseX - centerX) * 0.01
          val dy = (mouseY - centerY) * 0.01

          // Send movement command to GameWorldActor
          gameWorldActor ! MovePlayer(playerId, dx, dy)

          Behaviors.same

        case PlayerWorldUpdate(world) =>
          // Update the local view with new world state
          // This will trigger repaint automatically
          localView.repaint()

          // Update player position tracking
          world.playerById(playerId) match
            case Some(player) =>
              active(playerId, gameWorldActor, localView, gameStateManager, player.x, player.y)
            case None =>
              context.log.warn(s"Player $playerId not found in world update")
              Behaviors.same

        case LeaveGame =>
          context.log.info(s"Player $playerId leaving game")

          // Notify GameWorldActor
          gameWorldActor ! PlayerLeft(playerId)

          // Close LocalView
          localView.close()

          // Return to inactive state
          inactive(playerId, gameWorldActor, Some(localView), Some(gameStateManager))

        case GetPlayerStatus(replyTo) =>
          // Get current player status from world
          val world = gameStateManager.getWorld
          world.playerById(playerId) match
            case Some(player) =>
              replyTo ! PlayerStatusResponse(playerId, isActive = true, player.mass)
            case None =>
              replyTo ! PlayerStatusResponse(playerId, isActive = false, 0.0)

          Behaviors.same

        case _ =>
          context.log.warn(s"Player $playerId received unexpected message while active: $message")
          Behaviors.same
    }

  /**
   * Handle termination - cleanup resources
   */
  private def stopping(
      playerId: String,
      gameWorldActor: ActorRef[GameMessage],
      localView: Option[LocalView]
  ): Behavior[PlayerMessage] =
    Behaviors.setup { context =>
      context.log.info(s"PlayerActor $playerId is stopping")

      // Unregister from GameWorldActor
      gameWorldActor ! UnregisterPlayer(playerId)
      gameWorldActor ! PlayerLeft(playerId)

      // Close LocalView if open
      localView.foreach(_.close())

      Behaviors.stopped
    }