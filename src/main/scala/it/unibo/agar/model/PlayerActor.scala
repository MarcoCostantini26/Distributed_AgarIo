package it.unibo.agar.model

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import it.unibo.agar.view.LocalView
import scala.util.Random

object PlayerActor:

  def apply(playerId: String, gameWorldActor: ActorRef[GameMessage]): Behavior[PlayerMessage] =
    Behaviors.setup { context =>
      context.log.info(s"Starting PlayerActor for player: $playerId")

      val gameMessageAdapter: ActorRef[GameMessage] = context.messageAdapter[GameMessage] {
        case WorldStateUpdate(world) => PlayerWorldUpdate(world)
        case _ =>
          null.asInstanceOf[PlayerMessage]
      }
      gameWorldActor ! RegisterPlayer(playerId, gameMessageAdapter)
      inactive(playerId, gameWorldActor, None, None)
    }

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
          inactive(playerId, gameWorldActor, None, None)

        case JoinGame =>
          context.log.info(s"Player $playerId joining game")
          val startX = Random.nextInt(1000).toDouble
          val startY = Random.nextInt(1000).toDouble
          val startMass = 120.0
          gameWorldActor ! PlayerJoined(playerId, startX, startY, startMass)
          active(playerId, gameWorldActor, startX, startY)

        case PlayerWorldUpdate(world) =>
          Behaviors.same

        case _ =>
          Behaviors.same
    }

  private def active(
      playerId: String,
      gameWorldActor: ActorRef[GameMessage],
      lastX: Double,
      lastY: Double
  ): Behavior[PlayerMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case PlayerWorldUpdate(world) =>
          world.playerById(playerId) match
            case Some(player) =>
              active(playerId, gameWorldActor, player.x, player.y)
            case None =>
              Behaviors.same

        case LeaveGame =>
          context.log.info(s"Player $playerId leaving game")
          gameWorldActor ! PlayerLeft(playerId)
          gameWorldActor ! UnregisterPlayer(playerId)
          inactive(playerId, gameWorldActor, None, None)

        case _ =>
          Behaviors.same
    }

