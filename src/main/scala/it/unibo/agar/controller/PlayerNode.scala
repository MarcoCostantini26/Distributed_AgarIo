package it.unibo.agar.controller

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import it.unibo.agar.model.*
import it.unibo.agar.view.LocalView
import scala.swing.Swing.onEDT
import java.awt.Window
import java.util.{Timer, TimerTask}
import scala.concurrent.duration.*

object PlayerNode:

  sealed trait PlayerNodeCommand
  case object Initialize extends PlayerNodeCommand
  case class GameWorldFound(gameWorld: ActorRef[GameMessage]) extends PlayerNodeCommand
  case object GameWorldNotFound extends PlayerNodeCommand
  case object Shutdown extends PlayerNodeCommand

  val GameWorldKey: ServiceKey[GameMessage] = ServiceKey[GameMessage]("game-world-manager")

  def main(args: Array[String]): Unit =
    val playerName = if args.isEmpty then
      println("Error: Please provide a player name")
      sys.exit(1)
    else
      args(0)

    val width = 1000
    val height = 1000

    val system: ActorSystem[PlayerNodeCommand] = ActorSystem(
      playerBehavior(playerName, width, height),
      "ClusterSystem"
    )

    println(s"\nPlayer node for '$playerName' started!")
    println("Connecting to seed node...")
    println()
    sys.addShutdownHook {
      println(s"\nShutting down $playerName...")
      system.terminate()
    }

    import scala.concurrent.Await
    import scala.concurrent.duration.Duration
    try {
      Await.result(system.whenTerminated, Duration.Inf)
      println(s"$playerName terminated")
    } catch {
      case e: Exception => println(s"Error while waiting for termination: ${e.getMessage}")
    } finally {
      sys.exit(0)
    }

  def playerBehavior(playerName: String, width: Int, height: Int): Behavior[PlayerNodeCommand] =
    Behaviors.setup { context =>
      context.log.info(s"Initializing Player Node for: $playerName")
      val receptionistAdapter = context.messageAdapter[Receptionist.Listing] {
        case GameWorldKey.Listing(listings) =>
          listings.headOption match
            case Some(gameWorld) => GameWorldFound(gameWorld)
            case None => GameWorldNotFound
      }
      context.system.receptionist ! Receptionist.Subscribe(GameWorldKey, receptionistAdapter)
      context.scheduleOnce(2.seconds, context.self, Initialize)
      waitingForGameWorld(playerName, width, height, retryCount = 0)
    }

  private def waitingForGameWorld(
      playerName: String,
      width: Int,
      height: Int,
      retryCount: Int
  ): Behavior[PlayerNodeCommand] =
    Behaviors.receive { (context, message) =>
      message match
        case Initialize =>
          if retryCount > 0 then
            context.log.warn(s"Still waiting for GameWorld... (attempt $retryCount)")
            println(s"Waiting for GameWorld... (attempt $retryCount)")
          if retryCount > 15 then
            println("ERROR: Cannot find GameWorld!")
            context.system.terminate()
            Behaviors.stopped
          else
            context.scheduleOnce(2.seconds, context.self, Initialize)
            waitingForGameWorld(playerName, width, height, retryCount + 1)

        case GameWorldFound(gameWorld) =>
          context.log.info(s"Found GameWorld! Creating player: $playerName")

          val playerActor = context.spawn(
            PlayerActor(playerName, gameWorld),
            s"player-actor-$playerName"
          )

          playerActor ! StartPlayer(width, height)
          playerActor ! JoinGame

          val playerManager = new DistributedGameStateManager(gameWorld)(context.system)
          val selfRef = context.self

          onEDT {
            val localView = new LocalView(playerManager, playerName)
            localView.peer.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE)
            localView.reactions += {
              case scala.swing.event.WindowClosing(_) =>
                playerActor ! LeaveGame
                localView.dispose()
                selfRef ! Shutdown
            }
            localView.visible = true
          }
          val timer = new Timer()
          val task: TimerTask = new TimerTask:
            override def run(): Unit =
              onEDT(Window.getWindows.foreach(_.repaint()))
          timer.scheduleAtFixedRate(task, 0, 30)
          running(playerName, playerActor, timer)

        case GameWorldNotFound =>
          context.log.debug("GameWorld not found yet, continuing to wait...")
          Behaviors.same

        case Shutdown =>
          context.log.warn("Received Shutdown before GameWorld was found")
          context.system.terminate()
          Behaviors.stopped
    }

  private def running(
      playerName: String,
      playerActor: ActorRef[PlayerMessage],
      timer: Timer
  ): Behavior[PlayerNodeCommand] =
    Behaviors.receive { (context, message) =>
      message match
        case Shutdown =>
          context.log.info(s"Shutting down player node: $playerName")
          timer.cancel()
          context.system.terminate()
          Behaviors.stopped

        case _ =>
          context.log.debug(s"Received message: $message")
          Behaviors.same
    }
