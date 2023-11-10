/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.actions;

import static com.cburch.draw.Strings.S;

import com.cburch.draw.model.CanvasModel;
import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.model.ReorderRequest;
import com.cburch.draw.util.ZOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ModelReorderAction extends ModelAction {
  private final List<ReorderRequest> requests;
  private final List<CanvasObject> objects;
  private final int type;

  public ModelReorderAction(CanvasModel model, List<ReorderRequest> requests) {
    super(model);
    this.requests = new ArrayList<>(requests);
    this.objects = new ArrayList<>(requests.size());
    for (final com.cburch.draw.model.ReorderRequest req : requests) {
      objects.add(req.getObject());
    }
    int typeIndex = 0; // 0 = mixed/unknown, -1 = to greater index, 1 = to
    // smaller index
    for (final com.cburch.draw.model.ReorderRequest req : requests) {
      final int from = req.getFromIndex();
      final int to = req.getToIndex();
      final int thisType = Integer.compare(to, from);
      if (typeIndex == 2) {
        typeIndex = thisType;
      } else if (typeIndex != thisType) {
        typeIndex = 0;
        break;
      }
    }
    this.type = typeIndex;
  }

  public static ModelReorderAction createLower(
      CanvasModel model, Collection<? extends CanvasObject> objects) {
    final java.util.ArrayList<com.cburch.draw.model.ReorderRequest> reqs = new ArrayList<ReorderRequest>();
    final java.util.Map<com.cburch.draw.model.CanvasObject,java.lang.Integer> zmap = ZOrder.getZIndex(objects, model);
    for (final java.util.Map.Entry<com.cburch.draw.model.CanvasObject,java.lang.Integer> entry : zmap.entrySet()) {
      final com.cburch.draw.model.CanvasObject obj = entry.getKey();
      final java.lang.Integer from = entry.getValue();
      final com.cburch.draw.model.CanvasObject above = ZOrder.getObjectBelow(obj, model, objects);
      if (above != null) {
        int to = ZOrder.getZIndex(above, model);
        if (objects.contains(above)) {
          to++;
        }
        reqs.add(new ReorderRequest(obj, from, to));
      }
    }
    if (reqs.isEmpty()) return null;

    reqs.sort(ReorderRequest.ASCENDING_FROM);
    repairRequests(reqs);
    return new ModelReorderAction(model, reqs);
  }

  public static ModelReorderAction createLowerBottom(
      CanvasModel model, Collection<? extends CanvasObject> objects) {
    final java.util.ArrayList<com.cburch.draw.model.ReorderRequest> reqs = new ArrayList<ReorderRequest>();
    final java.util.Map<com.cburch.draw.model.CanvasObject,java.lang.Integer> zmap = ZOrder.getZIndex(objects, model);
    int to = 0;
    for (final java.util.Map.Entry<com.cburch.draw.model.CanvasObject,java.lang.Integer> entry : zmap.entrySet()) {
      final com.cburch.draw.model.CanvasObject obj = entry.getKey();
      final java.lang.Integer from = entry.getValue();
      reqs.add(new ReorderRequest(obj, from, to));
    }
    if (reqs.isEmpty()) return null;

    reqs.sort(ReorderRequest.ASCENDING_FROM);
    repairRequests(reqs);
    return new ModelReorderAction(model, reqs);
  }

  public static ModelReorderAction createRaise(
      CanvasModel model, Collection<? extends CanvasObject> objects) {
    final java.util.ArrayList<com.cburch.draw.model.ReorderRequest> reqs = new ArrayList<ReorderRequest>();
    final java.util.Map<com.cburch.draw.model.CanvasObject,java.lang.Integer> zmap = ZOrder.getZIndex(objects, model);
    for (final java.util.Map.Entry<com.cburch.draw.model.CanvasObject,java.lang.Integer> entry : zmap.entrySet()) {
      final com.cburch.draw.model.CanvasObject obj = entry.getKey();
      final java.lang.Integer from = entry.getValue();
      final com.cburch.draw.model.CanvasObject above = ZOrder.getObjectAbove(obj, model, objects);
      if (above != null) {
        int to = ZOrder.getZIndex(above, model);
        if (objects.contains(above)) {
          to--;
        }
        reqs.add(new ReorderRequest(obj, from, to));
      }
    }
    if (reqs.isEmpty()) return null;

    reqs.sort(ReorderRequest.DESCENDING_FROM);
    repairRequests(reqs);
    return new ModelReorderAction(model, reqs);
  }

  public static ModelReorderAction createRaiseTop(
      CanvasModel model, Collection<? extends CanvasObject> objects) {
    final java.util.ArrayList<com.cburch.draw.model.ReorderRequest> reqs = new ArrayList<ReorderRequest>();
    final java.util.Map<com.cburch.draw.model.CanvasObject,java.lang.Integer> zmap = ZOrder.getZIndex(objects, model);

    final int to = model.getObjectsFromBottom().size() - 1;
    for (final java.util.Map.Entry<com.cburch.draw.model.CanvasObject,java.lang.Integer> entry : zmap.entrySet()) {
      final com.cburch.draw.model.CanvasObject obj = entry.getKey();
      final java.lang.Integer from = entry.getValue();
      reqs.add(new ReorderRequest(obj, from, to));
    }
    if (reqs.isEmpty()) return null;

    reqs.sort(ReorderRequest.ASCENDING_FROM);
    repairRequests(reqs);
    return new ModelReorderAction(model, reqs);
  }

  private static void repairRequests(List<ReorderRequest> reqs) {
    for (int i = 0, n = reqs.size(); i < n; i++) {
      final com.cburch.draw.model.ReorderRequest req = reqs.get(i);
      int from = req.getFromIndex();
      int to = req.getToIndex();
      for (int j = 0; j < i; j++) {
        final com.cburch.draw.model.ReorderRequest prev = reqs.get(j);
        final int prevFrom = prev.getFromIndex();
        final int prevTo = prev.getToIndex();
        if (prevFrom <= from && from < prevTo) {
          from--;
        } else if (prevTo <= from && from < prevFrom) {
          from++;
        }
        if (prevFrom <= to && to < prevTo) {
          to--;
        } else if (prevTo <= to && to < prevFrom) {
          to++;
        }
      }
      if (from != req.getFromIndex() || to != req.getToIndex()) {
        reqs.set(i, new ReorderRequest(req.getObject(), from, to));
      }
    }
    for (int i = reqs.size() - 1; i >= 0; i--) {
      final com.cburch.draw.model.ReorderRequest req = reqs.get(i);
      if (req.getFromIndex() == req.getToIndex()) {
        reqs.remove(i);
      }
    }
  }

  @Override
  void doSub(CanvasModel model) {
    model.reorderObjects(requests);
  }

  @Override
  public String getName() {
    if (type < 0) {
      return S.get("actionRaise", getShapesName(objects));
    } else if (type > 0) {
      return S.get("actionLower", getShapesName(objects));
    } else {
      return S.get("actionReorder", getShapesName(objects));
    }
  }

  @Override
  public Collection<CanvasObject> getObjects() {
    return objects;
  }

  public List<ReorderRequest> getReorderRequests() {
    return Collections.unmodifiableList(requests);
  }

  @Override
  void undoSub(CanvasModel model) {
    final java.util.ArrayList<com.cburch.draw.model.ReorderRequest> inv = new ArrayList<ReorderRequest>(requests.size());
    for (int i = requests.size() - 1; i >= 0; i--) {
      final com.cburch.draw.model.ReorderRequest request = requests.get(i);
      inv.add(
          new ReorderRequest(request.getObject(), request.getToIndex(), request.getFromIndex()));
    }
    model.reorderObjects(inv);
  }
}
