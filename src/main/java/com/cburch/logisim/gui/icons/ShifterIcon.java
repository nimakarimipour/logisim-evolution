/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.icons;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;

public class ShifterIcon extends BaseIcon {

  private final int state = 2;

  @Override
  protected void paintIcon(Graphics2D g2) {
    final java.lang.StringBuilder s = new StringBuilder();
    if (state < 0) {
      s.append("\u25b6".repeat(3));
    } else {
      int mask = 4;
      while (mask > 0) {
        s.append((state & mask) == 0 ? "0" : "1");
        mask >>= 1;
      }
    }
    final java.awt.Font f = g2.getFont().deriveFont(scale((float) 6)).deriveFont(Font.BOLD);
    g2.setColor(Color.BLACK);
    g2.drawRect(0, scale(4), scale(16), scale(8));
    final java.awt.font.TextLayout t = new TextLayout(s.toString(), f, g2.getFontRenderContext());
    final float x = scale((float) 8) - (float) t.getBounds().getCenterX();
    final float y = scale((float) 8) - (float) t.getBounds().getCenterY();
    t.draw(g2, x, y);
  }
}
