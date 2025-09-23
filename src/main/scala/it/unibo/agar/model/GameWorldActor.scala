package it.unibo.agar.model

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import scala.util.Random

/**
 * The main actor that manages the distributed game world state.
 * This actor will run as a Cluster Singleton to ensure consistency.
 */
object GameWorldActor:

  private val GAME_END_MASS = 1000.0
  private val SPEED = 10.0

  def apply(initialWorld: World): Behavior[GameMessage] =
    gameWorld(initialWorld, Map.empty, Set.empty)

  private def gameWorld(
      world: World,
      directions: Map[String, (Double, Double)], // Player movement directions
      registeredPlayers: Set[ActorRef[GameMessage]] // Registered player nodes for broadcasting
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
          // Note: Broadcasting removed for simplicity, will add back later
          gameWorld(updatedWorld, directions, registeredPlayers)

        case PlayerJoined(id, x, y, mass) =>
          val newPlayer = Player(id, x, y, mass)
          val updatedWorld = world.copy(players = world.players :+ newPlayer)
          context.log.info(s"Player $id joined at ($x, $y) with mass $mass")
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

        case CheckGameEnd(replyTo) =>
          world.players.find(_.mass >= GAME_END_MASS) match
            case Some(winner) =>
              replyTo ! GameEnded(winner.id, winner.mass)
            case None =>
              replyTo ! GameContinues
          Behaviors.same

        case RegisterPlayer(playerId, playerNode) =>
          context.log.info(s"Registered player node for $playerId")
          gameWorld(world, directions, registeredPlayers + playerNode)

        case UnregisterPlayer(playerId) =>
          // Note: We can't easily remove specific player nodes without additional tracking
          // For now, we'll handle this in a future iteration
          context.log.info(s"Unregistered player node for $playerId")
          Behaviors.same

        case WorldStateUpdate(_) =>
          // This actor is the source of truth, ignore updates
          Behaviors.same
    }

  /**
   * Process a single game tick - move players and handle eating logic
   */
  private def processTick(world: World, directions: Map[String, (Double, Double)]): World =
    directions.foldLeft(world) { case (currentWorld, (playerId, (dx, dy))) =>
      currentWorld.playerById(playerId) match
        case Some(player) =>
          val movedPlayer = updatePlayerPosition(player, dx, dy, currentWorld.width, currentWorld.height)
          updateWorldAfterMovement(movedPlayer, currentWorld)
        case None =>
          // Player not found, ignore movement
          currentWorld
    }

  /**
   * Update player position with boundary constraints
   */
  private def updatePlayerPosition(player: Player, dx: Double, dy: Double, worldWidth: Int, worldHeight: Int): Player =
    val newX = (player.x + dx * SPEED).max(0).min(worldWidth)
    val newY = (player.y + dy * SPEED).max(0).min(worldHeight)
    player.copy(x = newX, y = newY)

  /**
   * Update world after a player movement - handle eating logic
   */
  private def updateWorldAfterMovement(player: Player, world: World): World =
    // Find food that can be eaten
    val foodEaten = world.foods.filter(food => EatingManager.canEatFood(player, food))
    val playerAfterEatingFood = foodEaten.foldLeft(player)((p, food) => p.grow(food))

    // Find players that can be eaten
    val playersEaten = world
      .playersExcludingSelf(player)
      .filter(otherPlayer => EatingManager.canEatPlayer(playerAfterEatingFood, otherPlayer))
    val playerAfterEatingPlayers = playersEaten.foldLeft(playerAfterEatingFood)((p, other) => p.grow(other))

    // Update world with new state
    world
      .updatePlayer(playerAfterEatingPlayers)
      .removePlayers(playersEaten)
      .removeFoods(foodEaten)

  /**
   * Generate random food for the world
   */
  def generateRandomFood(worldWidth: Int, worldHeight: Int, count: Int = 1): Seq[Food] =
    (1 to count).map { i =>
      Food(
        id = s"f_${System.currentTimeMillis()}_$i",
        x = Random.nextInt(worldWidth),
        y = Random.nextInt(worldHeight),
        mass = 100.0
      )
    }