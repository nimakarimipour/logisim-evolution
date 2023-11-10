/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;

public class ComboBox<T> extends JComboBox<T> {
  private static final long serialVersionUID = 1L;

  public ComboBox(T[] choices) {
    super(choices);
    setMaximumRowCount(Math.min(choices.length, 33));
    setKeySelectionManager(new MultiCharSelectionManager());
  }

  public static class MultiCharSelectionManager implements JComboBox.KeySelectionManager {
    String prefix = "";
    long last;

    static int currentIndex(ComboBoxModel<?> model) {
      final java.lang.Object item = model.getSelectedItem();
      for (int i = 0; item != null && i < model.getSize(); i++) {
        if (item.equals(model.getElementAt(i))) return i;
      }
      return -1;
    }

    @Override
    public int selectionForKey(char ch, ComboBoxModel<?> model) {
      int idx = currentIndex(model);
      final long now = System.currentTimeMillis();
      if (now > last + 500) {
        prefix = "";
        idx = 0;
      }
      last = now;

      prefix += Character.toLowerCase(ch);

      final int n = model.getSize();
      for (int offset = 0; offset < n; offset++) {
        final int i = (idx + offset) % n;
        final java.lang.Object item = model.getElementAt(i);
        if (item != null && item.toString().toLowerCase().startsWith(prefix)) return i;
      }

      return -1;
    }
  }
}
