/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

import com.cburch.logisim.prefs.AppPreferences;
import java.util.Hashtable;
import javax.swing.JLabel;
import javax.swing.JSlider;

public class ZoomSlider extends JSlider {

  private int zoomMin;
  private int zoomMax;

  public int getMaxZoom() {
    return zoomMax;
  }

  public int getMinZoom() {
    return zoomMin;
  }

  public ZoomSlider(int orientation, int min, int max, int value) {
    setup(orientation, min, max, value);
  }

  public ZoomSlider() {
    setup(JSlider.HORIZONTAL, 100, 200, 100);
  }

  private void setup(int orientation, int min, int max, int value) {
    zoomMin = min;
    zoomMax = max;
    final int midValue = min + ((max - min) >> 1);
    super.setOrientation(orientation);
    super.setMinimum(min);
    super.setMaximum(max);
    super.setValue(value);
    final java.awt.Dimension orig = super.getSize();
    orig.height = AppPreferences.getScaled(orig.height);
    orig.width = AppPreferences.getScaled(orig.width);
    super.setSize(orig);
    setMajorTickSpacing(50);
    setMinorTickSpacing(10);
    setPaintTicks(true);
    final java.util.Hashtable<java.lang.Integer,javax.swing.JLabel> labelTable = new Hashtable<Integer, JLabel>();
    javax.swing.JLabel label = new JLabel(getId(min));
    label.setFont(AppPreferences.getScaledFont(label.getFont()));
    labelTable.put(min, label);
    label = new JLabel(getId(midValue));
    label.setFont(AppPreferences.getScaledFont(label.getFont()));
    labelTable.put(midValue, label);
    label = new JLabel(getId(max));
    label.setFont(AppPreferences.getScaledFont(label.getFont()));
    labelTable.put(max, label);
    setLabelTable(labelTable);
    setPaintLabels(true);
  }

  private String getId(int value) {
    final int hun = value / 100;
    final int tens = (value % 100) / 10;
    return String.format("%d.%dx", hun, tens);
  }
}
