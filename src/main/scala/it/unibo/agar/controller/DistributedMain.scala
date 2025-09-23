package it.unibo.agar.controller

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import it.unibo.agar.model.*
import it.unibo.agar.view.{GlobalView, LocalView}
import scala.swing.SimpleSwingApplication
import scala.swing.Frame
import java.util.Timer
import java.util.TimerTask
import scala.swing.Swing.onEDT
import java.awt.Window

/**
 * Distributed version of the main application using Akka Cluster
 */
object DistributedMain extends SimpleSwingApplication:

  private val width = 1000
  private val height = 1000
  private val numPlayers = 4
  private val numFoods = 100

  // Initialize the cluster
  private val initialPlayers = GameInitializer.initialPlayers(numPlayers, width, height)
  private val initialFoods = GameInitializer.initialFoods(numFoods, width, height)
  private val initialWorld = World(width, height, initialPlayers, initialFoods)

  // Create the actor system with cluster
  private val system: ActorSystem[GameMessage] = ActorSystem(
    GameWorldSingleton(initialWorld),
    "ClusterSystem"
  )

  // Create distributed game state manager
  private val distributedManager = new DistributedGameStateManager(system)(system)

  // Set up timer for game ticks
  private val timer = new Timer()
  private val task: TimerTask = new TimerTask:
    override def run(): Unit =
      // Send tick to the singleton
      system ! Tick
      // Update UI
      onEDT(Window.getWindows.foreach(_.repaint()))

  timer.scheduleAtFixedRate(task, 0, 30) // every 30ms

  override def top: Frame =
    // Create views using the distributed manager
    new LocalView(distributedManager, "p1").open()
    new LocalView(distributedManager, "p2").open()

    // Return GlobalView as main frame
    new GlobalView(distributedManager)

/**
 * Guardian actor for the distributed system
 */
object DistributedGameGuardian:

  sealed trait GuardianMessage
  case object StartGame extends GuardianMessage
  case class CreatePlayer(playerId: String) extends GuardianMessage

  def apply(): Behavior[GuardianMessage] =
    Behaviors.setup { context =>
      context.log.info("DistributedGameGuardian started")

      val width = 1000
      val height = 1000
      val numPlayers = 4
      val numFoods = 100

      // Initialize world
      val initialPlayers = GameInitializer.initialPlayers(numPlayers, width, height)
      val initialFoods = GameInitializer.initialFoods(numFoods, width, height)
      val initialWorld = World(width, height, initialPlayers, initialFoods)

      // Start the GameWorld singleton
      val gameWorldSingleton = context.spawn(
        GameWorldSingleton(initialWorld),
        "game-world-manager"
      )

      // Set up game tick timer
      val timer = new Timer()
      val task: TimerTask = new TimerTask:
        override def run(): Unit =
          gameWorldSingleton ! Tick

      timer.scheduleAtFixedRate(task, 0, 30)

      running(gameWorldSingleton)
    }

  private def running(gameWorldRef: akka.actor.typed.ActorRef[GameMessage]): Behavior[GuardianMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case StartGame =>
          context.log.info("Starting distributed game...")
          Behaviors.same

        case CreatePlayer(playerId) =>
          context.log.info(s"Creating player: $playerId")
          val playerActor = context.spawn(
            PlayerActor(playerId, gameWorldRef),
            s"player-$playerId"
          )

          // Start the player
          playerActor ! StartPlayer(1000, 1000)
          playerActor ! JoinGame

          Behaviors.same
    }