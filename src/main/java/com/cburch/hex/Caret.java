/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.hex;

import com.cburch.contracts.BaseKeyListenerContract;
import com.cburch.contracts.BaseMouseListenerContract;
import com.cburch.contracts.BaseMouseMotionListenerContract;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Caret {
  private static final Color SELECT_COLOR = new Color(192, 192, 255);
  private static final Stroke CURSOR_STROKE = new BasicStroke(2.0F);
  private final HexEditor hex;
  private final List<ChangeListener> listeners;
  private long mark;
  private long cursor;
  private Object highlight;

  Caret(HexEditor hex) {
    this.hex = hex;
    this.listeners = new ArrayList<>();
    this.cursor = -1;

    final com.cburch.hex.Caret.Listener l = new Listener();
    hex.addMouseListener(l);
    hex.addMouseMotionListener(l);
    hex.addKeyListener(l);
    hex.addFocusListener(l);

    final javax.swing.InputMap imap = hex.getInputMap();
    final javax.swing.ActionMap amap = hex.getActionMap();
    final javax.swing.AbstractAction nullAction =
        new AbstractAction() {
          private static final long serialVersionUID = 1L;

          @Override
          public void actionPerformed(ActionEvent e) {
            // dummy
          }
        };
    final java.lang.String nullKey = "null";
    amap.put(nullKey, nullAction);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), nullKey);
    imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), nullKey);
  }

  public void addChangeListener(ChangeListener l) {
    listeners.add(l);
  }

  private void expose(long loc, boolean scrollTo) {
    if (loc >= 0) {
      final com.cburch.hex.Measures measures = hex.getMeasures();
      final int x = measures.toX(loc);
      final int y = measures.toY(loc);
      final int w = measures.getCellWidth();
      final int h = measures.getCellHeight();
      hex.repaint(x - 1, y - 1, w + 2, h + 2);
      if (scrollTo) {
        hex.scrollRectToVisible(new Rectangle(x, y, w, h));
      }
    }
  }

  public long getDot() {
    return cursor;
  }

  public long getMark() {
    return mark;
  }

  void paintForeground(Graphics g, long start, long end) {
    if (cursor >= start && cursor < end && hex.isFocusOwner()) {
      final com.cburch.hex.Measures measures = hex.getMeasures();
      final int x = measures.toX(cursor);
      final int y = measures.toY(cursor);
      final java.awt.Graphics2D g2 = (Graphics2D) g;
      final java.awt.Stroke oldStroke = g2.getStroke();
      g2.setColor(hex.getForeground());
      g2.setStroke(CURSOR_STROKE);
      g2.drawRect(x, y, measures.getCellWidth() - 1, measures.getCellHeight() - 1);
      g2.setStroke(oldStroke);
    }
  }

  public void removeChangeListener(ChangeListener l) {
    listeners.remove(l);
  }

  public void setDot(long value, boolean keepMark) {
    final com.cburch.hex.HexModel model = hex.getModel();
    if (model == null || value < model.getFirstOffset() || value > model.getLastOffset()) {
      value = -1;
    }
    if (cursor != value) {
      final long oldValue = cursor;
      if (highlight != null) {
        hex.getHighlighter().remove(highlight);
        highlight = null;
      }
      if (!keepMark) {
        mark = value;
      } else if (mark != value) {
        highlight = hex.getHighlighter().add(mark, value, SELECT_COLOR);
      }
      cursor = value;
      expose(oldValue, false);
      expose(value, true);
      if (!listeners.isEmpty()) {
        final javax.swing.event.ChangeEvent event = new ChangeEvent(this);
        for (final javax.swing.event.ChangeListener l : listeners) {
          l.stateChanged(event);
        }
      }
    }
  }

  private class Listener
      implements BaseMouseListenerContract,
          BaseMouseMotionListenerContract,
          BaseKeyListenerContract,
          FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
      expose(cursor, false);
    }

    @Override
    public void focusLost(FocusEvent e) {
      expose(cursor, false);
    }

    @Override
    public void keyPressed(KeyEvent e) {
      final int cols = hex.getMeasures().getColumnCount();
      int rows;
      final boolean shift = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
      switch (e.getKeyCode()) {
        case KeyEvent.VK_UP:
          if (cursor >= cols) setDot(cursor - cols, shift);
          break;
        case KeyEvent.VK_LEFT:
          if (cursor >= 1) setDot(cursor - 1, shift);
          break;
        case KeyEvent.VK_DOWN:
          if (cursor >= hex.getModel().getFirstOffset()
              && cursor <= hex.getModel().getLastOffset() - cols) {
            setDot(cursor + cols, shift);
          }
          break;
        case KeyEvent.VK_RIGHT:
          if (cursor >= hex.getModel().getFirstOffset()
              && cursor <= hex.getModel().getLastOffset() - 1) {
            setDot(cursor + 1, shift);
          }
          break;
        case KeyEvent.VK_HOME:
          if (cursor >= 0) {
            final int dist = (int) (cursor % cols);
            if (dist == 0) {
              setDot(0, shift);
            } else {
              setDot(cursor - dist, shift);
            }
          }
          break;
        case KeyEvent.VK_END:
          if (cursor >= 0) {
            final com.cburch.hex.HexModel model = hex.getModel();
            long dest = (cursor / cols * cols) + cols - 1;
            if (model != null) {
              final long end = model.getLastOffset();
              if (dest > end || dest == cursor) dest = end;
            }
            setDot(dest, shift);
          }
          break;
        case KeyEvent.VK_PAGE_DOWN:
          rows = hex.getVisibleRect().height / hex.getMeasures().getCellHeight();
          if (rows > 2) rows--;
          if (cursor >= 0) {
            final long max = hex.getModel().getLastOffset();
            if (cursor + rows * cols <= max) {
              setDot(cursor + rows * cols, shift);
            } else {
              long n = cursor;
              while (n + cols < max) n += cols;
              setDot(n, shift);
            }
          }
          break;
        case KeyEvent.VK_PAGE_UP:
          rows = hex.getVisibleRect().height / hex.getMeasures().getCellHeight();
          if (rows > 2) rows--;
          if (cursor >= rows * cols) setDot(cursor - rows * cols, shift);
          else if (cursor >= cols) setDot(cursor % cols, shift);
          break;

        default:
          // do nothing
      }
    }

    @Override
    public void keyTyped(KeyEvent e) {
      final int mask = e.getModifiersEx();
      if ((mask & ~InputEvent.SHIFT_DOWN_MASK) != 0) return;

      final char c = e.getKeyChar();
      final int cols = hex.getMeasures().getColumnCount();
      switch (c) {
        case ' ':
          if (cursor >= 0) setDot(cursor + 1, (mask & InputEvent.SHIFT_DOWN_MASK) != 0);
          break;
        case '\n':
          if (cursor >= 0) setDot(cursor + cols, (mask & InputEvent.SHIFT_DOWN_MASK) != 0);
          break;
        case '\u0008':
        case '\u007f':
          hex.delete();
          // setDot(cursor - 1, (mask & InputEvent.SHIFT_MASK) != 0);
          break;
        default:
          final int digit = Character.digit(e.getKeyChar(), 16);
          if (digit >= 0) {
            final com.cburch.hex.HexModel model = hex.getModel();
            if (model != null
                && cursor >= model.getFirstOffset()
                && cursor <= model.getLastOffset()) {
              final long curValue = model.get(cursor);
              final long newValue = 16 * curValue + digit;
              model.set(cursor, newValue);
            }
          }
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      final com.cburch.hex.Measures measures = hex.getMeasures();
      final long loc = measures.toAddress(e.getX(), e.getY());
      setDot(loc, true);

      // TODO should repeat dragged events when mouse leaves the
      // component
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
      // do nothing
    }

    @Override
    public void mousePressed(MouseEvent e) {
      final com.cburch.hex.Measures measures = hex.getMeasures();
      final long loc = measures.toAddress(e.getX(), e.getY());
      setDot(loc, (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0);
      if (!hex.isFocusOwner()) hex.requestFocus();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      mouseDragged(e);
    }
  }
}
