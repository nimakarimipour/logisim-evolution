/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.contracts.BaseMouseListenerContract;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.gui.icons.ArithmeticIcon;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import javax.swing.JLabel;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

public class Rom extends Mem {
  /**
   * Unique identifier of the tool, used as reference in project files.
   * Do NOT change as it will prevent project files from loading.
   *
   * Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "ROM";

  static class ContentsAttribute extends Attribute<MemContents> {
    public ContentsAttribute() {
      super("contents", S.getter("romContentsAttr"));
    }

    @Override
    public java.awt.Component getCellEditor(Window source, MemContents value) {
      if (source instanceof Frame frame) {
        final com.cburch.logisim.proj.Project proj = frame.getProject();
        RomAttributes.register(value, proj);
      }
      final com.cburch.logisim.std.memory.Rom.ContentsCell ret = new ContentsCell(source, value);
      ret.mouseClicked(null);
      return ret;
    }

    @Override
    public MemContents parse(String value) {
      final int lineBreak = value.indexOf('\n');
      final java.lang.String first = lineBreak < 0 ? value : value.substring(0, lineBreak);
      final java.lang.String rest = lineBreak < 0 ? "" : value.substring(lineBreak + 1);
      final java.util.StringTokenizer toks = new StringTokenizer(first);
      try {
        final java.lang.String header = toks.nextToken();
        if (!header.equals("addr/data:")) return null;
        final int addr = Integer.parseInt(toks.nextToken());
        final int data = Integer.parseInt(toks.nextToken());
        return HexFile.parseFromCircFile(rest, addr, data);
      } catch (IOException | NoSuchElementException | NumberFormatException e) {
        return null;
      }
    }

    @Override
    public String toDisplayString(MemContents value) {
      return S.get("romContentsValue");
    }

    @Override
    public String toStandardString(MemContents state) {
      final int addr = state.getLogLength();
      final int data = state.getWidth();
      final java.lang.String contents = HexFile.saveToString(state);
      return "addr/data: " + addr + " " + data + "\n" + contents;
    }
  }

  @SuppressWarnings("serial")
  private static class ContentsCell extends JLabel implements BaseMouseListenerContract {
    final Window source;
    final MemContents contents;

    ContentsCell(Window source, MemContents contents) {
      super(S.get("romContentsValue"));
      this.source = source;
      this.contents = contents;
      addMouseListener(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (contents == null) return;
      final com.cburch.logisim.proj.Project proj = (source instanceof Frame frame) ? frame.getProject() : null;
      final com.cburch.logisim.gui.hex.HexFrame frame = RomAttributes.getHexFrame(contents, proj, null);
      frame.setVisible(true);
      frame.toFront();
    }
  }

  public static final Attribute<MemContents> CONTENTS_ATTR = new ContentsAttribute();

  // The following is so that instance's MemListeners aren't freed by the
  // garbage collector until the instance itself is ready to be freed.
  private final WeakHashMap<Instance, MemListener> memListeners;

  public Rom() {
    super(_ID, S.getter("romComponent"), 0, new RomHdlGeneratorFactory(), true);
    setIcon(new ArithmeticIcon("ROM", 3));
    memListeners = new WeakHashMap<>();
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    super.configureNewInstance(instance);
    final com.cburch.logisim.std.memory.MemContents contents = getMemContents(instance);
    final com.cburch.logisim.std.memory.Mem.MemListener listener = new MemListener(instance);
    memListeners.put(instance, listener);
    contents.addHexModelListener(listener);
    instance.addAttributeListener();
  }

  @Override
  void configurePorts(Instance instance) {
    RamAppearance.configurePorts(instance);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new RomAttributes();
  }

  @Override
  HexFrame getHexFrame(Project proj, Instance instance, CircuitState state) {
    return RomAttributes.getHexFrame(getMemContents(instance), proj, instance);
  }

  public static MemContents getMemContents(Instance instance) {
    return instance.getAttributeValue(CONTENTS_ATTR);
  }

  public static void closeHexFrame(Component c) {
    if (!(c instanceof InstanceComponent)) return;
    final com.cburch.logisim.instance.Instance inst = ((InstanceComponent) c).getInstance();
    RomAttributes.closeHexFrame(getMemContents(inst));
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    final int len = attrs.getValue(Mem.DATA_ATTR).getWidth();
    if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      return Bounds.create(0, 0, SymbolWidth + 40, 140);
    } else {
      return Bounds.create(0, 0, SymbolWidth + 40, RamAppearance.getControlHeight(attrs) + 20 * len);
    }
  }

  @Override
  MemState getState(Instance instance, CircuitState state) {
    com.cburch.logisim.std.memory.MemState ret = (MemState) instance.getData(state);
    if (ret == null) {
      final com.cburch.logisim.std.memory.MemContents contents = getMemContents(instance);
      ret = new MemState(contents);
      instance.setData(state, ret);
    }
    return ret;
  }

  @Override
  MemState getState(InstanceState state) {
    com.cburch.logisim.std.memory.MemState ret = (MemState) state.getData();
    if (ret == null) {
      final com.cburch.logisim.std.memory.MemContents contents = getMemContents(state.getInstance());
      ret = new MemState(contents);
      state.setData(ret);
    }
    return ret;
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == Mem.DATA_ATTR || attr == Mem.ADDR_ATTR || attr == StdAttr.APPEARANCE || attr == Mem.LINE_ATTR) {
      instance.recomputeBounds();
      configurePorts(instance);
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    if (painter.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
      RamAppearance.drawRamClassic(painter);
    } else {
      RamAppearance.drawRamEvolution(painter);
    }
  }

  @Override
  public void propagate(InstanceState state) {
    final com.cburch.logisim.std.memory.MemState myState = getState(state);
    final com.cburch.logisim.data.BitWidth dataBits = state.getAttributeValue(DATA_ATTR);
    final com.cburch.logisim.data.AttributeSet attrs = state.getAttributeSet();

    final com.cburch.logisim.data.Value addrValue = state.getPortValue(RamAppearance.getAddrIndex(0, attrs));
    final int nrDataLines = RamAppearance.getNrDataOutPorts(attrs);

    final long addr = addrValue.toLongValue();
    if (addrValue.isErrorValue() || (addrValue.isFullyDefined() && addr < 0)) {
      for (int i = 0; i < nrDataLines; i++)
        state.setPort(RamAppearance.getDataOutIndex(i, attrs), Value.createError(dataBits), DELAY);
      return;
    }
    if (!addrValue.isFullyDefined()) {
      for (int i = 0; i < nrDataLines; i++)
        state.setPort(RamAppearance.getDataOutIndex(i, attrs), Value.createUnknown(dataBits), DELAY);
      return;
    }
    if (addr != myState.getCurrent()) {
      myState.setCurrent(addr);
      myState.scrollToShow(addr);
    }

    boolean misaligned = addr % nrDataLines != 0;
    boolean misalignError = misaligned && !state.getAttributeValue(ALLOW_MISALIGNED);

    for (int i = 0; i < nrDataLines; i++) {
      long val = myState.getContents().get(addr + i);
      state.setPort(
          RamAppearance.getDataOutIndex(i, attrs),
          misalignError ? Value.createError(dataBits) : Value.createKnown(dataBits, val),
          DELAY);
    }
  }

  @Override
  public void removeComponent(Circuit circ, Component c, CircuitState state) {
    closeHexFrame(c);
  }
}
