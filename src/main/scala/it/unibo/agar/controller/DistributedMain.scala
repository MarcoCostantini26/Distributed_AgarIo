package it.unibo.agar.controller

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
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

  // Create the actor system with cluster using a guardian behavior
  private val system: ActorSystem[GuardianCommand] = ActorSystem(
    guardian(),
    "ClusterSystem"
  )

  // References to actors (set after system starts)
  @volatile private var gameWorldRef: ActorRef[GameMessage] = _
  @volatile private var player1Manager: DistributedGameStateManager = _
  @volatile private var player2Manager: DistributedGameStateManager = _
  @volatile private var globalManager: DistributedGameStateManager = _

  // Guardian commands
  sealed trait GuardianCommand
  case object StartSystem extends GuardianCommand
  case class GameWorldStarted(ref: ActorRef[GameMessage]) extends GuardianCommand

  /**
   * Guardian behavior that initializes all actors
   */
  def guardian(): Behavior[GuardianCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting DistributedMain guardian")

      // Start GameWorldSingleton
      val gameWorld = context.spawn(
        GameWorldSingleton(initialWorld),
        "game-world-manager"
      )
      gameWorldRef = gameWorld

      // Create FoodManagerActor
      val foodManager = context.spawn(
        FoodManagerActor(gameWorld, width, height),
        "food-manager"
      )

      // Create a SINGLE shared DistributedGameStateManager
      // All views use the same manager because they all need the same world state
      val sharedManager = new DistributedGameStateManager(gameWorld)(context.system)
      player1Manager = sharedManager
      player2Manager = sharedManager
      globalManager = sharedManager

      // Create PlayerActors
      val playerIds = Seq("p1", "p2")

      playerIds.foreach { playerId =>
        val playerActor = context.spawn(
          PlayerActor(playerId, gameWorld),
          s"player-actor-$playerId"
        )

        // Initialize player
        playerActor ! StartPlayer(width, height)
        playerActor ! JoinGame
      }

      // Set up game tick timer
      val timer = new Timer()
      val task: TimerTask = new TimerTask:
        override def run(): Unit =
          gameWorld ! Tick
          onEDT(Window.getWindows.foreach(_.repaint()))

      timer.scheduleAtFixedRate(task, 0, 30) // every 30ms

      context.log.info("DistributedMain guardian started successfully")

      Behaviors.receiveMessage { msg =>
        context.log.warn(s"Guardian received unexpected message: $msg")
        Behaviors.same
      }
    }

  override def top: Frame =
    // Wait for system to initialize
    Thread.sleep(500)

    // Create views using the distributed managers
    new LocalView(player1Manager, "p1").open()
    new LocalView(player2Manager, "p2").open()

    // Return GlobalView as main frame
    new GlobalView(globalManager)

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

      // Create the FoodManager actor (spawn automatic food)
      val foodManager = context.spawn(
        FoodManagerActor(gameWorldSingleton, width, height),
        "food-manager"
      )

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