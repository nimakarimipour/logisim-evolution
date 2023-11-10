/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.model;

import com.cburch.logisim.util.CollectionUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DrawingOverlaps {
  private final Map<CanvasObject, List<CanvasObject>> map;
  private final Set<CanvasObject> untested;

  public DrawingOverlaps() {
    map = new HashMap<>();
    untested = new HashSet<>();
  }

  private void addOverlap(CanvasObject a, CanvasObject b) {
    final java.util.List<com.cburch.draw.model.CanvasObject> alist = map.computeIfAbsent(a, k -> new ArrayList<>());
    if (!alist.contains(b)) {
      alist.add(b);
    }
  }

  public void addShape(CanvasObject shape) {
    untested.add(shape);
  }

  private void ensureUpdated() {
    for (final com.cburch.draw.model.CanvasObject o : untested) {
      final java.util.ArrayList<com.cburch.draw.model.CanvasObject> over = new ArrayList<CanvasObject>();
      for (final com.cburch.draw.model.CanvasObject o2 : map.keySet()) {
        if (o != o2 && o.overlaps(o2)) {
          over.add(o2);
          addOverlap(o2, o);
        }
      }
      map.put(o, over);
    }
    untested.clear();
  }

  public Collection<CanvasObject> getObjectsOverlapping(CanvasObject o) {
    ensureUpdated();

    final java.util.List<com.cburch.draw.model.CanvasObject> ret = map.get(o);
    return CollectionUtil.isNullOrEmpty(ret)
        ? Collections.emptyList()
        : Collections.unmodifiableList(ret);
  }

  public void invalidateShape(CanvasObject shape) {
    removeShape(shape);
    untested.add(shape);
  }

  public void invalidateShapes(Collection<? extends CanvasObject> shapes) {
    for (final com.cburch.draw.model.CanvasObject o : shapes) {
      invalidateShape(o);
    }
  }

  public void removeShape(CanvasObject shape) {
    untested.remove(shape);
    final java.util.List<com.cburch.draw.model.CanvasObject> mapped = map.remove(shape);
    if (mapped != null) {
      for (final com.cburch.draw.model.CanvasObject o : mapped) {
        final java.util.List<com.cburch.draw.model.CanvasObject> reverse = map.get(o);
        if (reverse != null) {
          reverse.remove(shape);
        }
      }
    }
  }
}
