package it.unibo.agar.model

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object FoodManagerActor:
  sealed trait Command
  case object GenerateFood extends Command

  def apply(gameWorld: ActorRef[GameMessage], worldWidth: Int, worldHeight: Int): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(GenerateFood, 2.seconds)
      Behaviors.receive { (context, message) =>
        message match
          case GenerateFood =>
            val foods = GameWorldActor.generateRandomFood(worldWidth, worldHeight, count = 1)
            foods.foreach(food => gameWorld ! SpawnFood(food))
            Behaviors.same
      }
    }