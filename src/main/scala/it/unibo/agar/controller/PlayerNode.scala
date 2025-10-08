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

    // Add shutdown hook to clean up properly
    sys.addShutdownHook {
      println(s"\n🛑 Shutting down $playerName...")
      system.terminate()
    }

    // Block the main thread until the ActorSystem terminates
    // This prevents SBT from terminating the process immediately
    import scala.concurrent.Await
    import scala.concurrent.duration.Duration

    try {
      Await.result(system.whenTerminated, Duration.Inf)
      println(s"👋 $playerName terminated")
    } catch {
      case e: Exception =>
        println(s"❌ Error while waiting for termination: ${e.getMessage}")
    } finally {
      sys.exit(0)
    }

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
          if retryCount > 15 then
            context.log.error("❌ Failed to find GameWorld after 15 attempts. Is the SeedNode running?")
            println("❌ ERROR: Cannot find GameWorld!")
            println("   Make sure the SeedNode is running first:")
            println("   ./start-seed.sh")
            context.system.terminate()
            Behaviors.stopped
          else
            context.scheduleOnce(2.seconds, context.self, Initialize)
            waitingForGameWorld(playerName, width, height, retryCount + 1)

        case GameWorldFound(gameWorld) =>
          context.log.info(s"✅ Found GameWorld! Creating player: $playerName")
          println(s"✅ Connected to GameWorld!")
          println(s"🎮 Creating player: $playerName")

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

          // Capture references outside EDT to avoid ActorContext access from Swing thread
          val selfRef = context.self

          // Create LocalView on EDT
          onEDT {
            val localView = new LocalView(playerManager, playerName)

            // Override close operation to handle cleanup
            localView.peer.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE)

            // Listen for window closing
            localView.reactions += {
              case scala.swing.event.WindowClosing(_) =>
                // Use println instead of context.log (EDT thread can't access context)
                println(s"\n🚪 Player $playerName is closing window...")
                playerActor ! LeaveGame
                localView.dispose()
                // Use captured reference instead of context.self
                selfRef ! Shutdown
            }

            localView.visible = true
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
          // Ignore - we'll keep waiting via Subscribe
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
          context.log.info(s"🛑 Shutting down player node: $playerName")
          println(s"👋 Player $playerName disconnected")
          timer.cancel()
          // Terminate the entire actor system to exit the JVM
          context.system.terminate()
          Behaviors.stopped

        case _ =>
          context.log.debug(s"Received message: $message")
          Behaviors.same
    }
