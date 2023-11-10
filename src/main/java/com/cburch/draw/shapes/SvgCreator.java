/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.shapes;

import com.cburch.draw.model.AbstractCanvasObject;
import java.awt.Color;
import java.awt.Font;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class SvgCreator {
  private SvgCreator() {
    // dummy
  }

  public static boolean colorMatches(Color a, Color b) {
    return a.getRed() == b.getRed() && a.getGreen() == b.getGreen() && a.getBlue() == b.getBlue();
  }

  public static Element createCurve(Document doc, Curve curve) {
    final org.w3c.dom.Element elt = doc.createElement("path");
    final com.cburch.logisim.data.Location e0 = curve.getEnd0();
    final com.cburch.logisim.data.Location e1 = curve.getEnd1();
    final com.cburch.logisim.data.Location ct = curve.getControl();
    elt.setAttribute(
        "d",
        "M" + e0.getX() + "," + e0.getY() + " Q" + ct.getX() + "," + ct.getY() + " " + e1.getX()
            + "," + e1.getY());
    populateFill(elt, curve);
    return elt;
  }

  public static Element createLine(Document doc, Line line) {
    final org.w3c.dom.Element elt = doc.createElement("line");
    final com.cburch.logisim.data.Location v1 = line.getEnd0();
    final com.cburch.logisim.data.Location v2 = line.getEnd1();
    elt.setAttribute("x1", "" + v1.getX());
    elt.setAttribute("y1", "" + v1.getY());
    elt.setAttribute("x2", "" + v2.getX());
    elt.setAttribute("y2", "" + v2.getY());
    populateStroke(elt, line);
    return elt;
  }

  public static Element createOval(Document doc, Oval oval) {
    final int x = oval.getX();
    final int y = oval.getY();
    final int width = oval.getWidth();
    final int height = oval.getHeight();
    final org.w3c.dom.Element elt = doc.createElement("ellipse");
    elt.setAttribute("cx", "" + (x + width / 2));
    elt.setAttribute("cy", "" + (y + height / 2));
    elt.setAttribute("rx", "" + (width / 2));
    elt.setAttribute("ry", "" + (height / 2));
    populateFill(elt, oval);
    return elt;
  }

  public static Element createPoly(Document doc, Poly poly) {
    Element elt;
    elt = (poly.isClosed()) ? doc.createElement("polygon") : doc.createElement("polyline");

    final java.lang.StringBuilder points = new StringBuilder();
    boolean first = true;
    for (final com.cburch.draw.model.Handle h : poly.getHandles(null)) {
      if (!first) points.append(" ");
      points.append(h.getX()).append(",").append(h.getY());
      first = false;
    }
    elt.setAttribute("points", points.toString());

    populateFill(elt, poly);
    return elt;
  }

  public static Element createRectangle(Document doc, Rectangle rect) {
    return createRectangular(doc, rect);
  }

  private static Element createRectangular(Document doc, Rectangular rect) {
    final org.w3c.dom.Element elt = doc.createElement("rect");
    elt.setAttribute("x", "" + rect.getX());
    elt.setAttribute("y", "" + rect.getY());
    elt.setAttribute("width", "" + rect.getWidth());
    elt.setAttribute("height", "" + rect.getHeight());
    populateFill(elt, rect);
    return elt;
  }

  public static Element createRoundRectangle(Document doc, RoundRectangle rrect) {
    final org.w3c.dom.Element elt = createRectangular(doc, rrect);
    final java.lang.Integer r = rrect.getValue(DrawAttr.CORNER_RADIUS);
    elt.setAttribute("rx", "" + r);
    elt.setAttribute("ry", "" + r);
    return elt;
  }

  public static Element createText(Document doc, Text text) {
    final org.w3c.dom.Element elt = doc.createElement("text");
    final com.cburch.logisim.data.Location loc = text.getLocation();
    final java.awt.Font font = text.getValue(DrawAttr.FONT);
    final java.awt.Color fill = text.getValue(DrawAttr.FILL_COLOR);
    final Object halign = text.getValue(DrawAttr.HALIGNMENT);
    final Object valign = text.getValue(DrawAttr.VALIGNMENT);
    elt.setAttribute("x", "" + loc.getX());
    elt.setAttribute("y", "" + loc.getY());
    if (!colorMatches(fill, Color.BLACK)) {
      elt.setAttribute("fill", getColorString(fill));
    }
    if (showOpacity(fill)) {
      elt.setAttribute("fill-opacity", getOpacityString(fill));
    }
    setFontAttribute(elt, font, "");
    if (halign == DrawAttr.HALIGN_LEFT) {
      elt.setAttribute("text-anchor", "start");
    } else if (halign == DrawAttr.HALIGN_RIGHT) {
      elt.setAttribute("text-anchor", "end");
    } else {
      elt.setAttribute("text-anchor", "middle");
    }
    if (valign == DrawAttr.VALIGN_TOP) {
      elt.setAttribute("dominant-baseline", "top");
    } else if (valign == DrawAttr.VALIGN_BOTTOM) {
      elt.setAttribute("dominant-baseline", "bottom");
    } else if (valign == DrawAttr.VALIGN_BASELINE) {
      elt.setAttribute("dominant-baseline", "alphabetic");
    } else {
      elt.setAttribute("dominant-baseline", "central");
    }
    elt.appendChild(doc.createTextNode(text.getText()));
    return elt;
  }

  public static void setFontAttribute(Element elt, Font font, String prefix) {
    elt.setAttribute(prefix + "font-family", font.getFamily());
    elt.setAttribute(prefix + "font-size", "" + font.getSize());
    final int style = font.getStyle();
    if ((style & Font.ITALIC) != 0) {
      elt.setAttribute(prefix + "font-style", "italic");
    }
    if ((style & Font.BOLD) != 0) {
      elt.setAttribute(prefix + "font-weight", "bold");
    }
  }

  public static String getColorString(Color color) {
    return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
  }

  private static String getOpacityString(Color color) {
    return String.format("%5.3f", color.getAlpha() / 255.0);
  }

  private static void populateFill(Element elt, AbstractCanvasObject shape) {
    final com.cburch.logisim.data.AttributeOption type = shape.getValue(DrawAttr.PAINT_TYPE);
    if (type == DrawAttr.PAINT_FILL) {
      elt.setAttribute("stroke", "none");
    } else {
      populateStroke(elt, shape);
    }
    if (type == DrawAttr.PAINT_STROKE) {
      elt.setAttribute("fill", "none");
    } else {
      final java.awt.Color fill = shape.getValue(DrawAttr.FILL_COLOR);
      if (colorMatches(fill, Color.BLACK)) {
        elt.removeAttribute("fill");
      } else {
        elt.setAttribute("fill", getColorString(fill));
      }
      if (showOpacity(fill)) {
        elt.setAttribute("fill-opacity", getOpacityString(fill));
      }
    }
  }

  private static void populateStroke(Element elt, AbstractCanvasObject shape) {
    final java.lang.Integer width = shape.getValue(DrawAttr.STROKE_WIDTH);
    if (width != null && width != 1) {
      elt.setAttribute("stroke-width", width.toString());
    }
    final java.awt.Color stroke = shape.getValue(DrawAttr.STROKE_COLOR);
    elt.setAttribute("stroke", getColorString(stroke));
    if (showOpacity(stroke)) {
      elt.setAttribute("stroke-opacity", getOpacityString(stroke));
    }
    elt.setAttribute("fill", "none");
  }

  private static boolean showOpacity(Color color) {
    return color.getAlpha() != 255;
  }
}
