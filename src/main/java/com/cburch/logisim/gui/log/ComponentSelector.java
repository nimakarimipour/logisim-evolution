/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.util.CollectionUtil;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

// This is more like a JTree, but wedged into a JTable because it looks more
// reasonable sitting next to the SelectionList JTable .
public class ComponentSelector extends JTable {
  private static final long serialVersionUID = 1L;

  static final Comparator<Component> compareComponents =
      (a, b) -> {
        final java.lang.String nameA = a.getFactory().getDisplayName();
        final java.lang.String nameB = b.getFactory().getDisplayName();
        final int ret = nameA.compareToIgnoreCase(nameB);
        if (ret != 0) return ret;
        return a.getLocation().toString().compareTo(b.getLocation().toString());
      };

  static final Comparator<Object> compareNames =
      (a, b) -> a.toString().compareToIgnoreCase(b.toString());

  static class TableTreeModel extends AbstractTableModel {
    TreeNode<CircuitNode> root;
    final ArrayList<TreeNode<?>> rows = new ArrayList<>();

    TableTreeModel() {}

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public Object getValueAt(int row, int column) {
      return rows.get(row);
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return TreeNode.class;
    }

    @Override
    public String getColumnName(int column) {
      return "";
    }

    void toggleExpand(int row) {
      if (row < 0 || row >= rows.size()) return;
      final com.cburch.logisim.gui.log.ComponentSelector.TreeNode<?> o = rows.get(row);
      final int n = o.children.size();
      if (n == 0) return;
      if (o.expanded) {
        for (int i = 0; i < n; i++) removeAll(row + 1);
        o.expanded = false;
      } else {
        for (int i = n - 1; i >= 0; i--) insertAll(row + 1, o.children.get(i));
        o.expanded = true;
      }
      super.fireTableDataChanged(); // overkill, but works
    }

    void removeAll(int row) {
      final com.cburch.logisim.gui.log.ComponentSelector.TreeNode<?> item = rows.remove(row);
      if (item.expanded) {
        final int n = item.children.size();
        for (int i = 0; i < n; i++) removeAll(row);
      }
    }

    void insertAll(int row, TreeNode<?> item) {
      rows.add(row, item);
      if (item.expanded) {
        final int n = item.children.size();
        for (int i = n - 1; i >= 0; i--) insertAll(row + 1, item.children.get(i));
      }
    }

    @Override
    public void fireTableDataChanged() {
      setRoot(root);
    }

    void setRoot(TreeNode<CircuitNode> r) {
      root = r;
      rows.clear();
      final int n = root == null ? 0 : root.children.size();
      for (int i = n - 1; i >= 0; i--) insertAll(0, root.children.get(i));
      super.fireTableDataChanged();
    }
  }

  // TreeNode
  //   ComponentNode (e.g. a Pin or Button, or an expandable Ram placeholder)
  //   CircuitNode
  //   OptionNode (e.g. one location in a Ram component)

  private static class TreeNode<P extends TreeNode<?>> {
    final P parent;
    final int depth;
    boolean expanded;
    ArrayList<TreeNode<?>> children = new ArrayList<>();

    TreeNode(P p) {
      parent = p;
      depth = (parent == null ? 0 : parent.depth + 1);
    }

    void addChild(TreeNode<?> child) {
      children.add(child);
    }
  }

  private class ComponentNode extends TreeNode<CircuitNode> {

    final Component comp;

    public ComponentNode(CircuitNode p, Component c) {
      super(p);
      comp = c;

      final com.cburch.logisim.gui.log.LoggableContract log = (LoggableContract) comp.getFeature(LoggableContract.class);
      if (log == null) return;
      final java.lang.Object[] opts = log.getLogOptions();
      if (opts == null) return;
      for (Object opt : opts) addChild(new OptionNode(this, opt));
    }

    @Override
    public String toString() {
      final com.cburch.logisim.gui.log.LoggableContract log = (LoggableContract) comp.getFeature(LoggableContract.class);
      if (log != null) {
        final java.lang.String ret = log.getLogName(null);
        if (ret != null && !ret.equals("")) return ret;
      }
      return comp.getFactory().getDisplayName() + " " + comp.getLocation();
    }
  }

