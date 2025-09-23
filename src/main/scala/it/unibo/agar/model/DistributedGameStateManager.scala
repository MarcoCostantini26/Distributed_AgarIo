package it.unibo.agar.model

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Distributed implementation of GameStateManager that communicates
 * with the GameWorldActor using Akka messaging.
 *
 * This class maintains the same interface as MockGameStateManager
 * but delegates all operations to the distributed GameWorldActor.
 */
class DistributedGameStateManager(
    gameWorldActor: ActorRef[GameMessage]
)(implicit system: ActorSystem[_]) extends GameStateManager:

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler: akka.actor.typed.Scheduler = system.scheduler
  implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

  /**
   * Get the current world state from the GameWorldActor
   */
  def getWorld: World =
    try
      val future = gameWorldActor.ask(GetWorld.apply)
      Await.result(future, timeout.duration)
    catch
      case _: Exception =>
        // Return empty world if communication fails
        World(1000, 1000, Seq.empty, Seq.empty)

  /**
   * Send a movement command to the GameWorldActor
   */
  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit =
    gameWorldActor ! MovePlayer(id, dx, dy)

  /**
   * Send a tick command to the GameWorldActor
   * Note: In the distributed version, this might be called by a timer
   * in the GameWorldActor itself rather than externally
   */
  def tick(): Unit =
    gameWorldActor ! Tick