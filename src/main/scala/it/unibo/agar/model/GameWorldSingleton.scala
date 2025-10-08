package it.unibo.agar.model

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}

/**
 * Manages the GameWorldActor as a Cluster Singleton.
 * This ensures only one instance of GameWorldActor runs across the entire cluster.
 */
object GameWorldSingleton:


  def apply(initialWorld: World): Behavior[GameMessage] =
    Behaviors.setup { context =>
      val clusterSingleton = ClusterSingleton(context.system)
      val gameWorldSingletonRef = clusterSingleton.init(
        SingletonActor(
          Behaviors.supervise(GameWorldActor(initialWorld))
            .onFailure(akka.actor.typed.SupervisorStrategy.restart),
          "game-world-singleton"
        )
      )
      running(gameWorldSingletonRef)
    }

  private def running(gameWorldRef: ActorRef[GameMessage]): Behavior[GameMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case msg =>
          gameWorldRef ! msg
          Behaviors.same
    }