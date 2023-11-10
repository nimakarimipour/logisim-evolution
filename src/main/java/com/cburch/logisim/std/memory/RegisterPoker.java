/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class RegisterPoker extends InstancePoker {
  private long initValue;
  private long curValue;

  @Override
  public boolean init(InstanceState state, MouseEvent e) {
    RegisterData data = (RegisterData) state.getData();
    if (data == null) {
      data = new RegisterData(state.getAttributeValue(StdAttr.WIDTH));
      state.setData(data);
    }
    initValue = (data.value.isFullyDefined()) ? data.value.toLongValue() : 0;
    curValue = initValue;
    return true;
  }

  @Override
  public void keyTyped(InstanceState state, KeyEvent e) {
    final int val = Character.digit(e.getKeyChar(), 16);
    if (val < 0) return;

    com.cburch.logisim.data.BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
    if (dataWidth == null) dataWidth = BitWidth.create(8);
    curValue = (curValue * 16 + val) & dataWidth.getMask();
    final com.cburch.logisim.std.memory.RegisterData data = (RegisterData) state.getData();
    data.value = Value.createKnown(dataWidth, curValue);
    state.fireInvalidated();
  }

  @Override
  public void keyPressed(InstanceState state, KeyEvent e) {
    com.cburch.logisim.data.BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
    if (dataWidth == null) dataWidth = BitWidth.create(8);
    if (e.getKeyCode() == KeyEvent.VK_UP) {
      final long maxVal = dataWidth.getMask();
      if (curValue != maxVal) {
        curValue = curValue + 1;
        final com.cburch.logisim.std.memory.RegisterData data = (RegisterData) state.getData();
        data.value = Value.createKnown(dataWidth, curValue);
        state.fireInvalidated();
      }
    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
      if (curValue != 0) {
        curValue = curValue - 1;
        final com.cburch.logisim.std.memory.RegisterData data = (RegisterData) state.getData();
        data.value = Value.createKnown(dataWidth, curValue);
        state.fireInvalidated();
      }
    }
  }

  @Override
  public void paint(InstancePainter painter) {
    final com.cburch.logisim.data.Bounds bds = painter.getBounds();
    final com.cburch.logisim.data.BitWidth dataWidth = painter.getAttributeValue(StdAttr.WIDTH);
    final int width = dataWidth == null ? 8 : dataWidth.getWidth();
    final int len = (width + 3) / 4;

    final java.awt.Graphics g = painter.getGraphics();
    g.setColor(Color.RED);
    final int wid = 8 * len + 2;
    g.drawRect(bds.getX() + (bds.getWidth() - wid) / 2, bds.getY(), wid, 16);
    g.setColor(Color.BLACK);
  }
}
