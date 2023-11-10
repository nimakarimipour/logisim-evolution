/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.appear;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.draw.actions.ModelAddAction;
import com.cburch.draw.actions.ModelRemoveAction;
import com.cburch.draw.model.CanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.appear.AppearanceAnchor;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.StdAttr;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.scijava.swing.checkboxtree.CheckBoxNodeData;

public class ShowStateDialog extends JDialog implements ActionListener {
  private static final long serialVersionUID = 1L;
  final JButton ok;
  final JButton cancel;
  RefTreeNode root;
  final CheckBoxTree tree;
  final AppearanceCanvas canvas;

  public ShowStateDialog(JFrame parent, AppearanceCanvas canvas) {
    super(parent, true);
    this.canvas = canvas;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    final com.cburch.logisim.circuit.Circuit circuit = canvas.getCircuit();
    setTitle(S.get("showStateDialogTitle", circuit.getName()));

    root = enumerate(circuit, null);
    if (root == null) {
      root = new RefTreeNode(S.get("showStateDialogEmptyNode", circuit.getName()));
    }
    tree = new CheckBoxTree(root);
    tree.setCheckingPaths(getPaths());
    final javax.swing.JScrollPane infoPane = new JScrollPane(tree);

    ok = new JButton(S.get("showStateDialogOkButton"));
    cancel = new JButton(S.get("showStateDialogCancelButton"));
    ok.addActionListener(this);
    cancel.addActionListener(this);
    final javax.swing.JPanel buttonPanel = new JPanel();
    buttonPanel.add(ok);
    buttonPanel.add(cancel);

    final java.awt.Container contents = this.getContentPane();
    contents.setLayout(new BorderLayout());
    contents.add(infoPane, BorderLayout.CENTER);
    contents.add(buttonPanel, BorderLayout.PAGE_END);
    this.pack();

    final java.awt.Dimension pref = contents.getPreferredSize();
    if (pref.width > 750 || pref.height > 550 || pref.width < 200 || pref.height < 150) {
      if (pref.width > 750) pref.width = 750;
      else if (pref.width < 200) pref.width = 200;
      if (pref.height > 550) pref.height = 550;
      else if (pref.height < 200) pref.height = 200;
      this.setSize(pref);
    }
  }

  private static void pickPlacement(List<CanvasObject> avoid, DynamicElement shape, Bounds bbox) {
    while (badPosition(avoid, shape)) {
      // move down
      shape.translate(0, 10);
      final com.cburch.logisim.data.Location loc = shape.getLocation();
      if (loc.getX() < bbox.getX() + bbox.getWidth()
          && loc.getY() + shape.getBounds().getHeight() >= bbox.getY() + bbox.getHeight())
        // if we are below the bounding box, move right and up
        shape.translate(10, (bbox.getY() + 9) / 10 * 10 - loc.getY());
    }
  }

  private static boolean badPosition(List<CanvasObject> avoid, CanvasObject shape) {
    for (final com.cburch.draw.model.CanvasObject s : avoid) {
      if (shape.overlaps(s) || s.overlaps(shape)) return true;
    }
    return false;
  }

  private static DynamicElement.Path toComponentPath(TreePath p) {
    final java.lang.Object[] o = p.getPath();
    final com.cburch.logisim.instance.InstanceComponent[] elt = new InstanceComponent[o.length - 1];
    for (int i = 1; i < o.length; i++) {
      final com.cburch.logisim.gui.appear.ShowStateDialog.Ref r = ((RefTreeNode) o[i]).refData;
      elt[i - 1] = r.ic;
    }
    return new DynamicElement.Path(elt);
  }

  private static TreePath toTreePath(RefTreeNode root, DynamicElement.Path path) {
    final java.lang.Object[] objs = new Object[path.elt.length + 1];
    objs[0] = root;
    for (int i = 1; i < objs.length; i++) {
      objs[i] = findChild((RefTreeNode) objs[i - 1], path.elt[i - 1]);
      if (objs[i] == null) return null;
    }
    return new TreePath(objs);
  }

