/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.appear;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.io.Led;
import com.cburch.logisim.std.io.RgbLed;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collection;
import java.util.Collections;
import javax.swing.JComponent;

public class LayoutThumbnail extends JComponent {
  private static final long serialVersionUID = 1L;

  private static final int BORDER = 10;

  private CircuitState circuitState;
  private Collection<Instance> ports;
  private Collection<Instance> elts;

  public LayoutThumbnail(Dimension size) {
    circuitState = null;
    ports = null;
    elts = null;
    setBackground(Color.LIGHT_GRAY);
    setPreferredSize(size);
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (AppPreferences.AntiAliassing.getBoolean()) {
      final java.awt.Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    if (circuitState != null) {
      final com.cburch.logisim.circuit.Circuit circuit = circuitState.getCircuit();
      final com.cburch.logisim.data.Bounds bounds = circuit.getBounds(g);
      final java.awt.Dimension size = getSize();
      final double scaleX = (double) (size.width - 2 * BORDER) / bounds.getWidth();
      final double scaleY = (double) (size.height - 2 * BORDER) / bounds.getHeight();
      final double scale = Math.min(1.0, Math.min(scaleX, scaleY));

      final java.awt.Graphics gfxCopy = g.create();
      final int borderX = (int) ((size.width - bounds.getWidth() * scale) / 2);
      final int borderY = (int) ((size.height - bounds.getHeight() * scale) / 2);
      gfxCopy.translate(borderX, borderY);
      if (scale != 1.0 && g instanceof Graphics2D) {
        ((Graphics2D) gfxCopy).scale(scale, scale);
      }
      gfxCopy.translate(-bounds.getX(), -bounds.getY());

      final com.cburch.logisim.comp.ComponentDrawContext context = new ComponentDrawContext(this, circuit, circuitState, g, gfxCopy);
      context.setShowState(false);
      context.setShowColor(false);
      circuit.draw(context, Collections.emptySet());
      if (CollectionUtil.isNotEmpty(ports)) {
        gfxCopy.setColor(AppearancePort.COLOR);
        final int width = Math.max(4, (int) ((2 / scale) + 0.5));
        GraphicsUtil.switchToWidth(gfxCopy, width);
        for (final com.cburch.logisim.instance.Instance port : ports) {
          final com.cburch.logisim.data.Bounds b = port.getBounds();
          int x = b.getX();
          int y = b.getY();
          int w = b.getWidth();
          int h = b.getHeight();
          if (Pin.FACTORY.isInputPin(port)) {
            gfxCopy.drawRect(x, y, w, h);
          } else {
            if (b.getWidth() > 25) {
              gfxCopy.drawRoundRect(x, y, w, h, 4, 4);
            } else {
              gfxCopy.drawOval(x, y, w, h);
            }
          }
        }
      }

      if (CollectionUtil.isNotEmpty(elts)) {
        gfxCopy.setColor(DynamicElement.COLOR);
        final int width = Math.max(4, (int) ((2 / scale) + 0.5));
        GraphicsUtil.switchToWidth(gfxCopy, width);
        for (final com.cburch.logisim.instance.Instance elt : elts) {
          final com.cburch.logisim.data.Bounds b = elt.getBounds();
          final int x = b.getX();
          final int y = b.getY();
          final int w = b.getWidth();
          final int h = b.getHeight();
          if (elt.getFactory() instanceof Led || elt.getFactory() instanceof RgbLed) {
            gfxCopy.drawOval(x, y, w, h);
          } else {
            gfxCopy.drawRect(x, y, w, h);
          }
        }
      }
      gfxCopy.dispose();

      g.setColor(Color.BLACK);
      GraphicsUtil.switchToWidth(g, 2);
      g.drawRect(0, 0, size.width - 2, size.height - 2);
    }
  }

  public void setCircuit(
      CircuitState circuitState, Collection<Instance> ports, Collection<Instance> elts) {
    this.circuitState = circuitState;
    this.ports = ports;
    this.elts = elts;
    repaint();
  }
}
