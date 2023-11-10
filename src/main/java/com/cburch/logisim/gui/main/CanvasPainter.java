/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.generic.GridPainter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Set;

class CanvasPainter implements PropertyChangeListener {
  private static final Set<Component> NO_COMPONENTS = Collections.emptySet();

  private final Canvas canvas;
  private final GridPainter grid;
  private Component haloedComponent = null;
  private Circuit haloedCircuit = null;
  private WireSet highlightedWires = WireSet.EMPTY;

  CanvasPainter(Canvas canvas) {
    this.canvas = canvas;
    this.grid = new GridPainter(canvas);

    AppPreferences.ATTRIBUTE_HALO.addPropertyChangeListener(this);
    AppPreferences.CANVAS_BG_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_BG_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_DOT_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_ZOOMED_DOT_COLOR.addPropertyChangeListener(this);
  }

  private void drawWidthIncompatibilityData(Graphics base, Graphics g, Project proj) {
    final java.util.Set<com.cburch.logisim.circuit.WidthIncompatibilityData> exceptions = proj.getCurrentCircuit().getWidthIncompatibilityData();
    if (CollectionUtil.isNullOrEmpty(exceptions)) return;

    final java.awt.FontMetrics fm = base.getFontMetrics(g.getFont());
    for (final com.cburch.logisim.circuit.WidthIncompatibilityData ex : exceptions) {
      final com.cburch.logisim.data.BitWidth common = ex.getCommonBitWidth();
      for (int i = 0; i < ex.size(); i++) {
        final com.cburch.logisim.data.Location p = ex.getPoint(i);
        final com.cburch.logisim.data.BitWidth w = ex.getBitWidth(i);

        // ensure it hasn't already been drawn
        boolean drawn = false;
        for (int j = 0; j < i; j++) {
          if (ex.getPoint(j).equals(p)) {
            drawn = true;
            break;
          }
        }
        if (drawn) continue;

        // compute the caption combining all similar points
        java.lang.String caption = "" + w.getWidth();
        for (int j = i + 1; j < ex.size(); j++) {
          if (ex.getPoint(j).equals(p)) {
            caption += "/" + ex.getBitWidth(j);
            break;
          }
        }
        GraphicsUtil.switchToWidth(g, 2);
        if (common != null && !w.equals(common)) {
          g.setColor(Value.widthErrorHighlightColor);
          g.drawOval(p.getX() - 5, p.getY() - 5, 10, 10);
        }
        g.setColor(Value.widthErrorColor);
        g.drawOval(p.getX() - 4, p.getY() - 4, 8, 8);
        GraphicsUtil.switchToWidth(g, 3);
        GraphicsUtil.outlineText(
            g,
            caption,
            p.getX() + 4,
            p.getY() + 1 + fm.getAscent(),
            Value.widthErrorCaptionColor,
            common != null && !w.equals(common)
                ? Value.widthErrorHighlightColor
                : Value.widthErrorCaptionBgcolor);
      }
    }
    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 1);
  }

  private void drawWithUserState(Graphics base, Graphics g, Project proj) {
    final com.cburch.logisim.circuit.Circuit circ = proj.getCurrentCircuit();
    final com.cburch.logisim.gui.main.Selection sel = proj.getSelection();
    com.cburch.logisim.tools.Tool dragTool = canvas.getDragTool();
    Set<Component> hidden;
    if (dragTool == null) {
      hidden = NO_COMPONENTS;
    } else {
      hidden = dragTool.getHiddenComponents(canvas);
      if (hidden == null) hidden = NO_COMPONENTS;
    }

    // draw halo around component whose attributes we are viewing
    final boolean showHalo = AppPreferences.ATTRIBUTE_HALO.getBoolean();
    if (showHalo
        && haloedComponent != null
        && haloedCircuit == circ
        && !hidden.contains(haloedComponent)) {
      GraphicsUtil.switchToWidth(g, 3);
      g.setColor(Canvas.HALO_COLOR);
      final com.cburch.logisim.data.Bounds bds = haloedComponent.getBounds(g).expand(5);
      final int width = bds.getWidth();
      final int height = bds.getHeight();
      final double a = Canvas.SQRT_2 * width;
      final double b = Canvas.SQRT_2 * height;
      g.drawOval(
          (int) Math.round(bds.getX() + width / 2.0 - a / 2.0),
          (int) Math.round(bds.getY() + height / 2.0 - b / 2.0),
          (int) Math.round(a),
          (int) Math.round(b));
      GraphicsUtil.switchToWidth(g, 1);
      g.setColor(Color.BLACK);
    }

    // draw circuit and selection
    final com.cburch.logisim.circuit.CircuitState circState = proj.getCircuitState();
    final com.cburch.logisim.comp.ComponentDrawContext context = new ComponentDrawContext(canvas, circ, circState, base, g, false);
    context.setHighlightedWires(highlightedWires);
    circ.draw(context, hidden);
    sel.draw(context, hidden);

    // draw tool
    final com.cburch.logisim.tools.Tool tool = dragTool != null ? dragTool : proj.getTool();
    if (tool != null && !canvas.isPopupMenuUp()) {
      final java.awt.Graphics gfxCopy = g.create();
      context.setGraphics(gfxCopy);
      tool.draw(canvas, context);
      gfxCopy.dispose();
    }
  }

  private void exposeHaloedComponent(Graphics gfx) {
    final com.cburch.logisim.comp.Component comp = haloedComponent;
    if (comp == null) return;
    final com.cburch.logisim.data.Bounds bds = comp.getBounds(gfx).expand(7);
    final int width = bds.getWidth();
    final int height = bds.getHeight();
    final double a = Canvas.SQRT_2 * width;
    final double b = Canvas.SQRT_2 * height;
    canvas.repaint(
        (int) Math.round(bds.getX() + width / 2.0 - a / 2.0),
        (int) Math.round(bds.getY() + height / 2.0 - b / 2.0),
        (int) Math.round(a),
        (int) Math.round(b));
  }

  //
  // accessor methods
  //
  GridPainter getGridPainter() {
    return grid;
  }

  Component getHaloedComponent() {
    return haloedComponent;
  }

  //
  // painting methods
  //
  void paintContents(Graphics g, Project proj) {
    java.awt.Rectangle clip = g.getClipBounds();
    java.awt.Dimension size = canvas.getSize();
    final double zoomFactor = canvas.getZoomFactor();
    if (canvas.ifPaintDirtyReset() || clip == null) {
      clip = new Rectangle(0, 0, size.width, size.height);
    }

    grid.paintGrid(g);
    g.setColor(Color.black);

    java.awt.Graphics gfxScaled = g.create();
    if (zoomFactor != 1.0 && gfxScaled instanceof Graphics2D g2d) {
      g2d.scale(zoomFactor, zoomFactor);
    }
    drawWithUserState(g, gfxScaled, proj);
    drawWidthIncompatibilityData(g, gfxScaled, proj);
    com.cburch.logisim.circuit.Circuit circ = proj.getCurrentCircuit();

    com.cburch.logisim.circuit.CircuitState circState = proj.getCircuitState();
    com.cburch.logisim.comp.ComponentDrawContext ptContext = new ComponentDrawContext(canvas, circ, circState, g, gfxScaled);
    ptContext.setHighlightedWires(highlightedWires);
    gfxScaled.setColor(Color.RED);
    circState.drawOscillatingPoints(ptContext);
    gfxScaled.setColor(Color.BLUE);
    proj.getSimulator().drawStepPoints(ptContext);
    gfxScaled.setColor(Color.MAGENTA); // fixme
    proj.getSimulator().drawPendingInputs(ptContext);
    gfxScaled.dispose();
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (AppPreferences.GRID_BG_COLOR.isSource(event)
        || AppPreferences.GRID_DOT_COLOR.isSource(event)
        || AppPreferences.GRID_ZOOMED_DOT_COLOR.isSource(event)) {
      canvas.repaint();
    }
  }

  void setHaloedComponent(Circuit circ, Component comp) {
    if (comp == haloedComponent) return;
    final java.awt.Graphics g = canvas.getGraphics();
    exposeHaloedComponent(g);
    haloedCircuit = circ;
    haloedComponent = comp;
    exposeHaloedComponent(g);
  }

  //
  // mutator methods
  //
  void setHighlightedWires(WireSet value) {
    highlightedWires = value == null ? WireSet.EMPTY : value;
  }
}
