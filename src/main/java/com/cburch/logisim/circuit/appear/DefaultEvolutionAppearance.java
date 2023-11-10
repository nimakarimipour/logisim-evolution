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
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.shapes.Text;
import com.cburch.draw.util.EditableLabel;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class DefaultEvolutionAppearance {

  private static final int OFFS = 50;

  public static List<CanvasObject> build(
      Collection<Instance> pins, String circuitName, boolean fixedSize) {
    final java.util.HashMap<com.cburch.logisim.data.Direction,java.util.List<com.cburch.logisim.instance.Instance>> edge = new HashMap<Direction, List<Instance>>();
    edge.put(Direction.EAST, new ArrayList<>());
    edge.put(Direction.WEST, new ArrayList<>());
    int maxLeftLabelLength = 0;
    int maxRightLabelLength = 0;
    final int TitleWidth =
        (circuitName == null)
            ? 14 * DrawAttr.FIXED_FONT_CHAR_WIDTH
            : circuitName.length() * DrawAttr.FIXED_FONT_CHAR_WIDTH;

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

    final int dy = ((DrawAttr.FIXED_FONT_HEIGHT + (DrawAttr.FIXED_FONT_HEIGHT >> 2) + 5) / 10) * 10;
    final int textWidth =
        (fixedSize)
            ? 25 * DrawAttr.FIXED_FONT_CHAR_WIDTH
            : Math.max((maxLeftLabelLength + maxRightLabelLength + 35), (TitleWidth + 15));
    final int thight = ((DrawAttr.FIXED_FONT_HEIGHT + 10) / 10) * 10;
    final int width = (textWidth / 10) * 10 + 20;
    final int height = (maxVert > 0) ? maxVert * dy + thight : 10 + thight;
    final int sdy = (DrawAttr.FIXED_FONT_ASCENT - DrawAttr.FIXED_FONT_DESCENT) >> 1;

    // compute position of anchor relative to top left corner of box
    int ax;
    int ay;
    if (numEast > 0) { // anchor is on east side
      ax = width;
      ay = 10;
    } else if (numWest > 0) { // anchor is on west side
      ax = 0;
      ay = 10;
    } else { // anchor is top left corner
      ax = 0;
      ay = 0;
    }

    // place rectangle so anchor is on the grid
    final int rx = OFFS + (9 - (ax + 9) % 10);
    final int ry = OFFS + (9 - (ay + 9) % 10);

    final java.util.ArrayList<com.cburch.draw.model.CanvasObject> ret = new ArrayList<CanvasObject>();
    placePins(ret, edge.get(Direction.WEST), rx, ry + 10, 0, dy, true, sdy, fixedSize);
    placePins(ret, edge.get(Direction.EAST), rx + width, ry + 10, 0, dy, false, sdy, fixedSize);
    com.cburch.draw.shapes.Rectangle rect = new Rectangle(rx + 10, ry + height - thight, width - 20, thight);
    rect.setValue(DrawAttr.STROKE_WIDTH, 1);
    rect.setValue(DrawAttr.PAINT_TYPE, DrawAttr.PAINT_FILL);
    rect.setValue(DrawAttr.FILL_COLOR, Color.BLACK);
    ret.add(rect);
    rect = new Rectangle(rx + 10, ry, width - 20, height);
    rect.setValue(DrawAttr.STROKE_WIDTH, 2);
    ret.add(rect);
    java.lang.String label = circuitName == null ? "VHDL Component" : circuitName;
    final int maxLength = 23;
    final java.lang.String ellipsis = "...";
    if (fixedSize && label.length() > maxLength) {
      label = label.substring(0, maxLength - ellipsis.length()).concat(ellipsis);
    }
    final com.cburch.draw.shapes.Text textLabel =
        new Text(rx + (width >> 1), ry + (height - DrawAttr.FIXED_FONT_DESCENT - 5), label);
    textLabel.getLabel().setHorizontalAlignment(EditableLabel.CENTER);
    textLabel.getLabel().setColor(Color.WHITE);
    textLabel.getLabel().setFont(DrawAttr.DEFAULT_NAME_FONT);
    ret.add(textLabel);
    ret.add(new AppearanceAnchor(Location.create(rx + ax, ry + ay, true)));
    return ret;
  }

  private static void placePins(
      List<CanvasObject> dest,
      List<Instance> pins,
      int x,
      int y,
      int dX,
      int dY,
      boolean isLeftSide,
      int ldy,
      boolean isFixedSize) {
    int hAlign;
    final java.awt.Color color = Color.DARK_GRAY;
    int ldX;
    for (final com.cburch.logisim.instance.Instance pin : pins) {
      final int offset =
          (pin.getAttributeValue(StdAttr.WIDTH).getWidth() > 1)
              ? Wire.WIDTH_BUS >> 1
              : Wire.WIDTH >> 1;
      final int height =
          (pin.getAttributeValue(StdAttr.WIDTH).getWidth() > 1) ? Wire.WIDTH_BUS : Wire.WIDTH;
      Rectangle rect;
      if (isLeftSide) {
        ldX = 15;
        hAlign = EditableLabel.LEFT;
        rect = new Rectangle(x, y - offset, 10, height);
      } else {
        ldX = -15;
        hAlign = EditableLabel.RIGHT;
        rect = new Rectangle(x - 10, y - offset, 10, height);
      }
      rect.setValue(DrawAttr.STROKE_WIDTH, 1);
      rect.setValue(DrawAttr.PAINT_TYPE, DrawAttr.PAINT_FILL);
      rect.setValue(DrawAttr.FILL_COLOR, Color.BLACK);
      dest.add(rect);
      dest.add(new AppearancePort(Location.create(x, y, true), pin));
      if (pin.getAttributeSet().containsAttribute(StdAttr.LABEL)) {
        java.lang.String label = pin.getAttributeValue(StdAttr.LABEL);
        final int maxLength = 12;
        final java.lang.String ellipsis = "...";
        if (isFixedSize && label.length() > maxLength) {
          label = label.substring(0, maxLength - ellipsis.length()).concat(ellipsis);
        }
        final com.cburch.draw.shapes.Text textLabel = new Text(x + ldX, y + ldy, label);
        textLabel.getLabel().setHorizontalAlignment(hAlign);
        textLabel.getLabel().setColor(color);
        textLabel.getLabel().setFont(DrawAttr.DEFAULT_FIXED_PICH_FONT);
        dest.add(textLabel);
      }
      x += dX;
      y += dY;
    }
  }
}
