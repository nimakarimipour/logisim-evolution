/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.data;

import static com.cburch.logisim.fpga.Strings.S;

import com.cburch.logisim.circuit.CircuitMapInfo;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.std.io.RgbLed;
import com.cburch.logisim.std.io.SevenSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

public class MapComponent {

  public static final String MAP_KEY = "key";
  public static final String COMPLETE_MAP = "map";
  public static final String OPEN_KEY = "open";
  public static final String CONSTANT_KEY = "vconst";
  public static final String PIN_MAP = "pmap";
  public static final String NO_MAP = "u";
  private static final int ONLY_IO_MAP_NAME = -2;

  private static class MapClass {
    private final FpgaIoInformationContainer IOcomp;
    private Integer pin;

    public MapClass(FpgaIoInformationContainer IOcomp, Integer pin) {
      this.IOcomp = IOcomp;
      this.pin = pin;
    }

    public void unmap() {
      IOcomp.unmap(pin);
    }

    public boolean update(MapComponent comp) {
      return IOcomp.updateMap(pin, comp);
    }

    public FpgaIoInformationContainer getIoComp() {
      return IOcomp;
    }

    public int getIoPin() {
      return pin;
    }

    public void setIOPin(int value) {
      pin = value;
    }
  }

  // In the below structure the first Integer is the pin identifier,
  // the second is the global bubble id
  private final Map<Integer, Integer> myInputBubbles = new HashMap<>();
  private final Map<Integer, Integer> myOutputBubbles = new HashMap<>();
  private final Map<Integer, Integer> myIoBubbles = new HashMap<>();
  /*
   * The following structure defines if the pin is mapped
   */
  private final ComponentFactory myFactory;
  private final AttributeSet myAttributes;

  private final List<String> myName;

  private List<MapClass> maps = new ArrayList<>();
  private List<Boolean> opens = new ArrayList<>();
  private List<Integer> constants = new ArrayList<>();
  private final List<String> pinLabels = new ArrayList<>();

  private int nrOfPins;

  public MapComponent(List<String> name, netlistComponent comp) {
    myFactory = comp.getComponent().getFactory();
    myAttributes = comp.getComponent().getAttributeSet();
    myName = name;
    com.cburch.logisim.fpga.data.ComponentMapInformationContainer mapInfo = comp.getMapInformationContainer();
    java.util.ArrayList<java.lang.String> bName = new ArrayList<String>();
    for (int i = 1; i < name.size(); i++) bName.add(name.get(i));
    com.cburch.logisim.fpga.designrulecheck.BubbleInformationContainer BubbleInfo = comp.getGlobalBubbleId(bName);
    nrOfPins = 0;
    for (int i = 0; i < mapInfo.getNrOfInPorts(); i++) {
      maps.add(null);
      opens.add(false);
      constants.add(-1);
      int idx = BubbleInfo == null ? -1 : BubbleInfo.getInputStartIndex() + i;
      pinLabels.add(mapInfo.getInPortLabel(i));
      myInputBubbles.put(nrOfPins++, idx);
    }
    for (int i = 0; i < mapInfo.getNrOfOutPorts(); i++) {
      maps.add(null);
      opens.add(false);
      constants.add(-1);
      int idx = BubbleInfo == null ? -1 : BubbleInfo.getOutputStartIndex() + i;
      pinLabels.add(mapInfo.getOutPortLabel(i));
      myOutputBubbles.put(nrOfPins++, idx);
    }
    for (int i = 0; i < mapInfo.getNrOfInOutPorts(); i++) {
      maps.add(null);
      opens.add(false);
      constants.add(-1);
      int idx = BubbleInfo == null ? -1 : BubbleInfo.getInOutStartIndex() + i;
      pinLabels.add(mapInfo.getInOutportLabel(i));
      myIoBubbles.put(nrOfPins++, idx);
    }
  }

  public ComponentFactory getComponentFactory() {
    return myFactory;
  }

  public AttributeSet getAttributeSet() {
    return myAttributes;
  }

  public int getNrOfPins() {
    return nrOfPins;
  }

