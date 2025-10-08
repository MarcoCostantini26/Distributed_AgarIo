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

object SeedNode:

  sealed trait SeedCommand
  case object ClusterReady extends SeedCommand

  val GameWorldKey: ServiceKey[GameMessage] = ServiceKey[GameMessage]("game-world-manager")

  def main(args: Array[String]): Unit =
    val width = 1000
    val height = 1000
    val numPlayers = 0 
    val numFoods = 100

    println("=" * 60)
    println("🌱 Starting SEED NODE (GameWorld + GlobalView)")
    println("=" * 60)

    val system: ActorSystem[SeedCommand] = ActorSystem(
      seedBehavior(width, height, numPlayers, numFoods),
      "ClusterSystem"
    )
    println("Waiting for player nodes to connect...")
    scala.io.StdIn.readLine("Press ENTER to shutdown...\n")
    system.terminate()

  def seedBehavior(width: Int, height: Int, numPlayers: Int, numFoods: Int): Behavior[SeedCommand] =
    Behaviors.setup { context =>
      context.log.info("Initializing Seed Node")

      val cluster = Cluster(context.system)
      val clusterEventAdapter = context.messageAdapter[SelfUp](_ => ClusterReady)
      cluster.subscriptions ! Subscribe(clusterEventAdapter, classOf[SelfUp])

      val initialPlayers = GameInitializer.initialPlayers(numPlayers, width, height)
      val initialFoods = GameInitializer.initialFoods(numFoods, width, height)
      val initialWorld = World(width, height, initialPlayers, initialFoods)

      val gameWorld = context.spawn(
        GameWorldSingleton(initialWorld),
        "game-world-manager"
      )
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
          context.log.info("Cluster is ready, registering GameWorld")
          context.system.receptionist ! Receptionist.Register(GameWorldKey, gameWorld)

          val foodManager = context.spawn(
            FoodManagerActor(gameWorld, width, height),
            "food-manager"
          )

          val globalManager = new DistributedGameStateManager(gameWorld)(context.system)

          val timer = new Timer()
          val task: TimerTask = new TimerTask:
            override def run(): Unit =
              gameWorld ! Tick
              onEDT(Window.getWindows.foreach(_.repaint()))
          timer.scheduleAtFixedRate(task, 0, 30) 

          onEDT {
            val globalView = new GlobalView(globalManager)
            globalView.visible = true
          }
          running()

        case other =>
          context.log.debug(s"Waiting for cluster, ignoring: $other")
          Behaviors.same
    }

  private def running(): Behavior[SeedCommand] =
    Behaviors.receiveMessage { msg =>
      Behaviors.same
    }