  private class CircuitNode extends TreeNode<CircuitNode> implements CircuitListener {

    final Circuit circ;
    final Component comp;

    public CircuitNode(CircuitNode p, Circuit t, Component c) {
      super(p);
      circ = t;
      comp = c;
      circ.addCircuitListener(this);
      computeChildren();
    }

    @Override
    public void circuitChanged(CircuitEvent event) {
      int action = event.getAction();
      if (action == CircuitEvent.ACTION_SET_NAME)
        tableModel.fireTableDataChanged(); // overkill, but works
      else if (computeChildren()) tableModel.fireTableDataChanged(); // overkill, but works
      else if (action == CircuitEvent.ACTION_INVALIDATE)
        tableModel.fireTableDataChanged(); // overkill, but works
    }

    private ComponentNode findChildFor(Component c) {
      for (final com.cburch.logisim.gui.log.ComponentSelector.TreeNode<?> o : children) {
        if (o instanceof ComponentNode child) {
          if (child.comp == c) return child;
        }
      }
      return null;
    }

    private CircuitNode findChildFor(Circuit c) {
      for (TreeNode<?> o : children) {
        if (o instanceof CircuitNode child) {
          if (child.circ == c) return child;
        }
      }
      return null;
    }

    private boolean computeChildren() { // returns true if changed
      final java.util.ArrayList<com.cburch.logisim.gui.log.ComponentSelector.TreeNode<?>> newChildren = new ArrayList<TreeNode<?>>();
      final java.util.ArrayList<com.cburch.logisim.comp.Component> subcircs = new ArrayList<Component>();
      boolean changed = false;
      // TODO: hide from display any unselectable things that also have no children
      for (final com.cburch.logisim.comp.Component c : circ.getNonWires()) {
        // For DRIVEABLE_CLOCKS do not recurse into subcircuits
        if (c.getFactory() instanceof SubcircuitFactory && mode != DRIVEABLE_CLOCKS) {
          subcircs.add(c);
          continue;
        }
        final com.cburch.logisim.gui.log.LoggableContract log = (LoggableContract) c.getFeature(LoggableContract.class);
        if (log == null) continue;
        com.cburch.logisim.data.BitWidth bitWidth = log.getBitWidth(null);
        if (bitWidth == null) bitWidth = c.getAttributeSet().getValue(StdAttr.WIDTH);
        final int w = bitWidth.getWidth();
        if (mode != ANY_SIGNAL && w != 1) continue; // signal is too wide to be a used as a clock
        if (mode == DRIVEABLE_CLOCKS) {
          // For now, we only allow input Pins. In principle, we could allow
          // buttons, switches, or any other kind of 1-bit input. Note that we
          // don't bother looking for Clocks here, since this is only used by
          // main simulator when there are no clocks anywhere in the circuit.
          if (!(c.getFactory() instanceof Pin && log.isInput(null))) continue;
        } else if (mode == ACTUAL_CLOCKS) {
          if (!(c.getFactory() instanceof Clock)) continue;
        }
        com.cburch.logisim.gui.log.ComponentSelector.ComponentNode toAdd = findChildFor(c);
        if (toAdd == null) {
          toAdd = new ComponentNode(this, c);
          changed = true;
        }
        newChildren.add(toAdd);
      }
      newChildren.sort(compareNames);
      subcircs.sort(compareComponents);
      for (final com.cburch.logisim.comp.Component c : subcircs) {
        final com.cburch.logisim.circuit.SubcircuitFactory factory = (SubcircuitFactory) c.getFactory();
        final com.cburch.logisim.circuit.Circuit subCircuit = factory.getSubcircuit();
        com.cburch.logisim.gui.log.ComponentSelector.CircuitNode toAdd = findChildFor(subCircuit);
        if (toAdd == null) {
          changed = true;
          toAdd = new CircuitNode(this, subCircuit, c);
        }
        newChildren.add(toAdd);
      }

      changed = changed || !children.equals(newChildren);
      if (changed) children = newChildren;
      return changed;
    }

