/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.soc.data;

import static com.cburch.logisim.soc.Strings.S;

import com.cburch.contracts.BaseMouseListenerContract;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.soc.bus.SocBusAttributes;
import com.cburch.logisim.util.StringUtil;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JLabel;

public class SocSimulationManager implements SocBusMasterInterface {

  private static class SocBusSelector extends JLabel implements BaseMouseListenerContract {

    private static final long serialVersionUID = 1L;

    private Circuit myCirc;
    private final SocBusInfo myValue;

    public SocBusSelector(Window source, SocBusInfo value) {
      super(S.get("SocBusSelectAttrClick"));
      myCirc = null;
      this.repaint();
      if (source instanceof Frame frame) {
        myCirc = frame.getProject().getCurrentCircuit();
      }
      myValue = value;
      addMouseListener(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myCirc == null) return;
      SocSimulationManager socMan = myCirc.getSocSimulationManager();
      if (!socMan.hasSocBusses()) {
        OptionPane.showMessageDialog(null, S.get("SocManagerNoBusses"), S.get("SocBusSelectAttr"), OptionPane.ERROR_MESSAGE);
        return;
      }
      final java.lang.String id = socMan.getGuiBusId();
      if (StringUtil.isNotEmpty(id)) {
        final java.lang.String oldId = myValue.getBusId();
        final com.cburch.logisim.comp.Component comp = myValue.getComponent();
        if (comp == null) return;
        if (oldId != null && !oldId.equals(id)) {
          myValue.getSocSimulationManager().reRegisterSlaveSniffer(oldId, id, comp);
          final com.cburch.logisim.soc.data.SocBusInfo newId = new SocBusInfo(id);
          newId.setSocSimulationManager(myValue.getSocSimulationManager(), comp);
          comp.getAttributeSet().setValue(SOC_BUS_SELECT, newId);
        }
      }
    }
  }


  private static class SocBusSelectAttribute extends Attribute<SocBusInfo> {
    private SocBusSelectAttribute() {
      super("SocBusSelection", S.getter("SocBusSelectAttr"));
    }

    @Override
    public SocBusInfo parse(String value) {
      return new SocBusInfo(value);
    }

    @Override
    public java.awt.Component getCellEditor(Window source, SocBusInfo value) {
      SocBusSelector ret = new SocBusSelector(source, value);
      ret.mouseClicked(null);
      return ret;
    }

    @Override
    public String toDisplayString(SocBusInfo f) {
      return S.get("SocBusSelectAttrClick");
    }

    @Override
    public String toStandardString(SocBusInfo value) {
      return value.getBusId();
    }
  }

  public static final Attribute<SocBusInfo> SOC_BUS_SELECT = new SocBusSelectAttribute();
  private final HashMap<String, SocBusStateInfo> socBusses = new HashMap<>();
  private final ArrayList<Component> toBeChecked = new ArrayList<>();
  private CircuitState state;

  public String getSocBusDisplayString(String id) {
    if (StringUtil.isNullOrEmpty(id) || !socBusses.containsKey(id)) return null;
    final com.cburch.logisim.soc.data.SocBusStateInfo bus = socBusses.get(id);
    final com.cburch.logisim.comp.Component comp = bus.getComponent();

    String name = null;
    if (comp != null) {
      name = comp.getAttributeSet().getValue(StdAttr.LABEL);
      if (StringUtil.isNullOrEmpty(name)) {
        final com.cburch.logisim.data.Location loc = comp.getLocation();
        name = String.format("%s@%d,%d", comp.getFactory().getDisplayName(), loc.getX(), loc.getY());
      }
    }
    return name;
  }

  public boolean registerComponent(Component c) {
    if (!c.getFactory().isSocComponent()) return false;
    final com.cburch.logisim.soc.data.SocInstanceFactory fact = (SocInstanceFactory) c.getFactory();
    if (fact.isSocUnknown()) return false;
    if (fact.isSocBus()) {
      final com.cburch.logisim.soc.data.SocBusInfo id = c.getAttributeSet().getValue(SocBusAttributes.SOC_BUS_ID);
      if (socBusses.containsKey(id.getBusId()))
        socBusses.get(id.getBusId()).setComponent(c);
      else
        socBusses.put(id.getBusId(), new SocBusStateInfo(this, c));
      c.getAttributeSet().getValue(SocBusAttributes.SOC_BUS_ID).setSocSimulationManager(this, c);
    }
    if (c.getAttributeSet().containsAttribute(SOC_BUS_SELECT)) {
      c.getAttributeSet().getValue(SOC_BUS_SELECT).setSocSimulationManager(this, c);
      if (fact.isSocSlave() || fact.isSocSniffer()) {
        toBeChecked.add(c);
        final java.util.Iterator<com.cburch.logisim.comp.Component> iter = toBeChecked.iterator();
        while (iter.hasNext()) {
          final com.cburch.logisim.comp.Component comp = iter.next();
          if (comp.getAttributeSet().containsAttribute(SOC_BUS_SELECT)) {
            final java.lang.String id = comp.getAttributeSet().getValue(SOC_BUS_SELECT).getBusId();
            if (id != null && socBusses.containsKey(id)) {
              final com.cburch.logisim.soc.data.SocBusStateInfo binfo = socBusses.get(id);
              final com.cburch.logisim.soc.data.SocInstanceFactory factory = (SocInstanceFactory) comp.getFactory();
              if (factory.isSocSlave()) binfo.registerSocBusSlave(factory.getSlaveInterface(comp.getAttributeSet()));
              if (factory.isSocSniffer()) binfo.registerSocBusSniffer(factory.getSnifferInterface(comp.getAttributeSet()));
              iter.remove();
            } else {
              if (id == null || id.isEmpty()) iter.remove();
            }
          }
        }
      }
    }
    return true;
  }

