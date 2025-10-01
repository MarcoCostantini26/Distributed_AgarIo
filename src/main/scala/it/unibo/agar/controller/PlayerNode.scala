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

/**
 * Player node that connects to the seed node and creates a single player.
 * Each player runs in its own terminal/JVM.
 *
 * Usage:
 *   Terminal 1: sbt "runMain it.unibo.agar.controller.PlayerNode Alice"
 *   Terminal 2: sbt "runMain it.unibo.agar.controller.PlayerNode Bob"
 *   Terminal 3: sbt "runMain it.unibo.agar.controller.PlayerNode Charlie"
 */
object PlayerNode:

  sealed trait PlayerNodeCommand
  case object Initialize extends PlayerNodeCommand
  case class GameWorldFound(gameWorld: ActorRef[GameMessage]) extends PlayerNodeCommand
  case object GameWorldNotFound extends PlayerNodeCommand
  case object Shutdown extends PlayerNodeCommand

  val GameWorldKey: ServiceKey[GameMessage] = ServiceKey[GameMessage]("game-world-manager")

  def main(args: Array[String]): Unit =
    val playerName = if args.isEmpty then
      println("❌ Error: Please provide a player name")
      println("   Usage: sbt \"runMain it.unibo.agar.controller.PlayerNode <name>\"")
      sys.exit(1)
    else
      args(0)

    println("=" * 60)
    println(s"🎮 Starting PLAYER NODE: $playerName")
    println("=" * 60)

    val width = 1000
    val height = 1000

    // Create actor system with unique port
    val system: ActorSystem[PlayerNodeCommand] = ActorSystem(
      playerBehavior(playerName, width, height),
      "ClusterSystem"
    )

    println(s"\n✅ Player node for '$playerName' started!")
    println("📡 Connecting to seed node...")
    println()

  def playerBehavior(playerName: String, width: Int, height: Int): Behavior[PlayerNodeCommand] =
    Behaviors.setup { context =>
      context.log.info(s"🎮 Initializing Player Node for: $playerName")

      // Subscribe to find GameWorld singleton
      val receptionistAdapter = context.messageAdapter[Receptionist.Listing] {
        case GameWorldKey.Listing(listings) =>
          listings.headOption match
            case Some(gameWorld) => GameWorldFound(gameWorld)
            case None => GameWorldNotFound
      }

      context.system.receptionist ! Receptionist.Subscribe(GameWorldKey, receptionistAdapter)

      // Schedule periodic check for GameWorld
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
            println(s"⏳ Waiting for GameWorld... (attempt $retryCount)")
          context.scheduleOnce(2.seconds, context.self, Initialize)
          waitingForGameWorld(playerName, width, height, retryCount + 1)

        case GameWorldFound(gameWorld) =>
          context.log.info(s"✅ Found GameWorld! Creating player: $playerName")
          println(s"✅ Connected to GameWorld!")
          println(s"🎮 Creating player: $playerName")

          // Register GameWorld with receptionist so others can find it
          context.system.receptionist ! Receptionist.Register(GameWorldKey, gameWorld)

          // Create PlayerActor
          val playerActor = context.spawn(
            PlayerActor(playerName, gameWorld),
            s"player-actor-$playerName"
          )

          // Initialize player
          playerActor ! StartPlayer(width, height)
          playerActor ! JoinGame

          // Create game state manager
          val playerManager = new DistributedGameStateManager(gameWorld)(context.system)

          // Create LocalView on EDT
          onEDT {
            val localView = new LocalView(playerManager, playerName)
            localView.visible = true

            // Listen for window closing
            localView.reactions += {
              case scala.swing.event.WindowClosing(_) =>
                context.log.info(s"🚪 Player $playerName leaving game")
                playerActor ! LeaveGame
                context.self ! Shutdown
            }
          }

          // Set up repaint timer
          val timer = new Timer()
          val task: TimerTask = new TimerTask:
            override def run(): Unit =
              onEDT(Window.getWindows.foreach(_.repaint()))

          timer.scheduleAtFixedRate(task, 0, 30)

          context.log.info(s"✅ Player $playerName initialized successfully")
          println(s"✅ Player $playerName ready!")
          println("   Close the window to disconnect")
          println()

          running(playerName, playerActor, timer)

        case GameWorldNotFound =>
          context.log.warn("❌ GameWorld not found in receptionist")
          Behaviors.same

        case Shutdown =>
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
          context.log.info(s"🛑 Shutting down player node: $playerName")
          println(s"\n👋 Player $playerName disconnected")
          timer.cancel()
          Behaviors.stopped

        case _ =>
          context.log.debug(s"Received message: $message")
          Behaviors.same
    }
