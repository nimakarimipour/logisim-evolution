/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit.appear;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.shapes.Text;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;

public class DefaultCustomAppearance {

  private static final int OFFS = 50;

  public static List<CanvasObject> build(Collection<Instance> pins) {
    final java.util.HashMap<com.cburch.logisim.data.Direction,java.util.List<com.cburch.logisim.instance.Instance>> edge = new HashMap<Direction, List<Instance>>();
    edge.put(Direction.EAST, new ArrayList<>());
    edge.put(Direction.WEST, new ArrayList<>());
    int maxLeftLabelLength = 0;
    int maxRightLabelLength = 0;

    if (!pins.isEmpty()) {
      for (final com.cburch.logisim.instance.Instance pin : pins) {
        Direction pinEdge;
        final com.cburch.draw.shapes.Text label = new Text(0, 0, pin.getAttributeValue(StdAttr.LABEL));
        final int labelWidth = label.getText().length() * DrawAttr.FIXED_FONT_CHAR_WIDTH;
        if (pin.getAttributeValue(Pin.ATTR_TYPE)) {
          pinEdge = Direction.EAST;
          if (labelWidth > maxRightLabelLength) maxRightLabelLength = labelWidth;
        } else {
          pinEdge = Direction.WEST;
          if (labelWidth > maxLeftLabelLength) maxLeftLabelLength = labelWidth;
        }
        final java.util.List<com.cburch.logisim.instance.Instance> e = edge.get(pinEdge);
        e.add(pin);
      }
    }

    for (final java.util.Map.Entry<com.cburch.logisim.data.Direction,java.util.List<com.cburch.logisim.instance.Instance>> entry : edge.entrySet()) {
      DefaultAppearance.sortPinList(entry.getValue(), entry.getKey());
    }

    final int numEast = edge.get(Direction.EAST).size();
    final int numWest = edge.get(Direction.WEST).size();
    final int maxVert = Math.max(numEast, numWest);

    final int textWidth = 25 * DrawAttr.FIXED_FONT_CHAR_WIDTH;
    final int width = (textWidth / 10) * 10 + 20;
    final int height = (maxVert > 0) ? maxVert * 10 + 10 : 10;

    // compute position of anchor relative to top left corner of box
    int ax = 0;
    int ay = 0;
    if (numEast > 0) { // anchor is on east side
      ax = width;
      ay = 10;
    } else if (numWest > 0) { // anchor is on west side
      ax = 0;
      ay = 10;
    }

    // place rectangle so anchor is on the grid
    final int rx = OFFS + (9 - (ax + 9) % 10);
    final int ry = OFFS + (9 - (ay + 9) % 10);

    final java.util.ArrayList<com.cburch.draw.model.CanvasObject> ret = new ArrayList<CanvasObject>();
    placePins(ret, edge.get(Direction.WEST), rx, ry + 10, 0, 10);
    placePins(ret, edge.get(Direction.EAST), rx + width, ry + 10, 0, 10);
    final com.cburch.draw.shapes.Rectangle rect = new Rectangle(rx, ry, width, height);
    rect.setValue(DrawAttr.STROKE_WIDTH, 1);
    ret.add(rect);
    ret.add(new AppearanceAnchor(Location.create(rx + ax, ry + ay, true)));
    return ret;
  }

  private static void placePins(
      List<CanvasObject> dest, List<Instance> pins, int x, int y, int dX, int dY) {
    for (final com.cburch.logisim.instance.Instance pin : pins) {
      dest.add(new AppearancePort(Location.create(x, y, true), pin));
      x += dX;
      y += dY;
    }
  }
}