    @Override
    public String toString() {
      if (comp != null) {
        final java.lang.String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
        if (label != null && !label.equals("")) return label;
      }
      java.lang.String ret = circ.getName();
      if (comp != null) ret += comp.getLocation();
      return ret;
    }
  }

  // OptionNode represents some value in the internal state of a component, e.g.
  // the value inside a shift register stage, or the value at a specific RAM
  // location.
  // TODO: Those are the only two components that have been outfitted for this,
  // apparently.
  // FIXME: And for RAM, the current UI is unworkable unless there are only a
  // very few addresses.
  private class OptionNode extends TreeNode<ComponentNode> {
    private final Object option;

    public OptionNode(ComponentNode p, Object o) {
      super(p);
      option = o;
    }

    @Override
    public String toString() {
      return option.toString();
    }
  }

  private class TreeNodeRenderer extends DefaultTableCellRenderer implements Icon {

    private TreeNode<?> node;

    @Override
    public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value instanceof CircuitNode) isSelected = false;
      final java.awt.Component ret = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (ret instanceof JLabel && value instanceof TreeNode) {
        node = (TreeNode) value;
        ((JLabel) ret).setIcon(this);
      }
      return ret;
    }

    @Override
    public int getIconHeight() {
      return 20;
    }

    @Override
    public int getIconWidth() {
      return 10 * (node.depth - 1) + (needsTriangle() ? 40 : 20);
    }

    boolean needsTriangle() {
      return (node instanceof CircuitNode)
          || (node instanceof ComponentNode && node.children.size() > 0);
    }

    @Override
    public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
      g.setColor(Color.GRAY);
      for (int i = 1; i < node.depth; i++) {
        g.drawLine(x + 5, 0, x + 5, 20);
        x += 10;
      }

      Component comp;
      Object opt = null;
      if (node instanceof ComponentNode compNode) {
        comp = compNode.comp;
      } else if (node instanceof CircuitNode circNode) {
        comp = circNode.comp;
      } else if (node instanceof OptionNode optNode) {
        comp = optNode.parent.comp;
        opt = optNode.option;
      } else {
        return; // null node?
      }

      SignalInfo.paintIcon(comp, opt, c, g, needsTriangle() ? x + 10 : x, y);

      if (!needsTriangle()) return;

      int[] xp;
      int[] yp;
      if (node.expanded) {
        xp = new int[] {x + 0, x + 10, x + 5};
        yp = new int[] {y + 9, y + 9, y + 14};
      } else {
        xp = new int[] {x + 3, x + 3, x + 8};
        yp = new int[] {y + 5, y + 15, y + 10};
      }
      g.setColor(new Color(51, 102, 255));
      g.fillPolygon(xp, yp, 3);
      g.setColor(Color.BLACK);
      g.drawPolygon(xp, yp, 3);
    }
  }

  private Circuit rootCircuit;
  private final TableTreeModel tableModel = new TableTreeModel();
  private final int mode;

  public static final int ANY_SIGNAL = 1;
  public static final int OBSERVEABLE_CLOCKS = 2; // only 1-bit signals (pins, wires, clocks, etc.)
  public static final int DRIVEABLE_CLOCKS = 3; // only top-level 1-bit inputs
  public static final int ACTUAL_CLOCKS = 4; // only clocks

  public ComponentSelector(Circuit circ, int mode) {
    this.mode = mode;
    setRootCircuit(circ);
    setModel(tableModel);
    setDefaultRenderer(TreeNode.class, new TreeNodeRenderer());
    if (mode == ANY_SIGNAL) setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    else setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    getTableHeader().setUI(null);
    setRowHeight(24);
    // setAutoResizeMode(AUTO_RESIZE_OFF);
    setShowGrid(false);
    setFillsViewportHeight(true);
    setDragEnabled(true);
    setDropMode(DropMode.ON_OR_INSERT); // ?
    setTransferHandler(new ComponentTransferHandler());

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            int row = rowAtPoint(e.getPoint());
            int col = columnAtPoint(e.getPoint());
            if (row < 0 || col < 0) return;
            tableModel.toggleExpand(row);
          }
        });
  }

  public SignalInfo.List getSelectedItems() {
    final com.cburch.logisim.gui.log.SignalInfo.List items = new SignalInfo.List();
    final int[] sel = getSelectedRows();
    for (final int i : sel) {
      final com.cburch.logisim.gui.log.ComponentSelector.TreeNode<?> node = tableModel.rows.get(i);
      final com.cburch.logisim.gui.log.SignalInfo item = makeSignalInfo(node);
      if (item != null) items.add(item);
    }

    return (items.size() > 0 ? items : null);
  }

  private SignalInfo makeSignalInfo(TreeNode<?> node) {
    ComponentNode n = null;
    Object opt = null;
    if (node instanceof OptionNode optNode) {
      n = optNode.parent;
      opt = optNode.option;
    } else if (node instanceof ComponentNode compNode) {
      n = compNode;
      if (n.children.size() > 0) return null;
    } else {
      return null;
    }
    int count = 0;
    for (com.cburch.logisim.gui.log.ComponentSelector.CircuitNode cur = n.parent; cur != null; cur = cur.parent) count++;
    final com.cburch.logisim.comp.Component[] paths = new Component[count];
    paths[paths.length - 1] = n.comp;
    com.cburch.logisim.gui.log.ComponentSelector.CircuitNode cur = n.parent;
    for (int j = paths.length - 2; j >= 0; j--) {
      paths[j] = cur.comp;
      cur = cur.parent;
    }
    return new SignalInfo(rootCircuit, paths, opt);
  }

  public void localeChanged() {
    repaint();
  }

  public void setRootCircuit(Circuit circ) {
    if (rootCircuit == circ) return;
    rootCircuit = circ;

    if (rootCircuit == null) {
      tableModel.setRoot(null);
      return;
    }
    tableModel.setRoot(new CircuitNode(null, rootCircuit, null));
  }

  static class ComponentTransferHandler extends TransferHandler {
    private static final long serialVersionUID = 1L;
    boolean sending;

    @Override
    public boolean canImport(TransferHandler.TransferSupport support) {
      return !sending && support.isDataFlavorSupported(SignalInfo.List.dataFlavor);
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      sending = true;
      final com.cburch.logisim.gui.log.ComponentSelector tree = (ComponentSelector) c;
      final com.cburch.logisim.gui.log.SignalInfo.List items = tree.getSelectedItems();
      return CollectionUtil.isNullOrEmpty(items) ? null : items;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
      sending = false;
    }

    @Override
    public int getSourceActions(JComponent c) {
      return COPY;
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport support) {
      sending = false;
      return false;
    }
  }

  private void enumerate(ArrayList<SignalInfo> result, TreeNode<?> node) {
    for (final com.cburch.logisim.gui.log.ComponentSelector.TreeNode<?> child : node.children) {
      final com.cburch.logisim.gui.log.SignalInfo item = makeSignalInfo(child);
      if (item != null) result.add(item);
      enumerate(result, child);
    }
  }

  // Returns empty list if there are no clocks, but there are other suitable
  // observable clocks. Returns null if there are no clocks and nothing suitable
  // as an observable clock.
  public static ArrayList<SignalInfo> findClocks(Circuit circ) {
    com.cburch.logisim.gui.log.ComponentSelector sel = new ComponentSelector(circ, ACTUAL_CLOCKS);
    java.util.ArrayList<com.cburch.logisim.gui.log.SignalInfo> clocks = new ArrayList<SignalInfo>();
    sel.enumerate(clocks, sel.tableModel.root);
    if (clocks.size() > 0) return clocks;
    sel = new ComponentSelector(circ, OBSERVEABLE_CLOCKS);
    sel.enumerate(clocks, sel.tableModel.root);
    if (clocks.size() > 0) clocks.clear();
    else clocks = null;
    return clocks;
  }
}