  private static RefTreeNode findChild(RefTreeNode node, InstanceComponent ic) {
    for (int i = 0; i < node.getChildCount(); i++) {
      final com.cburch.logisim.gui.appear.ShowStateDialog.RefTreeNode child = (RefTreeNode) node.getChildAt(i);
      final com.cburch.logisim.gui.appear.ShowStateDialog.Ref r = child.refData;
      if (r.ic.getLocation().equals(ic.getLocation())
          && r.ic.getFactory().getName().equals(ic.getFactory().getName())) return child;
    }
    return null;
  }

  private TreePath[] getPaths() {
    final com.cburch.logisim.gui.appear.ShowStateDialog.RefTreeNode root = (RefTreeNode) tree.getModel().getRoot();
    final java.util.ArrayList<javax.swing.tree.TreePath> paths = new ArrayList<TreePath>();
    for (final com.cburch.draw.model.CanvasObject shape : canvas.getModel().getObjectsFromBottom()) {
      if (!(shape instanceof DynamicElement dynEl)) continue;
      final javax.swing.tree.TreePath path = toTreePath(root, dynEl.getPath());
      paths.add(path);
    }
    return paths.toArray(new TreePath[0]);
  }

  private void apply() {
    final com.cburch.draw.model.CanvasModel model = canvas.getModel();
    final com.cburch.logisim.gui.appear.ShowStateDialog.RefTreeNode root = (RefTreeNode) tree.getModel().getRoot();

    com.cburch.logisim.data.Bounds boundingBox = Bounds.EMPTY_BOUNDS;
    for (final com.cburch.draw.model.CanvasObject shape : model.getObjectsFromBottom()) {
      boundingBox = boundingBox.add(shape.getBounds());
    }
    com.cburch.logisim.data.Location loc = Location.create(boundingBox.getX(), boundingBox.getY(), true);

    // TreePath[] roots = tree.getCheckingRoots();
    final javax.swing.tree.TreePath[] checked = tree.getCheckingPaths();
    final java.util.ArrayList<javax.swing.tree.TreePath> toAdd = new ArrayList<>(Arrays.asList(checked));

    // Remove existing dynamic objects that are no longer checked.
    final java.util.ArrayList<com.cburch.draw.model.CanvasObject> toRemove = new ArrayList<CanvasObject>();
    for (final com.cburch.draw.model.CanvasObject shape : model.getObjectsFromBottom()) {
      if (!(shape instanceof DynamicElement dynEl)) continue;
      final javax.swing.tree.TreePath path = toTreePath(root, dynEl.getPath());
      if (path != null && tree.isPathChecked(path)) {
        toAdd.remove(path); // already present, don't need to add it again
      } else {
        toRemove.add(shape); // no longer checked, or invalid
      }
    }

    boolean dirty = true;
    if (toRemove.size() > 0) {
      canvas.doAction(new ModelRemoveAction(model, toRemove));
      dirty = true;
    }

    // sort the remaining shapes
    toAdd.sort(new CompareByLocations());

    final java.util.ArrayList<com.cburch.draw.model.CanvasObject> avoid = new ArrayList<>(model.getObjectsFromBottom());
    for (int i = avoid.size() - 1; i >= 0; i--) {
      if (avoid.get(i) instanceof AppearanceAnchor) avoid.remove(i);
    }
    final java.util.ArrayList<com.cburch.draw.model.CanvasObject> newShapes = new ArrayList<CanvasObject>();

    for (final javax.swing.tree.TreePath path : toAdd) {
      final com.cburch.logisim.gui.appear.ShowStateDialog.RefTreeNode node = (RefTreeNode) path.getLastPathComponent();
      final com.cburch.logisim.gui.appear.ShowStateDialog.Ref ref = node.refData;
      if (ref instanceof CircuitRef) continue;
      final com.cburch.logisim.comp.ComponentFactory factory = ref.ic.getFactory();
      if (factory instanceof DynamicElementProvider) {
        final int x = loc.getX();
        final int y = loc.getY();
        final com.cburch.logisim.circuit.appear.DynamicElement.Path p = toComponentPath(path);
        final com.cburch.logisim.circuit.appear.DynamicElement shape = ((DynamicElementProvider) factory).createDynamicElement(x, y, p);
        pickPlacement(avoid, shape, boundingBox);
        loc = shape.getLocation();
        avoid.add(shape);
        newShapes.add(shape);
      }
    }
    if (newShapes.size() > 0) {
      canvas.doAction(new ModelAddAction(model, newShapes));
      dirty = true;
    }
    if (dirty) canvas.repaint();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Object src = e.getSource();
    if (src == ok) {
      apply();
    }
    this.dispose();
  }