  public boolean hasInputs() {
    return myInputBubbles.keySet().size() != 0;
  }

  public boolean hasOutputs() {
    return myOutputBubbles.keySet().size() != 0;
  }

  public boolean hasIos() {
    return myIoBubbles.keySet().size() != 0;
  }

  public boolean isInput(int pin) {
    return myInputBubbles.containsKey(pin);
  }

  public boolean isOutput(int pin) {
    return myOutputBubbles.containsKey(pin);
  }

  public boolean isIo(int pin) {
    return myIoBubbles.containsKey(pin);
  }

  public int nrInputs() {
    return myInputBubbles.keySet().size();
  }

  public int nrOutputs() {
    return myOutputBubbles.keySet().size();
  }

  public int nrIOs() {
    return myIoBubbles.keySet().size();
  }

  public int getIoBubblePinId(int id) {
    for (java.lang.Integer key : myIoBubbles.keySet()) if (myIoBubbles.get(key) == id) return key;
    return -1;
  }

  public String getPinLocation(int pin) {
    if (pin < 0 || pin >= nrOfPins) return null;
    if (maps.get(pin) == null) return null;
    int iopin = maps.get(pin).getIoPin();
    return maps.get(pin).getIoComp().getPinLocation(iopin);
  }

  public boolean isMapped(int pin) {
    if (pin < 0 || pin >= nrOfPins) return false;
    if (maps.get(pin) != null) return true;
    if (opens.get(pin)) return true;
    return constants.get(pin) >= 0;
  }

  public boolean isInternalMapped(int pin) {
    if (pin < 0 || pin >= nrOfPins) return false;
    return isBoardMapped(pin)
        && maps.get(pin).getIoComp().getType().equals(IoComponentTypes.LedArray);
  }

  public boolean isBoardMapped(int pin) {
    if (pin < 0 || pin >= nrOfPins) return false;
    return maps.get(pin) != null;
  }

  public boolean isExternalInverted(int pin) {
    if (pin < 0 || pin >= nrOfPins) return false;
    if (maps.get(pin) == null) return false;
    return maps.get(pin).getIoComp().getActivityLevel() == PinActivity.ACTIVE_LOW;
  }

  public boolean requiresPullup(int pin) {
    if (pin < 0 || pin >= nrOfPins) return false;
    if (maps.get(pin) == null) return false;
    return maps.get(pin).getIoComp().getPullBehavior() == PullBehaviors.PULL_UP;
  }

  public FpgaIoInformationContainer getFpgaInfo(int pin) {
    if (pin < 0 || pin >= nrOfPins) return null;
    if (maps.get(pin) == null) return null;
    return maps.get(pin).getIoComp();
  }

  public boolean equalsType(netlistComponent comp) {
    return myFactory.equals(comp.getComponent().getFactory());
  }

  public void unmap(int pin) {
    if (pin < 0 || pin >= maps.size()) return;
    if (myFactory instanceof RgbLed) {
      /* we have too look if we have a tripple map */
      final var map1 = maps.get(0);
      final var map2 = maps.get(1);
      final var map3 = maps.get(2);
      if (map1 != null
          && map2 != null
          && map3 != null
          && map1.getIoComp().equals(map2.getIoComp())
          && (map2.getIoComp().equals(map3.getIoComp()))) {
        if ((maps.get(0).getIoPin() == maps.get(1).getIoPin())
            && (maps.get(1).getIoPin() == maps.get(2).getIoPin())) {
          /* we have a tripple map, unmap all */
          map1.unmap();
          map2.unmap();
          map3.unmap();
          for (int i = 0; i < 3; i++) {
            maps.set(i, null);
            opens.set(i, false);
            constants.set(i, -1);
          }
          return;
        }
      }
    }
    com.cburch.logisim.fpga.data.MapComponent.MapClass map = maps.get(pin);
    maps.set(pin, null);
    if (map != null) map.unmap();
    opens.set(pin, false);
    constants.set(pin, -1);
  }

  public void unmap() {
    for (int i = 0; i < nrOfPins; i++) {
      final var map = maps.get(i);
      if (map != null) {
        map.unmap();
      }
      opens.set(i, false);
      constants.set(i, -1);
    }
  }

