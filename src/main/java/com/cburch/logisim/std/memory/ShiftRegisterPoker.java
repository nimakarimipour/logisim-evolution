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

public class ShiftRegisterPoker extends InstancePoker {
  private int loc;

  private int computeStage(InstanceState state, MouseEvent e) {
    final com.cburch.logisim.data.BitWidth widObj = state.getAttributeValue(StdAttr.WIDTH);
    final com.cburch.logisim.data.Bounds bds = state.getInstance().getBounds();
    if (state.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      final java.lang.Integer lenObj = state.getAttributeValue(ShiftRegister.ATTR_LENGTH);
      final java.lang.Boolean loadObj = state.getAttributeValue(ShiftRegister.ATTR_LOAD);

      int y = bds.getY();
      final java.lang.String label = state.getAttributeValue(StdAttr.LABEL);
      if (label == null || label.equals("")) y += bds.getHeight() / 2;
      else y += 3 * bds.getHeight() / 4;
      y = e.getY() - y;
      if (y <= -6 || y >= 8) return -1;
      final int x = e.getX() - (bds.getX() + 15);
      if (!loadObj || widObj.getWidth() > 4) return -1;
      if (x < 0 || x >= lenObj * 10) return -1;
      return x / 10;
    } else {
      final int len = (widObj.getWidth() + 3) / 4;
      final int boxXpos = ((ShiftRegister.symbolWidth - 30) / 2 + 30) - (len * 4);
      final int boxXend = boxXpos + 2 + len * 8;
      final int y = e.getY() - bds.getY() - 80;
      if (y < 0) return -1;
      final int x = e.getX() - bds.getX() - 10;
      if ((x < boxXpos) || (x > boxXend)) return -1;
      return (y / 20);
    }
  }

  @Override
  public boolean init(InstanceState state, MouseEvent e) {
    loc = computeStage(state, e);
    return loc >= 0;
  }

  @Override
  public void keyTyped(InstanceState state, KeyEvent e) {
    final int loc = this.loc;
    if (loc < 0) return;
    final char c = e.getKeyChar();
    if (c == ' ' || c == '\t') {
      final java.lang.Integer lenObj = state.getAttributeValue(ShiftRegister.ATTR_LENGTH);
      if (loc < lenObj - 1) {
        this.loc = loc + 1;
        state.fireInvalidated();
      }
    } else if (c == '\u0008') {
      if (loc > 0) {
        this.loc = loc - 1;
        state.fireInvalidated();
      }
    } else {
      try {
        final int val = Integer.parseInt("" + e.getKeyChar(), 16);
        final com.cburch.logisim.data.BitWidth widObj = state.getAttributeValue(StdAttr.WIDTH);
        final com.cburch.logisim.std.memory.ShiftRegisterData data = (ShiftRegisterData) state.getData();
        final int i = data.getLength() - 1 - loc;
        long value = data.get(i).toLongValue();
        value = ((value * 16) + val) & widObj.getMask();
        final com.cburch.logisim.data.Value valObj = Value.createKnown(widObj, value);
        data.set(i, valObj);
        state.fireInvalidated();
      } catch (NumberFormatException ex) {
        return;
      }
    }
  }

  @Override
  public void keyPressed(InstanceState state, KeyEvent e) {
    final int loc = this.loc;
    if (loc < 0) return;
    com.cburch.logisim.data.BitWidth dataWidth = state.getAttributeValue(StdAttr.WIDTH);
    if (dataWidth == null) dataWidth = BitWidth.create(8);
    final com.cburch.logisim.std.memory.ShiftRegisterData data = (ShiftRegisterData) state.getData();
    final int i = data.getLength() - 1 - loc;
    long curValue = data.get(i).toLongValue();
    if (e.getKeyCode() == KeyEvent.VK_UP) {
      final long maxVal = dataWidth.getMask();
      if (curValue != maxVal) {
        curValue = curValue + 1;
        data.set(i, Value.createKnown(dataWidth, curValue));
        state.fireInvalidated();
      }
    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
      if (curValue != 0) {
        curValue = curValue - 1;
        data.set(i, Value.createKnown(dataWidth, curValue));
        state.fireInvalidated();
      }
    }
  }

  @Override
  public void mousePressed(InstanceState state, MouseEvent e) {
    loc = computeStage(state, e);
  }

  @Override
  public void mouseReleased(InstanceState state, MouseEvent e) {
    final int oldLoc = loc;
    if (oldLoc < 0) return;
    final com.cburch.logisim.data.BitWidth widObj = state.getAttributeValue(StdAttr.WIDTH);
    if (widObj.equals(BitWidth.ONE)) {
      final int newLoc = computeStage(state, e);
      if (oldLoc == newLoc) {
        final com.cburch.logisim.std.memory.ShiftRegisterData data = (ShiftRegisterData) state.getData();
        final int i = data.getLength() - 1 - loc;
        com.cburch.logisim.data.Value v = data.get(i);
        v = (v == Value.FALSE) ? Value.TRUE : Value.FALSE;
        data.set(i, v);
        state.fireInvalidated();
      }
    }
  }

  @Override
  public void paint(InstancePainter painter) {
    final int loc = this.loc;
    if (loc < 0) return;
    final com.cburch.logisim.data.BitWidth widObj = painter.getAttributeValue(StdAttr.WIDTH);
    final com.cburch.logisim.data.Bounds bds = painter.getInstance().getBounds();
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      final int x = bds.getX() + 15 + loc * 10;
      int y = bds.getY();
      final java.lang.String label = painter.getAttributeValue(StdAttr.LABEL);
      if (label == null || label.equals("")) y += bds.getHeight() / 2;
      else y += 3 * bds.getHeight() / 4;
      final java.awt.Graphics g = painter.getGraphics();
      g.setColor(Color.RED);
      g.drawRect(x, y - 6, 10, 13);
    } else {
      final int len = (widObj.getWidth() + 3) / 4;
      final int boxXpos = ((ShiftRegister.symbolWidth - 30) / 2 + 30) - (len * 4) + bds.getX() + 10;
      final int y = bds.getY() + 82 + loc * 20;
      final java.awt.Graphics g = painter.getGraphics();
      g.setColor(Color.RED);
      g.drawRect(boxXpos, y, 2 + len * 8, 16);
    }
  }
}
