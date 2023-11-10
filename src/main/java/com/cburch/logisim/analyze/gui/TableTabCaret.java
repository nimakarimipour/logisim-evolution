/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static com.cburch.logisim.analyze.Strings.S;

import com.cburch.contracts.BaseKeyListenerContract;
import com.cburch.contracts.BaseMouseListenerContract;
import com.cburch.contracts.BaseMouseMotionListenerContract;
import com.cburch.logisim.analyze.model.Entry;
import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.analyze.model.TruthTableEvent;
import com.cburch.logisim.analyze.model.TruthTableListener;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

class TableTabCaret {
  private class Listener
      implements BaseMouseListenerContract,
          BaseMouseMotionListenerContract,
          BaseKeyListenerContract,
          FocusListener,
          TruthTableListener,
          ActionListener {

    @Override
    public void cellsChanged(TruthTableEvent event) {
      // dummy
    }

    @Override
    public void focusGained(FocusEvent e) {
      repaint(cursor);
    }

    @Override
    public void focusLost(FocusEvent e) {
      repaint(cursor);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      final java.lang.String action = event.getActionCommand();
      switch (action) {
        case "1" -> doKey('1');
        case "0" -> doKey('0');
        case "x" -> doKey('-');
        case "compact" -> {
          final com.cburch.logisim.analyze.model.TruthTable tt = table.getTruthTable();
          if (tt.getRowCount() > 4096) {
            (new Analyzer.PleaseWait<Void>(S.get("tabcaretCompactRows"), table) {
              private static final long serialVersionUID = 1L;

              @Override
              public Void doInBackground() {
                tt.compactVisibleRows();
                return null;
              }
            })
                .get();
          } else {
            tt.compactVisibleRows();
          }
        }
        case "expand" -> {
          TruthTable model = table.getTruthTable();
          model.expandVisibleRows();
        }
        default -> {
          // do nothing
        }
      }
    }

    @Override
    public void keyPressed(KeyEvent e) {
      int rows = table.getRowCount();
      final int inputs = table.getInputColumnCount();
      final int outputs = table.getOutputColumnCount();
      final int cols = inputs + outputs;
      final boolean shift = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
      final com.cburch.logisim.analyze.gui.TableTabCaret.Pt p = (shift ? markB.isValid() ? markB : markA : cursor);
      switch (e.getKeyCode()) {
        case KeyEvent.VK_UP:
          move(p.row - 1, p.col, shift);
          break;
        case KeyEvent.VK_LEFT:
          move(p.row, p.col - 1, shift);
          break;
        case KeyEvent.VK_DOWN:
          move(p.row + 1, p.col, shift);
          break;
        case KeyEvent.VK_RIGHT:
          move(p.row, p.col + 1, shift);
          break;
        case KeyEvent.VK_HOME:
          if (p.col == 0) move(0, 0, shift);
          else move(p.row, 0, shift);
          break;
        case KeyEvent.VK_END:
          if (p.col == cols - 1) move(rows - 1, cols - 1, shift);
          else move(p.row, cols - 1, shift);
          break;
        case KeyEvent.VK_PAGE_DOWN:
          rows = table.getBody().getVisibleRect().height / table.getCellHeight();
          if (rows > 2) rows--;
          move(p.row + rows, p.col, shift);
          break;
        case KeyEvent.VK_PAGE_UP:
          rows = table.getBody().getVisibleRect().height / table.getCellHeight();
          if (rows > 2) rows--;
          move(cursor.row - rows, cursor.col, shift);
          break;
        default:
          // none
          break;
      }
    }

    @Override
    public void keyTyped(KeyEvent e) {
      final int mask = e.getModifiersEx();
      if ((mask & ~InputEvent.SHIFT_DOWN_MASK) != 0) return;
      doKey(e.getKeyChar());
    }

    private int[] allRowsContaining(List<Integer> indexes) {
      final com.cburch.logisim.analyze.model.TruthTable model = table.getTruthTable();
      int n = (indexes == null ? 0 : indexes.size());
      if (n == 0) return null;
      final int[] rows = new int[n];
      for (int i = 0; i < n; i++) rows[i] = model.findVisibleRowContaining(indexes.get(i));
      Arrays.sort(rows);
      return rows;
    }

    private List<Integer> allIndexesForRowRange(int r1, int r2) {
      final com.cburch.logisim.analyze.model.TruthTable model = table.getTruthTable();
      if (r1 < 0 || r2 < 0) return null;
      if (r1 > r2) {
        final int t = r1;
        r1 = r2;
        r2 = t;
      }
      final java.util.ArrayList<java.lang.Integer> indexes = new ArrayList<Integer>();
      for (int r = r1; r <= r2; r++) {
        for (final java.lang.Integer idx : model.getVisibleRowIndexes(r)) indexes.add(idx);
      }
      Collections.sort(indexes);
      return indexes;
    }

    void doKey(char c) {
      clearHilight();
      table.requestFocus();
      if (!cursor.isValid()) {
        if (!marked()) return;
        final java.awt.Rectangle s = getSelection();
        cursor = new Pt(s.y, s.x);
        repaint(cursor);
        scrollTo(cursor);
      }
      final com.cburch.logisim.analyze.model.TruthTable model = table.getTruthTable();
      final int inputs = table.getInputColumnCount();
      Entry newEntry = null;
      int dx = 1;
      int dy = 0;
      switch (c) {
        case ' ':
          if (cursor.col < inputs) {
            final com.cburch.logisim.analyze.model.Entry cur = model.getVisibleInputEntry(cursor.row, cursor.col);
            newEntry = (cur == Entry.DONT_CARE ? Entry.ZERO : Entry.ONE);
          } else {
            final com.cburch.logisim.analyze.model.Entry cur = model.getVisibleOutputEntry(cursor.row, cursor.col - inputs);
            if (cur == Entry.ZERO) newEntry = Entry.ONE;
            else if (cur == Entry.ONE) newEntry = Entry.DONT_CARE;
            else newEntry = Entry.ZERO;
          }
          break;
        case '0':
        case 'f':
        case 'F':
          newEntry = Entry.ZERO;
          break;
        case '1':
        case 't':
        case 'T':
          newEntry = Entry.ONE;
          break;
        case '-':
        case 'x':
        case 'X':
          newEntry = Entry.DONT_CARE;
          break;
        case '\n':
          dy = 1;
          break;
        case '\u0008': // backspace
          newEntry = Entry.DONT_CARE;
          dx = -1;
          break;
        default:
          return;
      }
      if (newEntry != null && cursor.col < inputs) {
        // Nearly all of this is just trying to do a sensible
        // cursor/selection update.
        // FIXME: This is very inefficient for large tables. It
        // makes a round trip from row numbers to indexes and
        // back, for the cursor and the marks. But there is no
        // obvious way to get from an index to a row number
        // except for scanning all existing rows.
        // First: save the old state
        com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldCursor = cursor;
        com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldMarkA = markA;
        com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldMarkB = markB;
        List<Integer> oldCursorIdx;
        List<Integer> oldMarkIdx;
        oldCursorIdx = allIndexesForRowRange(cursor.row, cursor.row);
        oldMarkIdx = allIndexesForRowRange(markA.row, markB.row);
        // Second: do the actual update
        boolean updated = model.setVisibleInputEntry(cursor.row, cursor.col, newEntry, true);
        // Third: try to update the cursor and selection.
        if (updated) {
          // Update the cursor position
          cursor = invalid;
          int[] rows = allRowsContaining(oldCursorIdx);
          if (rows != null) {
            if (newEntry != Entry.ONE) {
              cursor = new Pt(rows[0], oldCursor.col);
            } else {
              cursor = new Pt(rows[rows.length - 1], oldCursor.col);
            }
            hilightRows = rows;
          }
          // Update the selection
          markA = cursor;
          markB = invalid;
          int[] marks = allRowsContaining(oldMarkIdx);
          if (marks != null) {
            int n = marks.length;
            if (isContiguous(marks)) {
              final boolean fwd = oldMarkA.row <= oldMarkB.row;
              markA = new Pt(marks[fwd ? 0 : n - 1], oldMarkA.col);
              markB = new Pt(marks[fwd ? n - 1 : 0], oldMarkB.col);
            }
            hilightRows = marks;
          }
          table.repaint();
        }
      } else if (newEntry != null) {
        model.setVisibleOutputEntry(cursor.row, cursor.col - inputs, newEntry);
      }
      if (!markA.isValid() || !markB.isValid()) return;
      final java.awt.Rectangle selection = getSelection();
      int row = cursor.row;
      int col = cursor.col;
      if (dy > 0) { // advance down
        col = selection.x;
        if (++row >= selection.y + selection.height) row = selection.y;
      } else if (dx > 0) { // advance right
        if (++col >= selection.x + selection.width) {
          col = selection.x;
          if (++row >= selection.y + selection.height) {
            row = selection.y;
          }
        }
      } else if (dx < 0) { // advance left
        if (--col < selection.x) {
          col = selection.x + selection.width - 1;
          if (--row < selection.y) row = selection.y + selection.height - 1;
        }
      }
      final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldCursor = cursor;
      cursor = new Pt(row, col);
      repaint(oldCursor, cursor, markA, markB);
      scrollTo(cursor);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (cursor.isValid() && cursor.col >= table.getInputColumnCount()) {
        /* We clicked inside the output region; we mark the complete
         * region below the cursor.
         * markA is already set, so we set markB to the end of the table
         */
        markB = new Pt(table.getRowCount() - 1, markA.col);
        repaint(markA, markB);
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldMarkB = markB;
      markB = pointNear(e);
      repaint(oldMarkB, cursor, markA, markB);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldHover = hover;
      hover = pointAt(e);
      repaint(oldHover, hover);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldHover = hover;
      hover = pointAt(e);
      repaint(oldHover, hover);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldHover = hover;
      hover = invalid;
      repaint(oldHover, hover);
    }

    @Override
    public void mousePressed(MouseEvent e) {
      table.requestFocus();
      if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
        mouseDragged(e);
      } else {
        setCursor(pointAt(e), pointNear(e));
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      mouseDragged(e);
    }

    @Override
    public void rowsChanged(TruthTableEvent event) {
      structureChanged(event);
    }

    @Override
    public void structureChanged(TruthTableEvent event) {
      cursor = invalid;
      markA = invalid;
      markB = invalid;
      hover = invalid;
      clearHilight();
      repaint();
    }

    Pt pointAt(MouseEvent e) {
      return new Pt(table.getRow(e), table.getColumn(e));
    }

    Pt pointNear(MouseEvent e) {
      return new Pt(table.getNearestRow(e), table.getNearestColumn(e));
    }
  }

  private class Pt implements Comparable<Pt> {
    final int row;
    final int col;

    Pt() {
      row = -1;
      col = -1;
    }

    Pt(int r, int c) {
      row = r;
      col = c;
    }

    boolean isValid() {
      return row >= 0 && col >= 0 && row < table.getRowCount() && col < table.getColumnCount();
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof Pt other)
             ? (other.row == this.row && other.col == this.col)
                     || (!other.isValid() && !this.isValid())
             : false;
    }

    @Override
    public int compareTo(Pt other) {
      if (!other.isValid()) return (!this.isValid()) ? 0 : 1;
      else if (!this.isValid()) return -1;
      else if (other.row != this.row) return this.row - other.row;
      else return this.col - other.col;
    }

    @Override
    public String toString() {
      return isValid() ? "Pt(" + row + ", " + col + ")" : "Pt(?, ?)";
    }
  }