  public void copyMapFrom(MapComponent comp) {
    if (comp.nrOfPins != nrOfPins || !comp.myFactory.equals(myFactory)) {
      comp.unmap();
      return;
    }
    maps = comp.maps;
    opens = comp.opens;
    constants = comp.constants;
    for (int i = 0; i < nrOfPins; i++) {
      final var map = maps.get(i);
      if (map != null) if (!map.update(this)) unmap(i);
    }
  }

  public void tryMap(CircuitMapInfo cmap, List<FpgaIoInformationContainer> IOcomps) {
    if (cmap.isOpen()) {
      if (cmap.isSinglePin()) {
        final var pin = cmap.getPinId();
        if (pin < 0 || pin >= nrOfPins) return;
        unmap(pin);
        constants.set(pin, -1);
        opens.set(pin, true);
      } else {
        for (int i = 0; i < nrOfPins; i++) {
          unmap(i);
          constants.set(i, -1);
          opens.set(i, true);
        }
      }
    } else if (cmap.isConst()) {
      if (cmap.isSinglePin()) {
        final var pin = cmap.getPinId();
        if (pin < 0 || pin >= nrOfPins) return;
        unmap(pin);
        opens.set(pin, false);
        constants.set(pin, cmap.getConstValue().intValue() & 1);
      } else {
        long mask = 1L;
        java.lang.Long val = cmap.getConstValue();
        for (int i = 0; i < nrOfPins; i++) {
          unmap(i);
          opens.set(i, false);
          int value = (val & mask) == 0L ? 0 : 1;
          constants.set(i, value);
          mask <<= 1;
        }
      }
    }
    if (cmap.getPinMaps() == null) {
      final var rect = cmap.getRectangle();
      if (rect == null) return;
      for (com.cburch.logisim.fpga.data.FpgaIoInformationContainer comp : IOcomps) {
        if (comp.getRectangle().isPointInside(rect.getXpos(), rect.getYpos())) {
          if (cmap.isSinglePin()) {
            tryMap(cmap.getPinId(), comp, cmap.getIoId());
          } else {
            tryMap(comp);
          }
          break;
        }
      }
    } else {
      final var pmaps = cmap.getPinMaps();
      if (pmaps.size() != nrOfPins) return;
      if (myFactory instanceof RgbLed) {
        /* let's see of the RGB-Led is triple mapped */
        boolean isPinMapped = true;
        for (int i = 0; i < nrOfPins; i++) {
          isPinMapped &= pmaps.get(i).isSinglePin();
        }
        if (isPinMapped) {
          final var rect1 = pmaps.get(0).getRectangle();
          final var rect2 = pmaps.get(1).getRectangle();
          final var rect3 = pmaps.get(2).getRectangle();
          if (rect1.equals(rect2) && rect2.equals(rect3)) {
            final var iomap1 = pmaps.get(0).getIoId();
            final var iomap2 = pmaps.get(1).getIoId();
            final var iomap3 = pmaps.get(2).getIoId();
            if (iomap1 == iomap2 && iomap2 == iomap3) {
              /* we have a triple map on a LEDArray, so do it */
              for (com.cburch.logisim.fpga.data.FpgaIoInformationContainer comp : IOcomps) {
                if (comp.getRectangle().isPointInside(rect1.getXpos(), rect1.getYpos())) {
                  tryCompleteMap(comp, iomap1);
                  return;
                }
              }
            }
          }
        }
      }
      for (int i = 0; i < nrOfPins; i++) {
        opens.set(i, false);
        constants.set(i, -1);
        if (maps.get(i) != null) maps.get(i).unmap();
        if (pmaps.get(i) == null) continue;
        if (pmaps.get(i).isOpen()) {
          opens.set(i, true);
          continue;
        }
        if (pmaps.get(i).isConst()) {
          constants.set(i, pmaps.get(i).getConstValue().intValue());
          continue;
        }
        if (pmaps.get(i).isSinglePin()) {
          tryMap(pmaps.get(i), IOcomps);
        }
      }
    }
  }