  public boolean removeComponent(Component c) {
    if (!c.getFactory().isSocComponent()) return false;
    final com.cburch.logisim.soc.data.SocInstanceFactory fact = (SocInstanceFactory) c.getFactory();
    if (fact.isSocUnknown()) return false;
    if (fact.isSocBus()) {
      final com.cburch.logisim.soc.data.SocBusStateInfo info = socBusses.get(c.getAttributeSet().getValue(SocBusAttributes.SOC_BUS_ID).getBusId());
      if (info != null)
        info.setComponent(null);
    }
    if (fact.isSocSlave() || fact.isSocSniffer()) {
      final com.cburch.logisim.soc.data.SocBusInfo binfo = c.getAttributeSet().getValue(SOC_BUS_SELECT);
      if (binfo != null) reRegisterSlaveSniffer(binfo.getBusId(), null, c);
    }
    return true;
  }

  public int nrOfSocBusses() {
    int result = 0;
    for (final java.lang.String s : socBusses.keySet()) {
      if (socBusses.get(s).getComponent() != null) result++;
    }
    return result;
  }

  public boolean hasSocBusses() {
    return nrOfSocBusses() != 0;
  }

  public String getGuiBusId() {
    final java.util.HashMap<java.lang.String,java.lang.String> busses = new HashMap<String, String>();
    for (final java.lang.String id : socBusses.keySet()) {
      if (socBusses.get(id).getComponent() != null) busses.put(getSocBusDisplayString(id), id);
    }
    final java.lang.String res = (String) OptionPane.showInputDialog(
        null,
        S.get("SocBusManagerSelectBus"),
        S.get("SocBusSelectAttr"),
        OptionPane.PLAIN_MESSAGE,
        null,
        busses.keySet().toArray(),
        "");

    return StringUtil.isNotEmpty(res) ? busses.get(res) : "";
  }

  public SocBusStateInfo getSocBusState(String busId) {
    return socBusses.get(busId);
  }

  public void reRegisterSlaveSniffer(String oldId, String newId, Component comp) {
    final com.cburch.logisim.soc.data.SocInstanceFactory fact = (SocInstanceFactory) comp.getFactory();
    if (oldId != null && socBusses.containsKey(oldId)) {
      final com.cburch.logisim.soc.data.SocBusStateInfo binfo = socBusses.get(oldId);
      if (fact.isSocSlave()) binfo.removeSocBusSlave(fact.getSlaveInterface(comp.getAttributeSet()));
      if (fact.isSocSniffer()) binfo.removeSocBusSniffer(fact.getSnifferInterface(comp.getAttributeSet()));
    }
    if (newId != null && socBusses.containsKey(newId)) {
      final com.cburch.logisim.soc.data.SocBusStateInfo busInfo = socBusses.get(newId);
      if (fact.isSocSlave()) busInfo.registerSocBusSlave(fact.getSlaveInterface(comp.getAttributeSet()));
      if (fact.isSocSniffer()) busInfo.registerSocBusSniffer(fact.getSnifferInterface(comp.getAttributeSet()));
    }
    toBeChecked.remove(comp);
  }

  public Object getdata(Component comp) {
    if (state == null) return null;
    return state.getData(comp);
  }

  public InstanceState getState(Component comp) {
    if (state == null) return null;
    return state.getInstanceState(comp);
  }

  @Override
  public void initializeTransaction(SocBusTransaction trans, String busId, CircuitState cState) {
    state = cState;
    final com.cburch.logisim.soc.data.SocBusStateInfo info = socBusses.get(busId);
    if (info == null || info.getComponent() == null) {
      trans.setError(SocBusTransaction.NO_SOC_BUS_CONNECTED_ERROR);
      return;
    }
    final java.util.Iterator<com.cburch.logisim.comp.Component> iter = toBeChecked.iterator();
    while (iter.hasNext()) {
      final com.cburch.logisim.comp.Component comp = iter.next();
      if (comp.getAttributeSet().containsAttribute(SOC_BUS_SELECT)) {
        final java.lang.String id = comp.getAttributeSet().getValue(SOC_BUS_SELECT).getBusId();
        if (id != null && socBusses.containsKey(id)) {
          final com.cburch.logisim.soc.data.SocBusStateInfo binfo = socBusses.get(id);
          final com.cburch.logisim.soc.data.SocInstanceFactory fact = (SocInstanceFactory) comp.getFactory();
          if (fact.isSocSlave()) binfo.registerSocBusSlave(fact.getSlaveInterface(comp.getAttributeSet()));
          if (fact.isSocSniffer()) binfo.registerSocBusSniffer(fact.getSnifferInterface(comp.getAttributeSet()));
        } else {
          final com.cburch.logisim.soc.data.SocBusInfo binfo = comp.getAttributeSet().getValue(SOC_BUS_SELECT);
          binfo.setBusId("");
          comp.getAttributeSet().setValue(SOC_BUS_SELECT, binfo);
        }
      }
      iter.remove();
    }
    info.initializeTransaction(trans, busId);
  }

}
