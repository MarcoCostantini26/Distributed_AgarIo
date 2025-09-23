package it.unibo.agar.view

import it.unibo.agar.model.GameStateManager

import java.awt.Graphics2D
import scala.swing.*

class LocalView(manager: GameStateManager, playerId: String) extends MainFrame:

  title = s"Agar.io - Local View ($playerId)"
  preferredSize = new Dimension(400, 400)

  contents = new Panel:
    listenTo(keys, mouse.moves)
    focusable = true
    requestFocusInWindow()

    override def paintComponent(g: Graphics2D): Unit =
      val world = manager.getWorld
      val playerOpt = world.players.find(_.id == playerId)
      val (offsetX, offsetY) = playerOpt
        .map(p => (p.x - size.width / 2.0, p.y - size.height / 2.0))
        .getOrElse((0.0, 0.0))
      AgarViewUtils.drawWorld(g, world, offsetX, offsetY)

    reactions += { case e: event.MouseMoved =>
      val mousePos = e.point
      val playerOpt = manager.getWorld.players.find(_.id == playerId)
      playerOpt.foreach: player =>
        val dx = (mousePos.x - size.width / 2) * 0.01
        val dy = (mousePos.y - size.height / 2) * 0.01
        manager.movePlayerDirection(playerId, dx, dy)
      repaint()
    }