  public boolean tryCompleteMap(FpgaIoInformationContainer comp, int compPin) {
    com.cburch.logisim.fpga.data.MapComponent.MapClass map = new MapClass(comp, compPin);
    if (!comp.tryMap(this, 0, compPin)) return false;
    for (int i = 0; i < nrOfPins; i++) {
      maps.set(i, map);
      opens.set(i, false);
      constants.set(i, -1);
    }
    return true;
  }

  public boolean tryMap(int myPin, FpgaIoInformationContainer comp, int compPin) {
    if (myPin < 0 || myPin >= nrOfPins) return false;
    com.cburch.logisim.fpga.data.MapComponent.MapClass map = new MapClass(comp, compPin);
    if (!comp.tryMap(this, myPin, compPin)) return false;
    maps.set(myPin, map);
    opens.set(myPin, false);
    constants.set(myPin, -1);
    return true;
  }

  public boolean tryMap(
      String PinKey, CircuitMapInfo cmap, List<FpgaIoInformationContainer> IOcomps) {
    /* this is for backward compatibility */
    java.lang.String[] parts = PinKey.split("#");
    String number = null;
    if (parts.length != 2) return false;
    if (parts[1].contains("Pin")) {
      number = parts[1].substring(3);
    } else if (parts[1].contains("Button")) {
      number = parts[1].substring(6);
    } else {
      int id = 0;
      for (java.lang.String key : SevenSegment.getLabels()) {
        if (parts[1].equals(key)) number = Integer.toString(id);
        id++;
      }
    }
    if (number != null) {
      try {
        final var pinId = Integer.parseUnsignedInt(number);
        for (com.cburch.logisim.fpga.data.FpgaIoInformationContainer comp : IOcomps) {
          if (comp.getRectangle()
              .isPointInside(cmap.getRectangle().getXpos(), cmap.getRectangle().getYpos())) {
            return tryMap(pinId, comp, 0);
          }
        }
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }

  public boolean tryMap(FpgaIoInformationContainer comp) {
    /* first we make a copy of the current map in case we have to restore */
    java.util.ArrayList<com.cburch.logisim.fpga.data.MapComponent.MapClass> oldmaps = new ArrayList<MapClass>();
    java.util.ArrayList<java.lang.Boolean> oldOpens = new ArrayList<Boolean>();
    java.util.ArrayList<java.lang.Integer> oldConstants = new ArrayList<Integer>();
    for (int i = 0; i < nrOfPins; i++) {
      oldmaps.add(maps.get(i));
      oldOpens.add(opens.get(i));
      oldConstants.add(constants.get(i));
    }
    boolean success = true;
    for (int i = 0; i < nrOfPins; i++) {
      com.cburch.logisim.fpga.data.MapComponent.MapClass newMap = new MapClass(comp, -1);
      com.cburch.logisim.fpga.data.MapComponent.MapClass oldMap = maps.get(i);
      if (oldMap != null) oldMap.unmap();
      if (myInputBubbles.containsKey(i)) {
        com.cburch.logisim.fpga.data.FpgaIoInformationContainer.MapResultClass res = comp.tryInputMap(this, i, i);
        success &= res.mapResult;
        newMap.setIOPin(res.pinId);
      } else if (myOutputBubbles.containsKey(i)) {
        int outputid = i - (myInputBubbles == null ? 0 : myInputBubbles.size());
        com.cburch.logisim.fpga.data.FpgaIoInformationContainer.MapResultClass res = comp.tryOutputMap(this, i, outputid);
        success &= res.mapResult;
        newMap.setIOPin(res.pinId);
      } else if (myIoBubbles.containsKey(i)) {
        int ioid =
            i
                - (myInputBubbles == null ? 0 : myInputBubbles.size())
                - (myOutputBubbles == null ? 0 : myOutputBubbles.size());
        com.cburch.logisim.fpga.data.FpgaIoInformationContainer.MapResultClass res = comp.tryIOMap(this, i, ioid);
        success &= res.mapResult;
        newMap.setIOPin(res.pinId);
      } else {
        success = false;
        break;
      }
      if (success) {
        maps.set(i, newMap);
        opens.set(i, false);
        constants.set(i, -1);
      }
    }
    if (!success) {
      /* restore the old situation */
      for (int i = 0; i < nrOfPins && maps != null; i++) {
        if (maps != null && maps.get(i) != null) maps.get(i).unmap();
        com.cburch.logisim.fpga.data.MapComponent.MapClass map = oldmaps.get(i);
        if (map != null) {
          if (tryMap(i, map.getIoComp(), map.getIoPin())) maps.set(i, map);
        }
        opens.set(i, oldOpens.get(i));
        constants.set(i, oldConstants.get(i));
      }
    }
    return success;
  }

  public boolean tryConstantMap(int pin, long value) {
    if (pin < 0) {
      long maskinp = 1L;
      boolean change = false;
      for (int i = 0; i < nrOfPins; i++) {
        if (myInputBubbles.containsKey(i)) {
          if (maps.get(i) != null) maps.get(i).unmap();
          maps.set(i, null);
          constants.set(i, (value & maskinp) == 0 ? 0 : 1);
          opens.set(i, false);
          maskinp <<= 1;
          change = true;
        }
      }
      return change;
    } else {
      if (myInputBubbles.containsKey(pin)) {
        if (maps.get(pin) != null) maps.get(pin).unmap();
        maps.set(pin, null);
        constants.set(pin, (int) (value & 1));
        opens.set(pin, false);
        return true;
      }
    }
    return false;
  }

  public boolean tryOpenMap(int pin) {
    if (pin < 0) {
      for (int i = 0; i < nrOfPins; i++) {
        if (myOutputBubbles.containsKey(i) || myIoBubbles.containsKey(i)) {
          if (maps.get(i) != null) maps.get(i).unmap();
          maps.set(i, null);
          constants.set(i, -1);
          opens.set(i, true);
        }
      }
      return true;
    } else if (myOutputBubbles.containsKey(pin) || myIoBubbles.containsKey(pin)) {
      if (maps.get(pin) != null) {
        maps.get(pin).unmap();
      }
      maps.set(pin, null);
      constants.set(pin, -1);
      opens.set(pin, true);
      return true;
    }
    return false;
  }

  public boolean hasMap() {
    for (int i = 0; i < nrOfPins; i++) {
      if (opens.get(i)) return true;
      if (constants.get(i) >= 0) return true;
      if (maps.get(i) != null) return true;
    }
    return false;
  }

  public boolean isNotMapped() {
    for (int i = 0; i < nrOfPins; i++) {
      if (opens.get(i)) return false;
      if (constants.get(i) >= 0) return false;
      if (maps.get(i) != null) return false;
    }
    return true;
  }

  public boolean isOpenMapped(int pin) {
    if (pin < 0 || pin >= nrOfPins) return true;
    return opens.get(pin);
  }

  public boolean isConstantMapped(int pin) {
    if (pin < 0 || pin >= nrOfPins) return false;
    return (constants.get(pin) >= 0);
  }

  public boolean isZeroConstantMap(int pin) {
    if (pin < 0 || pin >= nrOfPins) return true;
    return constants.get(pin) == 0;
  }

  public boolean isCompleteMap(boolean bothSides) {
    FpgaIoInformationContainer io = null;
    int nrConstants = 0;
    int nrOpens = 0;
    int nrMaps = 0;
    for (int i = 0; i < nrOfPins; i++) {
      if (opens.get(i)) nrOpens++;
      else if (constants.get(i) >= 0) nrConstants++;
      else if (maps.get(i) != null) {
        nrMaps++;
        if (io == null) io = maps.get(i).IOcomp;
        else if (!io.equals(maps.get(i).IOcomp)) return false;
      } else return false;
    }
    if (nrOpens != 0 && nrOpens == nrOfPins) return true;
    if (nrConstants != 0 && nrConstants == nrOfPins) return true;
    if (nrMaps != 0 && nrMaps == nrOfPins) return !bothSides || io.isCompletelyMappedBy(this);
    return false;
  }

  public String getHdlString(int pin) {
    if (pin < 0 || pin >= nrOfPins) return null;
    java.lang.StringBuilder s = new StringBuilder();
    /* The first element is the BoardName, so we skip */
    for (int i = 1; i < myName.size(); i++) s.append(i == 1 ? "" : "_").append(myName.get(i));
    s.append(s.length() == 0 ? "" : "_").append(pinLabels.get(pin));
    return s.toString();
  }

  public String getHdlSignalName(int pin) {
    if (pin < 0 || pin >= nrOfPins) return null;
    if (myInputBubbles.containsKey(pin) && myInputBubbles.get(pin) >= 0) {
      return "s_"
          + HdlGeneratorFactory.LOCAL_INPUT_BUBBLE_BUS_NAME
          + Hdl.bracketOpen()
          + myInputBubbles.get(pin)
          + Hdl.bracketClose();
    }
    if (myOutputBubbles.containsKey(pin) && myOutputBubbles.get(pin) >= 0) {
      return "s_"
          + HdlGeneratorFactory.LOCAL_OUTPUT_BUBBLE_BUS_NAME
          + Hdl.bracketOpen()
          + myOutputBubbles.get(pin)
          + Hdl.bracketClose();
    }
    java.lang.StringBuilder s = new StringBuilder();
    s.append("s_");
    /* The first element is the BoardName, so we skip */
    for (int i = 1; i < myName.size(); i++) s.append(i == 1 ? "" : "_").append(myName.get(i));
    if (nrOfPins > 1) {
      s.append(Hdl.bracketOpen()).append(pin).append(Hdl.bracketClose());
    }
    return s.toString();
  }

  public String getDisplayString(int pin) {
    java.lang.StringBuilder s = new StringBuilder();
    /* The first element is the BoardName, so we skip */
    for (int i = 1; i < myName.size(); i++) s.append("/").append(myName.get(i));
    if (pin >= 0) {
      if (pin < nrOfPins) s.append("#").append(pinLabels.get(pin));
      else s.append("#unknown").append(pin);
      if (opens.get(pin)) s.append("->").append(S.get("MapOpen"));
      if (constants.get(pin) >= 0) s.append("->").append(constants.get(pin) & 1);
    } else {
      boolean outAllOpens = nrOutputs() > 0;
      boolean ioAllOpens = nrIOs() > 0;
      boolean inpAllConst = nrInputs() > 0;
      boolean ioAllConst = ioAllOpens;
      long inpConst = 0L;
      long ioConst = 0L;
      java.lang.String open = S.get("MapOpen");
      for (int i = nrOfPins - 1; i >= 0; i--) {
        if (myInputBubbles.containsKey(i)) {
          inpAllConst &= constants.get(i) >= 0;
          inpConst <<= 1;
          inpConst |= constants.get(i) & 1;
        }
        if (myOutputBubbles.containsKey(i)) {
          outAllOpens &= opens.get(i);
        }
        if (myIoBubbles.containsKey(i)) {
          ioAllOpens &= opens.get(i);
          ioAllConst &= constants.get(i) >= 0;
          ioConst <<= 1;
          ioConst |= constants.get(i) & 1;
        }
      }
      if (pin == ONLY_IO_MAP_NAME) return s.toString();
      if (outAllOpens || ioAllOpens || inpAllConst || ioAllConst) s.append("->");
      boolean addcomma = false;
      if (inpAllConst) {
        s.append("0x").append(Long.toHexString(inpConst));
        addcomma = true;
      }
      if (outAllOpens) {
        if (addcomma) s.append(",");
        else addcomma = true;
        s.append(open);
      }
      if (ioAllOpens) {
        if (addcomma) s.append(",");
        else addcomma = true;
        s.append(open);
      }
      if (ioAllConst) {
        if (addcomma) s.append(",");
        else addcomma = true;
        s.append("0x").append(Long.toHexString(ioConst));
      }
    }
    return s.toString();
  }

  public void getMapElement(Element Map) throws DOMException {
    if (!hasMap()) return;
    Map.setAttribute(MAP_KEY, getDisplayString(ONLY_IO_MAP_NAME));
    if (isCompleteMap(true)) {
      if (opens.get(0)) {
        Map.setAttribute("open", "open");
      } else if (constants.get(0) >= 0) {
        long value = 0L;
        for (int i = nrOfPins - 1; i >= 0; i--) {
          value <<= 1L;
          value += constants.get(i);
        }
        Map.setAttribute(CONSTANT_KEY, Long.toString(value));
      } else {
        final var rect = maps.get(0).IOcomp.getRectangle();
        Map.setAttribute(COMPLETE_MAP, rect.getXpos() + "," + rect.getYpos());
      }
    } else {
      java.lang.StringBuilder s = new StringBuilder();
      boolean first = true;
      for (int i = 0; i < nrOfPins; i++) {
        if (first) first = false;
        else s.append(",");
        if (opens.get(i)) s.append(OPEN_KEY);
        else if (constants.get(i) >= 0) s.append(constants.get(i));
        else if (maps.get(i) != null) {
          com.cburch.logisim.fpga.data.MapComponent.MapClass map = maps.get(i);
          s.append(map.IOcomp.getRectangle().getXpos())
              .append("_")
              .append(map.IOcomp.getRectangle().getYpos())
              .append("_")
              .append(map.pin);
        } else s.append(NO_MAP);
      }
      Map.setAttribute(PIN_MAP, s.toString());
    }
  }

  public static void getComplexMap(Element Map, CircuitMapInfo cmap) throws DOMException {
    List<CircuitMapInfo> pinmaps = cmap.getPinMaps();
    if (pinmaps != null) {
      java.lang.StringBuilder s = new StringBuilder();
      boolean first = true;
      for (CircuitMapInfo pinmap : pinmaps) {
        if (first) first = false;
        else s.append(",");
        if (pinmap == null) {
          s.append(NO_MAP);
        } else {
          if (pinmap.isConst()) s.append(pinmap.getConstValue());
          else if (pinmap.isOpen()) s.append(OPEN_KEY);
          else if (pinmap.isSinglePin())
            s.append(pinmap.getRectangle().getXpos())
                .append("_")
                .append(pinmap.getRectangle().getYpos())
                .append("_")
                .append(pinmap.getIoId());
          else s.append(NO_MAP);
        }
      }
      Map.setAttribute(PIN_MAP, s.toString());
    } else {
      final var br = cmap.getRectangle();
      if (br == null) return;
      Map.setAttribute(COMPLETE_MAP, br.getXpos() + "," + br.getYpos());
    }
  }

  public static CircuitMapInfo getMapInfo(Element map) throws DOMException {
    if (map.hasAttribute(COMPLETE_MAP)) {
      java.lang.String[] xy = map.getAttribute(COMPLETE_MAP).split(",");
      if (xy.length != 2) return null;
      try {
        final var x = Integer.parseUnsignedInt(xy[0]);
        final var y = Integer.parseUnsignedInt(xy[1]);
        return new CircuitMapInfo(x, y);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    if (map.hasAttribute(PIN_MAP)) {
      java.lang.String[] maps = map.getAttribute(PIN_MAP).split(",");
      com.cburch.logisim.circuit.CircuitMapInfo complexI = new CircuitMapInfo();
      for (java.lang.String s : maps) {
        if (s.equals(NO_MAP)) {
          complexI.addPinMap(null);
        } else if (s.equals(OPEN_KEY)) {
          complexI.addPinMap(new CircuitMapInfo());
        } else if (s.contains("_")) {
          java.lang.String[] parts = s.split("_");
          if (parts.length != 3) return null;
          try {
            final var x = Integer.parseUnsignedInt(parts[0]);
            final var y = Integer.parseUnsignedInt(parts[1]);
            final var pin = Integer.parseUnsignedInt(parts[2]);
            complexI.addPinMap(x, y, pin);
          } catch (NumberFormatException e) {
            return null;
          }
        } else {
          try {
            final var c = Long.parseUnsignedLong(s);
            complexI.addPinMap(new CircuitMapInfo(c));
          } catch (NumberFormatException e) {
            return null;
          }
        }
      }
      return complexI;
    }
    return null;
  }
}