  private static final Color SELECT_COLOR = new Color(192, 192, 255);
  private static final Color HIGHLIGHT_COLOR = new Color(255, 255, 192);
  private final Listener listener = new Listener();
  private final TableTab table;
  private Pt cursor;
  private Pt markA;
  private Pt markB;
  private Pt hover;
  private final Pt invalid;
  private final Pt home;
  private int[] hilightRows;
  private boolean cleanHilight;

  private void clearHilight() {
    if (hilightRows == null) return;
    cleanHilight = true;
    hilightRows = null;
  }

  public ActionListener getListener() {
    return listener;
  }

  TableTabCaret(TableTab table) {
    this.table = table;
    invalid = new Pt();
    home = new Pt(0, 0);
    cursor = home;
    markA = cursor;
    markB = invalid;
    hover = invalid;
    table.getTruthTable().addTruthTableListener(listener);
    table.getBody().addMouseListener(listener);
    table.getBody().addMouseMotionListener(listener);
    table.addKeyListener(listener);
    table.addFocusListener(listener);

    final javax.swing.InputMap imap = table.getInputMap();
    final javax.swing.ActionMap amap = table.getActionMap();
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

  boolean marked() {
    return (markA.isValid() && markB.isValid());
  }

  Rectangle getSelection() {
    if (marked()) {
      final int r0 = Math.min(markA.row, markB.row);
      final int c0 = Math.min(markA.col, markB.col);
      final int r1 = Math.max(markA.row, markB.row);
      final int c1 = Math.max(markA.col, markB.col);
      return new Rectangle(c0, r0, (c1 - c0) + 1, (r1 - r0) + 1);
    } else if (cursor.isValid()) {
      return new Rectangle(cursor.col, cursor.row, 1, 1);
    } else {
      return new Rectangle(0, 0, -1, -1);
    }
  }

  boolean hasSelection() {
    return marked() || cursor.isValid();
  }

  boolean hadSelection = false;

  void updateMenus() {
    final boolean sel = hasSelection();
    if (hadSelection != sel) {
      hadSelection = sel;
      table.updateTab();
    }
  }

  void paintBackground(Graphics g) {
    if (hilightRows != null) {
      g.setColor(HIGHLIGHT_COLOR);
      final int inputs = table.getInputColumnCount();
      final int outputs = table.getOutputColumnCount();
      final int x0 = table.getXLeft(0);
      final int x1 = table.getXRight(inputs + outputs - 1);
      for (final int rowId : hilightRows) {
        final int y = table.getY(rowId);
        final int h = table.getCellHeight();
        g.fillRect(x0, y, x1 - x0, h);
      }
    }
    if (marked() && !markA.equals(markB)) {
      final java.awt.Rectangle r = region(markA, markB);
      g.setColor(SELECT_COLOR);
      g.fillRect(r.x, r.y, r.width, r.height);
    }
    if (table.isFocusOwner() && cursor.isValid()) {
      final java.awt.Rectangle r = region(cursor);
      g.setColor(Color.WHITE);
      g.fillRect(r.x, r.y + 1, r.width - 1, r.height - 3);
    }
  }

  void paintForeground(Graphics g) {
    if (!table.isFocusOwner()) return;
    Pt p;
    if (cursor.isValid()) {
      p = cursor;
      g.setColor(Color.BLACK);
    } else if (hover.isValid()) {
      p = hover;
      g.setColor(Color.GRAY);
    } else {
      return;
    }
    final int x = table.getXLeft(p.col);
    final int y = table.getY(p.row);
    final int w = table.getCellWidth(p.row);
    final int h = table.getCellHeight();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x - 1, y, w + 1, h - 2);
    GraphicsUtil.switchToWidth(g, 1);
  }

