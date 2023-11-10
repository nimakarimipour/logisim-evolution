/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.toolbar;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractToolbarModel implements ToolbarModel {
  private final List<ToolbarModelListener> listeners;

  public AbstractToolbarModel() {
    listeners = new ArrayList<>();
  }

  @Override
  public void addToolbarModelListener(ToolbarModelListener listener) {
    listeners.add(listener);
  }

  protected void fireToolbarAppearanceChanged() {
    final com.cburch.draw.toolbar.ToolbarModelEvent event = new ToolbarModelEvent(this);
    for (final com.cburch.draw.toolbar.ToolbarModelListener listener : listeners) {
      listener.toolbarAppearanceChanged(event);
    }
  }

  protected void fireToolbarContentsChanged() {
    final com.cburch.draw.toolbar.ToolbarModelEvent event = new ToolbarModelEvent(this);
    for (final com.cburch.draw.toolbar.ToolbarModelListener listener : listeners) {
      listener.toolbarContentsChanged(event);
    }
  }

  @Override
  public void removeToolbarModelListener(ToolbarModelListener listener) {
    listeners.remove(listener);
  }
}
