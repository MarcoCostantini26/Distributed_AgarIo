package it.unibo.agar.controller

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.cluster.typed.{Cluster, SelfUp, Subscribe}
import it.unibo.agar.model.*
import it.unibo.agar.view.GlobalView
import scala.swing.Swing.onEDT
import java.awt.Window
import java.util.{Timer, TimerTask}

/**
 * Seed node that hosts the GameWorld singleton and GlobalView.
 * This should be started first before any PlayerNode.
 *
 * Usage:
 *   sbt "runMain it.unibo.agar.controller.SeedNode"
 */
object SeedNode:

  sealed trait SeedCommand
  case object Initialize extends SeedCommand
  case object ClusterReady extends SeedCommand

  val GameWorldKey: ServiceKey[GameMessage] = ServiceKey[GameMessage]("game-world-manager")

  def main(args: Array[String]): Unit =
    val width = 1000
    val height = 1000
    val numPlayers = 0  // ← Cambiato da 4 a 0: nessun player dummy
    val numFoods = 100

    println("=" * 60)
    println("🌱 Starting SEED NODE (GameWorld + GlobalView)")
    println("=" * 60)

    // Create actor system
    val system: ActorSystem[SeedCommand] = ActorSystem(
      seedBehavior(width, height, numPlayers, numFoods),
      "ClusterSystem"
    )

    println("\n✅ Seed node started!")
    println("📡 Waiting for player nodes to connect...")
    println("   Run: sbt \"runMain it.unibo.agar.controller.PlayerNode <playerName>\"")
    println()

    // Keep application running
    scala.io.StdIn.readLine("Press ENTER to shutdown...\n")
    system.terminate()

  def seedBehavior(width: Int, height: Int, numPlayers: Int, numFoods: Int): Behavior[SeedCommand] =
    Behaviors.setup { context =>
      context.log.info("🌱 Initializing Seed Node")

      // Subscribe to cluster events
      val cluster = Cluster(context.system)
      val clusterEventAdapter = context.messageAdapter[SelfUp](_ => ClusterReady)
      cluster.subscriptions ! Subscribe(clusterEventAdapter, classOf[SelfUp])

      // Initialize world
      val initialPlayers = GameInitializer.initialPlayers(numPlayers, width, height)
      val initialFoods = GameInitializer.initialFoods(numFoods, width, height)
      val initialWorld = World(width, height, initialPlayers, initialFoods)

      // Create GameWorld singleton
      val gameWorld = context.spawn(
        GameWorldSingleton(initialWorld),
        "game-world-manager"
      )

      // Wait for cluster to be ready before registering
      waitingForCluster(gameWorld, width, height)
    }

  private def waitingForCluster(
      gameWorld: akka.actor.typed.ActorRef[GameMessage],
      width: Int,
      height: Int
  ): Behavior[SeedCommand] =
    Behaviors.receive { (context, message) =>
      message match
        case ClusterReady =>
          context.log.info("✅ Cluster is ready, registering GameWorld")

          // ⭐ IMPORTANT: Register GameWorld in the Receptionist so PlayerNodes can find it
          context.system.receptionist ! Receptionist.Register(GameWorldKey, gameWorld)
          context.log.info("📡 GameWorld registered in Receptionist")

          // Create FoodManager
          val foodManager = context.spawn(
            FoodManagerActor(gameWorld, width, height),
            "food-manager"
          )

          // Create GlobalView manager
          val globalManager = new DistributedGameStateManager(gameWorld)(context.system)

          // Set up game tick timer
          val timer = new Timer()
          val task: TimerTask = new TimerTask:
            override def run(): Unit =
              gameWorld ! Tick
              onEDT(Window.getWindows.foreach(_.repaint()))

          timer.scheduleAtFixedRate(task, 0, 30) // 30ms tick

          // Open GlobalView on EDT
          onEDT {
            val globalView = new GlobalView(globalManager)
            globalView.visible = true
          }

          context.log.info("✅ Seed Node initialized successfully")

          running()

        case other =>
          context.log.debug(s"Waiting for cluster, ignoring: $other")
          Behaviors.same
    }

  private def running(): Behavior[SeedCommand] =
    Behaviors.receiveMessage { msg =>
      Behaviors.same
    }
