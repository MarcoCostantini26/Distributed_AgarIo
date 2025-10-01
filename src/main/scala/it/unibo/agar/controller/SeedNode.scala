package it.unibo.agar.controller

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
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

  def main(args: Array[String]): Unit =
    val width = 1000
    val height = 1000
    val numPlayers = 4
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

      // Initialize world
      val initialPlayers = GameInitializer.initialPlayers(numPlayers, width, height)
      val initialFoods = GameInitializer.initialFoods(numFoods, width, height)
      val initialWorld = World(width, height, initialPlayers, initialFoods)

      // Create GameWorld singleton
      val gameWorld = context.spawn(
        GameWorldSingleton(initialWorld),
        "game-world-manager"
      )

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

      Behaviors.receiveMessage { msg =>
        context.log.info(s"Seed node received: $msg")
        Behaviors.same
      }
    }
