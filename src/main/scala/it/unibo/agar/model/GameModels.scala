package it.unibo.agar.model

sealed trait Entity:

  def id: String
  def mass: Double
  def x: Double
  def y: Double
  def radius: Double = math.sqrt(mass / math.Pi)

  def distanceTo(other: Entity): Double =
    val dx = x - other.x
    val dy = y - other.y
    math.hypot(dx, dy)

case class Player(id: String, x: Double, y: Double, mass: Double) extends Entity:

  def grow(entity: Entity): Player =
    copy(mass = mass + entity.mass)

case class Food(id: String, x: Double, y: Double, mass: Double = 100.0) extends Entity

case class World(
    width: Int,
    height: Int,
    players: Seq[Player],
    foods: Seq[Food]
):

  def playersExcludingSelf(player: Player): Seq[Player] =
    players.filterNot(_.id == player.id)

  def playerById(id: String): Option[Player] =
    players.find(_.id == id)

  def updatePlayer(player: Player): World =
    copy(players = players.map(p => if (p.id == player.id) player else p))

  def removePlayers(ids: Seq[Player]): World =
    copy(players = players.filterNot(p => ids.map(_.id).contains(p.id)))

  def removeFoods(ids: Seq[Food]): World =
    copy(foods = foods.filterNot(f => ids.contains(f)))
