package it.unibo.agar.model

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicReference

/**
 * Distributed implementation of game state manager that communicates
 * with the GameWorldActor using Akka messaging.
 *
 * This version uses a cached world state updated asynchronously
 * to avoid blocking the UI thread.
 */
class DistributedGameStateManager(
    gameWorldActor: ActorRef[GameMessage]
)(implicit system: ActorSystem[_]):

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler: akka.actor.typed.Scheduler = system.scheduler
  implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

  // Cached world state - updated asynchronously
  private val cachedWorld = new AtomicReference[World](
    World(1000, 1000, Seq.empty, Seq.empty)
  )

  // Start background polling for world state
  // Note: This is a fallback mechanism. In production, PlayerActor
  // would receive WorldStateUpdate messages and update this cache
  private val cancellable = system.scheduler.scheduleAtFixedRate(
    initialDelay = 0.millis,
    interval = 100.millis
  ) { () =>
    gameWorldActor.ask(GetWorld.apply).foreach { world =>
      cachedWorld.set(world)
    }
  }

  /**
   * Get the current world state (non-blocking, returns cached value)
   */
  def getWorld: World =
    cachedWorld.get()

  /**
   * Update the cached world state (called by PlayerActor when receiving updates)
   */
  def updateWorld(world: World): Unit =
    cachedWorld.set(world)

  /**
   * Send a movement command to the GameWorldActor
   */
  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit =
    gameWorldActor ! MovePlayer(id, dx, dy)

  /**
   * Send a tick command to the GameWorldActor
   * Note: In the distributed version, this is called by a timer
   * in the main application
   */
  def tick(): Unit =
    gameWorldActor ! Tick

  /**
   * Cleanup resources
   */
  def shutdown(): Unit =
    cancellable.cancel()