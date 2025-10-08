package it.unibo.agar.model

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicReference

class DistributedGameStateManager(
    gameWorldActor: ActorRef[GameMessage]
)(implicit system: ActorSystem[_]):

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler: akka.actor.typed.Scheduler = system.scheduler
  implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

  private val cachedWorld = new AtomicReference[World](
    World(1000, 1000, Seq.empty, Seq.empty)
  )

  private val cancellable = system.scheduler.scheduleAtFixedRate(
    initialDelay = 0.millis,
    interval = 100.millis
  ) { () =>
    gameWorldActor.ask(GetWorld.apply).foreach { world =>
      cachedWorld.set(world)
    }
  }

  def getWorld: World =
    cachedWorld.get()

  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit =
    gameWorldActor ! MovePlayer(id, dx, dy)