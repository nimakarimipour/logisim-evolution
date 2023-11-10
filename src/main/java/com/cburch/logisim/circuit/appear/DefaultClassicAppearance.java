/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit.appear;

import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.Curve;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class DefaultClassicAppearance {

  private static final int OFFS = 50;

  public static List<CanvasObject> build(Collection<Instance> pins) {
    final java.util.HashMap<com.cburch.logisim.data.Direction,java.util.List<com.cburch.logisim.instance.Instance>> edge = new HashMap<Direction, List<Instance>>();
    edge.put(Direction.NORTH, new ArrayList<>());
    edge.put(Direction.SOUTH, new ArrayList<>());
    edge.put(Direction.EAST, new ArrayList<>());
    edge.put(Direction.WEST, new ArrayList<>());
    for (final com.cburch.logisim.instance.Instance pin : pins) {
      final com.cburch.logisim.data.Direction pinFacing = pin.getAttributeValue(StdAttr.FACING);
      final com.cburch.logisim.data.Direction pinEdge = pinFacing.reverse();
      final java.util.List<com.cburch.logisim.instance.Instance> e = edge.get(pinEdge);
      e.add(pin);
    }

    for (final java.util.Map.Entry<com.cburch.logisim.data.Direction,java.util.List<com.cburch.logisim.instance.Instance>> entry : edge.entrySet()) {
      DefaultAppearance.sortPinList(entry.getValue(), entry.getKey());
    }

    final int numNorth = edge.get(Direction.NORTH).size();
    final int numSouth = edge.get(Direction.SOUTH).size();
    final int numEast = edge.get(Direction.EAST).size();
    final int numWest = edge.get(Direction.WEST).size();
    final int maxVert = Math.max(numNorth, numSouth);
    final int maxHorz = Math.max(numEast, numWest);

    final int offsNorth = computeOffset(numNorth, numSouth, maxHorz);
    final int offsSouth = computeOffset(numSouth, numNorth, maxHorz);
    final int offsEast = computeOffset(numEast, numWest, maxVert);
    final int offsWest = computeOffset(numWest, numEast, maxVert);

    final int width = computeDimension(maxVert, maxHorz);
    final int height = computeDimension(maxHorz, maxVert);

    // compute position of anchor relative to top left corner of box
    int ax;
    int ay;
    if (numEast > 0) { // anchor is on east side
      ax = width;
      ay = offsEast;
    } else if (numNorth > 0) { // anchor is on north side
      ax = offsNorth;
      ay = 0;
    } else if (numWest > 0) { // anchor is on west side
      ax = 0;
      ay = offsWest;
    } else if (numSouth > 0) { // anchor is on south side
      ax = offsSouth;
      ay = height;
    } else { // anchor is top left corner
      ax = 0;
      ay = 0;
    }

    // place rectangle so anchor is on the grid
    final int rX = Math.round((OFFS + ax) / 10) * 10;
    final int rY = Math.round((OFFS + ay) / 10) * 10;

    final com.cburch.logisim.data.Location e0 = Location.create(rX + (width - 8) / 2, rY + 1, false);
    final com.cburch.logisim.data.Location e1 = Location.create(rX + (width + 8) / 2, rY + 1, false);
    final com.cburch.logisim.data.Location ct = Location.create(rX + width / 2, rY + 11, false);
    final com.cburch.draw.shapes.Curve notch = new Curve(e0, e1, ct);
    notch.setValue(DrawAttr.STROKE_WIDTH, 2);
    notch.setValue(DrawAttr.STROKE_COLOR, Color.GRAY);
    final com.cburch.draw.shapes.Rectangle rect = new Rectangle(rX, rY, width, height);
    rect.setValue(DrawAttr.STROKE_WIDTH, 2);

    final java.util.ArrayList<com.cburch.draw.model.CanvasObject> ret = new ArrayList<CanvasObject>();
    ret.add(notch);
    ret.add(rect);
    placePins(ret, edge.get(Direction.WEST), rX, rY + offsWest, 0, 10);
    placePins(ret, edge.get(Direction.EAST), rX + width, rY + offsEast, 0, 10);
    placePins(ret, edge.get(Direction.NORTH), rX + offsNorth, rY, 10, 0);
    placePins(ret, edge.get(Direction.SOUTH), rX + offsSouth, rY + height, 10, 0);
    ret.add(new AppearanceAnchor(Location.create(rX + ax, rY + ay, true)));
    return ret;
  }

  private static int computeDimension(int maxThis, int maxOthers) {
    if (maxThis < 3) {
      return 30;
    } else if (maxOthers == 0) {
      return 10 * maxThis;
    } else {
      return 10 * maxThis + 10;
    }
  }

  private static int computeOffset(int numFacing, int numOpposite, int maxOthers) {
    final int maxThis = Math.max(numFacing, numOpposite);
    int maxOffs = switch (maxThis) {
      case 0, 1 -> (maxOthers == 0 ? 15 : 10);
      case 2 -> 10;
      default -> (maxOthers == 0 ? 5 : 10);
    };
    return maxOffs + 10 * ((maxThis - numFacing) / 2);
  }

  private static void placePins(List<CanvasObject> dest, List<Instance> pins, int x, int y, int dx, int dy) {
    for (final com.cburch.logisim.instance.Instance pin : pins) {
      dest.add(new AppearancePort(Location.create(x, y, true), pin));
      x += dx;
      y += dy;
    }
  }
}