  void selectAll() {
    table.requestFocus();
    clearHilight();
    cursor = invalid;
    markA = new Pt(0, 0);
    markB = new Pt(table.getRowCount() - 1, table.getColumnCount() - 1);
    repaint(markA, markB);
  }

  private Pt pointNear(int row, int col) {
    final int inputs = table.getInputColumnCount();
    final int outputs = table.getOutputColumnCount();
    final int rows = table.getRowCount();
    final int cols = inputs + outputs;
    row = row < 0 ? 0 : row >= rows ? rows - 1 : row;
    col = col < 0 ? 0 : col >= cols ? cols - 1 : col;
    return new Pt(row, col);
  }

  private void move(int row, int col, boolean shift) {
    final com.cburch.logisim.analyze.gui.TableTabCaret.Pt p = pointNear(row, col);
    if (shift) {
      final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldMarkB = markB;
      markB = p;
      repaint(oldMarkB, cursor, markA, markB);
      scrollTo(markB);
    } else {
      setCursor(p, p);
    }
  }

  private void setCursor(Pt p, Pt m) {
    final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldCursor = cursor;
    final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldMarkA = markA;
    final com.cburch.logisim.analyze.gui.TableTabCaret.Pt oldMarkB = markB;
    clearHilight();
    cursor = p;
    markA = m;
    markB = invalid;
    repaint(oldCursor, oldMarkA, oldMarkB, cursor, markA, markB);
    if (cursor.isValid()) scrollTo(cursor);
  }

