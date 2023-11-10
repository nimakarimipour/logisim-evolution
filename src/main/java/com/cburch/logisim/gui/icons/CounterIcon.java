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
import java.awt.Graphics2D;
import java.awt.font.TextLayout;

public class CounterIcon extends BaseIcon {

  private final int state = 1;

  @Override
  protected void paintIcon(Graphics2D g2) {
    g2.setColor(Color.BLACK);
    g2.fillRect(scale(1), 0, scale(6), scale(16));
    g2.fillRect(scale(9), 0, scale(6), scale(16));
    g2.drawRect(scale(1), 0, scale(6), scale(16));
    g2.drawRect(scale(9), 0, scale(6), scale(16));
    final java.awt.Font f = g2.getFont().deriveFont(scale((float) 6));
    final int tens = state / 10;
    final int ones = state % 10;
    g2.setColor(Color.WHITE);
    for (int i = -1; i < 2; i++) {
      final int val = Math.abs((ones + i) % 10);
      final char c = (char) ('0' + val);
      final java.awt.font.TextLayout t = new TextLayout(Character.toString(c), f, g2.getFontRenderContext());
      final float x = scale((float) 11.5) - (float) t.getBounds().getCenterX();
      final float y = scale((float) (8.5 + i * 7)) - (float) t.getBounds().getCenterY();
      t.draw(g2, x, y);
    }
    for (int i = -1; i < 2; i++) {
      final int val = Math.abs((tens + i) % 10);
      final char c = (char) ('0' + val);
      final java.awt.font.TextLayout t = new TextLayout(Character.toString(c), f, g2.getFontRenderContext());
      final float x = scale((float) 3.5) - (float) t.getBounds().getCenterX();
      final float y = scale((float) (8.5 + i * 7)) - (float) t.getBounds().getCenterY();
      t.draw(g2, x, y);
    }
  }
}
