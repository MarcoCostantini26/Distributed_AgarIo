package it.unibo.agar.model

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}

/**
 * Manages the GameWorldActor as a Cluster Singleton.
 * This ensures only one instance of GameWorldActor runs across the entire cluster.
 */
object GameWorldSingleton:

  /**
   * Message to get reference to the GameWorldActor singleton
   */
  case class GetGameWorldActor(replyTo: ActorRef[ActorRef[GameMessage]])

  /**
   * Creates and manages the GameWorldActor singleton
   */
  def apply(initialWorld: World): Behavior[GameMessage] =
    Behaviors.setup { context =>
      val clusterSingleton = ClusterSingleton(context.system)

      // Start the GameWorldActor as a singleton
      val gameWorldSingletonRef = clusterSingleton.init(
        SingletonActor(
          Behaviors.supervise(GameWorldActor(initialWorld))
            .onFailure(akka.actor.typed.SupervisorStrategy.restart),
          "game-world-singleton"
        )
      )

      context.log.info("GameWorldSingleton started")

      running(gameWorldSingletonRef)
    }

  /**
   * Behavior when singleton is running
   */
  private def running(gameWorldRef: ActorRef[GameMessage]): Behavior[GameMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case GetGameWorldActor(replyTo) =>
          replyTo ! gameWorldRef
          Behaviors.same

        case msg =>
          // Forward all other messages to the actual GameWorldActor
          gameWorldRef ! msg
          Behaviors.same
    }

  /**
   * Creates a proxy to communicate with the GameWorldActor singleton
   * from any node in the cluster
   */
  def createProxy(): Behavior[GameMessage] =
    Behaviors.setup { context =>
      val clusterSingleton = ClusterSingleton(context.system)

      val gameWorldProxy = clusterSingleton.init(
        SingletonActor(
          Behaviors.empty[GameMessage], // We just want the proxy
          "game-world-singleton"
        )
      )

      context.log.info("GameWorldSingleton proxy created")

      // Forward all messages to the singleton
      Behaviors.receiveMessage { message =>
        gameWorldProxy ! message
        Behaviors.same
      }
    }