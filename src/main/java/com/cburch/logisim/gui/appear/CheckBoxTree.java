/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.appear;

import java.util.ArrayList;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.scijava.swing.checkboxtree.CheckBoxNodeData;
import org.scijava.swing.checkboxtree.CheckBoxNodeEditor;
import org.scijava.swing.checkboxtree.CheckBoxNodeRenderer;

public class CheckBoxTree extends JTree {

  public CheckBoxTree(DefaultMutableTreeNode root) {
    super(root);
    final org.scijava.swing.checkboxtree.CheckBoxNodeRenderer renderer = new CheckBoxNodeRenderer();
    this.setCellRenderer(renderer);
    this.setCellEditor(new CheckBoxNodeEditor(this));
    this.setEditable(true);

    getModel()
        .addTreeModelListener(
            new TreeModelListener() {
              @Override
              public void treeNodesChanged(TreeModelEvent e) {
                nodeModified(e.getTreePath(), e.getChildIndices());
              }

              @Override
              public void treeStructureChanged(TreeModelEvent e) {}

              @Override
              public void treeNodesRemoved(TreeModelEvent e) {}

              @Override
              public void treeNodesInserted(TreeModelEvent e) {}
            });
  }

  private void nodeModified(TreePath parentPath, int[] changedChildren) {
    javax.swing.tree.DefaultMutableTreeNode parent = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
    DefaultMutableTreeNode node;
    if (changedChildren == null) {
      node = parent;
      parent = null;
    } else {
      node = (DefaultMutableTreeNode) parent.getChildAt(changedChildren[0]);
    }
    boolean checked = ((CheckBoxNodeData) node.getUserObject()).isChecked();
    markDescendents(node, checked);
    for (DefaultMutableTreeNode p = parent; p != null; p = (DefaultMutableTreeNode) p.getParent()) {
      adjustParentForChildrenValues(p);
    }
  }

  private void markDescendents(DefaultMutableTreeNode node, boolean checked) {
    for (final java.util.Enumeration<javax.swing.tree.TreeNode> e = node.depthFirstEnumeration(); e.hasMoreElements(); ) {
      final javax.swing.tree.DefaultMutableTreeNode n = (DefaultMutableTreeNode) (e.nextElement());
      ((CheckBoxNodeData) (n.getUserObject())).setChecked(checked);
    }
  }

  private void adjustParentForChildrenValues(DefaultMutableTreeNode parent) {
    boolean foundCheck = false;
    for (final java.util.Enumeration<javax.swing.tree.TreeNode> e = parent.children(); !foundCheck && e.hasMoreElements(); ) {
      final javax.swing.tree.DefaultMutableTreeNode child = (DefaultMutableTreeNode) (e.nextElement());
      foundCheck = (((CheckBoxNodeData) (child.getUserObject())).isChecked());
    }
    ((CheckBoxNodeData) parent.getUserObject()).setChecked(foundCheck);
  }

  private void adjustParentsInTree(DefaultMutableTreeNode root) {
    for (final java.util.Enumeration<javax.swing.tree.TreeNode> e = root.postorderEnumeration(); e.hasMoreElements(); ) {
      final javax.swing.tree.DefaultMutableTreeNode node = (DefaultMutableTreeNode) (e.nextElement());
      if (!node.isLeaf()) {
        adjustParentForChildrenValues(node);
      }
    }
  }

  public void setCheckingPaths(TreePath[] paths) {
    for (final javax.swing.tree.TreePath path : paths) {
      final javax.swing.tree.DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) (path.getLastPathComponent());
      ((CheckBoxNodeData) (treeNode.getUserObject())).setChecked(true);
    }
    adjustParentsInTree((DefaultMutableTreeNode) getModel().getRoot());
  }

  public TreePath[] getCheckingPaths() {
    final java.util.ArrayList<javax.swing.tree.TreePath> paths = new ArrayList<TreePath>();
    final javax.swing.tree.DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
    for (final java.util.Enumeration<javax.swing.tree.TreeNode> e = root.preorderEnumeration(); e.hasMoreElements(); ) {
      final javax.swing.tree.DefaultMutableTreeNode n = (DefaultMutableTreeNode) (e.nextElement());
      if (((CheckBoxNodeData) (n.getUserObject())).isChecked()) {
        paths.add(new TreePath(n.getPath()));
      }
    }
    return paths.toArray(new TreePath[0]);
  }

  public boolean isPathChecked(TreePath path) {
    final javax.swing.tree.DefaultMutableTreeNode n = (DefaultMutableTreeNode) (path.getLastPathComponent());
    return ((CheckBoxNodeData) (n.getUserObject())).isChecked();
  }
}