  private RefTreeNode enumerate(Circuit circuit, InstanceComponent ic) {
    final com.cburch.logisim.gui.appear.ShowStateDialog.RefTreeNode root = new RefTreeNode(new CircuitRef(circuit, ic));
    for (final com.cburch.logisim.comp.Component c : circuit.getNonWires()) {
      if (c instanceof InstanceComponent child) {
        final com.cburch.logisim.comp.ComponentFactory factory = child.getFactory();
        if (factory instanceof DynamicElementProvider) {
          root.add(new RefTreeNode(new Ref(child)));
        } else if (factory instanceof SubcircuitFactory sub) {
          final com.cburch.logisim.gui.appear.ShowStateDialog.RefTreeNode node = enumerate(sub.getSubcircuit(), child);
          if (node != null) root.add(node);
        }
      }
    }
    return (root.getChildCount() == 0) ? null : root;
  }

  private static class CompareByLocations implements Comparator<TreePath> {
    @Override
    public int compare(TreePath a, TreePath b) {
      final java.lang.Object[] aa = a.getPath();
      final java.lang.Object[] bb = b.getPath();
      for (int i = 1; i < aa.length && i < bb.length; i++) {
        final com.cburch.logisim.gui.appear.ShowStateDialog.Ref refA = ((RefTreeNode) aa[i]).refData;
        final com.cburch.logisim.gui.appear.ShowStateDialog.Ref refB = ((RefTreeNode) bb[i]).refData;
        final com.cburch.logisim.data.Location locA = refA.ic.getLocation();
        final com.cburch.logisim.data.Location locB = refB.ic.getLocation();
        int diff = locA.compareTo(locB);
        if (diff != 0) return diff;
      }
      return 0;
    }
  }

  private static class Ref {
    final InstanceComponent ic;

    Ref(InstanceComponent ic) {
      this.ic = ic;
    }

    @Override
    public String toString() {
      final java.lang.String s = ic.getInstance().getAttributeValue(StdAttr.LABEL);
      final com.cburch.logisim.data.Location loc = ic.getInstance().getLocation();
      java.lang.String str = "";

      if (s != null && s.length() > 0) str += "\"" + s + "\" ";  // mind trailing space!
      str += String.format("%s @ (%d, %d)", ic.getFactory(), loc.getX(), loc.getY());

      return str;
    }
  }

  private static class CircuitRef extends Ref {
    final Circuit circuit;

    CircuitRef(Circuit c, InstanceComponent ic) {
      super(ic);
      this.circuit = c;
    }

    @Override
    public String toString() {
      return (ic == null) ? S.get("showStateDialogNodeTitle", circuit.getName()) : super.toString();
    }
  }

  private static class RefTreeNode extends DefaultMutableTreeNode {
    final Ref refData;

    RefTreeNode(Object data) {
      super(new CheckBoxNodeData(data.toString(), false));
      refData = (data instanceof Ref ref) ? ref : null;
    }
  }
}
