/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit.appear;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import java.awt.event.MouseEvent;

public abstract class DynamicElementWithPoker extends DynamicElement {

  private boolean isPressed;
  private Location anchorPosition;

  public DynamicElementWithPoker(Path p, Bounds b) {
    super(p, b);
    isPressed = false;
  }

  public void mousePressed(InstanceState state, MouseEvent e) {
    isPressed = true;
  }

  public void mouseReleased(InstanceState state, MouseEvent e) {
    if (isPressed) performClickAction(state, e);
    isPressed = false;
  }

  public void setAnchor(Location loc) {
    anchorPosition = loc;
  }

  public Bounds getScreenBounds(InstanceState state) {
    final com.cburch.logisim.data.Direction dir = state.getAttributeValue(StdAttr.FACING);
    final com.cburch.logisim.data.Location loc = state.getInstance().getLocation();
    if (dir == Direction.EAST) {
      final int posX = bounds.getX() - anchorPosition.getX() + loc.getX();
      final int posY = bounds.getY() - anchorPosition.getY() + loc.getY();
      return Bounds.create(posX, posY, bounds.getWidth(), bounds.getHeight());
    }
    if (dir == Direction.WEST) {
      final int posX = anchorPosition.getX() - bounds.getX() - bounds.getWidth() + loc.getX();
      final int posY = anchorPosition.getY() - bounds.getY() - bounds.getHeight() + loc.getY();
      return Bounds.create(posX, posY, bounds.getWidth(), bounds.getHeight());
    }
    if (dir == Direction.NORTH) {
      final int posX = bounds.getY() - anchorPosition.getY() + loc.getX();
      final int posY = bounds.getX() - anchorPosition.getX() - bounds.getWidth() + loc.getY();
      return Bounds.create(posX, posY, bounds.getHeight(), bounds.getWidth());
    }
    final int posX = anchorPosition.getY() - bounds.getY() - bounds.getHeight() + loc.getX();
    final int posY = bounds.getX() - anchorPosition.getX() + loc.getY();
    return Bounds.create(posX, posY, bounds.getHeight(), bounds.getWidth());
  }

  public Boolean mouseInside(InstanceState state, MouseEvent e) {
    final com.cburch.logisim.data.Bounds b = getScreenBounds(state);
    return b.contains(e.getX(), e.getY());
  }

  public abstract void performClickAction(InstanceState state, MouseEvent e);
}
