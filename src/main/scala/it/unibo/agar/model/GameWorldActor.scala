package it.unibo.agar.model

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import scala.util.Random

object GameWorldActor:

  private val GAME_END_MASS = 1000.0
  private val SPEED = 10.0

  def apply(initialWorld: World): Behavior[GameMessage] =
    gameWorld(initialWorld, Map.empty, Set.empty)

  private def gameWorld(
      world: World,
      directions: Map[String, (Double, Double)], 
      registeredPlayers: Set[ActorRef[GameMessage]] 
  ): Behavior[GameMessage] =
    Behaviors.receive { (context, message) =>
      message match
        case MovePlayer(id, dx, dy) =>
          val newDirections = directions.updated(id, (dx, dy))
          gameWorld(world, newDirections, registeredPlayers)

        case GetWorld(replyTo) =>
          replyTo ! world
          Behaviors.same

        case Tick =>
          val updatedWorld = processTick(world, directions)
          registeredPlayers.foreach { playerRef =>
            playerRef ! WorldStateUpdate(updatedWorld)
          }
          gameWorld(updatedWorld, directions, registeredPlayers)

        case PlayerJoined(id, x, y, mass) =>
          val newPlayer = Player(id, x, y, mass)
          val updatedWorld = world.copy(players = world.players :+ newPlayer)
          context.log.info(s"Player $id joined at ($x, $y) with mass $mass. Total players: ${updatedWorld.players.size}")
          registeredPlayers.foreach { playerRef =>
            playerRef ! WorldStateUpdate(updatedWorld)
          }
          gameWorld(updatedWorld, directions, registeredPlayers)

        case PlayerLeft(id) =>
          val updatedWorld = world.copy(players = world.players.filterNot(_.id == id))
          val newDirections = directions.removed(id)
          context.log.info(s"Player $id left the game")
          gameWorld(updatedWorld, newDirections, registeredPlayers)

        case SpawnFood(food) =>
          val updatedWorld = world.copy(foods = world.foods :+ food)
          gameWorld(updatedWorld, directions, registeredPlayers)

        case RemoveFood(foodIds) =>
          val updatedWorld = world.copy(foods = world.foods.filterNot(f => foodIds.contains(f.id)))
          gameWorld(updatedWorld, directions, registeredPlayers)

        case RegisterPlayer(playerId, playerNode) =>
          context.log.info(s"Registered player node for $playerId. Total registered: ${registeredPlayers.size + 1}")
          gameWorld(world, directions, registeredPlayers + playerNode)

        case UnregisterPlayer(playerId) =>
          context.log.info(s"Unregistered player node for $playerId")
          Behaviors.same

        case WorldStateUpdate(_) =>
          Behaviors.same
    }

  private def processTick(world: World, directions: Map[String, (Double, Double)]): World =
    directions.foldLeft(world) { case (currentWorld, (playerId, (dx, dy))) =>
      currentWorld.playerById(playerId) match
        case Some(player) =>
          val movedPlayer = updatePlayerPosition(player, dx, dy, currentWorld.width, currentWorld.height)
          updateWorldAfterMovement(movedPlayer, currentWorld)
        case None =>
          currentWorld
    }

  private def updatePlayerPosition(player: Player, dx: Double, dy: Double, worldWidth: Int, worldHeight: Int): Player =
    val newX = (player.x + dx * SPEED).max(0).min(worldWidth)
    val newY = (player.y + dy * SPEED).max(0).min(worldHeight)
    player.copy(x = newX, y = newY)

  private def updateWorldAfterMovement(player: Player, world: World): World =
    val foodEaten = world.foods.filter(food => EatingManager.canEatFood(player, food))
    val playerAfterEatingFood = foodEaten.foldLeft(player)((p, food) => p.grow(food))

    val playersEaten = world
      .playersExcludingSelf(player)
      .filter(otherPlayer => EatingManager.canEatPlayer(playerAfterEatingFood, otherPlayer))
    val playerAfterEatingPlayers = playersEaten.foldLeft(playerAfterEatingFood)((p, other) => p.grow(other))

    world
      .updatePlayer(playerAfterEatingPlayers)
      .removePlayers(playersEaten)
      .removeFoods(foodEaten)


  def generateRandomFood(worldWidth: Int, worldHeight: Int, count: Int = 1): Seq[Food] =
    (1 to count).map { i =>
      Food(
        id = s"f_${System.currentTimeMillis()}_$i",
        x = Random.nextInt(worldWidth),
        y = Random.nextInt(worldHeight),
        mass = 100.0
      )
    }