  private void scrollTo(Pt p) {
    if (!p.isValid()) return;
    final int cx = table.getXLeft(p.col);
    final int cy = table.getY(p.row);
    final int cw = table.getCellWidth(p.col);
    final int ch = table.getCellHeight();
    table.getBody().scrollRectToVisible(new Rectangle(cx, cy, cw, ch));
  }

  private void repaint(Pt... pts) {
    updateMenus();
    if (cleanHilight) {
      cleanHilight = false;
      table.repaint();
      return;
    }
    final java.awt.Rectangle r = region(pts);
    if (r.isEmpty()) return;
    r.grow(2, 2);
    table.getBody().repaint(r);
  }

  private Rectangle region(Pt... pts) {
    int r0 = -1;
    int r1 = -1;
    int c0 = -1;
    int c1 = -1;
    for (Pt p : pts) {
      if (p == null || !p.isValid()) continue;
      if (r0 == -1) {
        r0 = p.row;
        c0 = p.col;
        r1 = r0;
        c1 = c0;
      } else {
        r0 = Math.min(r0, p.row);
        c0 = Math.min(c0, p.col);
        r1 = Math.max(r1, p.row);
        c1 = Math.max(c1, p.col);
      }
    }
    if (r0 < 0) return new Rectangle(0, 0, -1, -1);
    int x0 = table.getXLeft(c0);
    int x1 = table.getXRight(c1);
    int y0 = table.getY(r0);
    int y1 = table.getY(r1) + table.getCellHeight();
    return new Rectangle(x0 - 2, y0 - 2, (x1 - x0) + 4, (y1 - y0) + 4);
  }

  private boolean isContiguous(int[] rows) {
    if (rows.length <= 1) return true;
    for (int i = 1; i < rows.length; i++) {
      // FIXME: this condition is always false. It most likely was meant to read:
      // if (Math.abs(rows[i] - rows[i+1]) > 1) return false;
      if (Math.abs(rows[i] - rows[i]) > 1) return false;
    }
    return true;
  }
}
