/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static com.cburch.logisim.file.Strings.S;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitMutator;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.appear.AppearanceSvgReader;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.memory.Mem;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.memory.RamAttributes;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

public class XmlCircuitReader extends CircuitTransaction {

  private static final String contextFmt = "%s.%s";

  private final XmlReader.ReadContext reader;

  private final List<XmlReader.CircuitData> circuitsData;
  private boolean isHolyCross = false;
  private boolean isEvolution = false;

  public XmlCircuitReader(
      XmlReader.ReadContext reader,
      List<XmlReader.CircuitData> circDatas,
      boolean isThisHolyCrossFile,
      boolean isThisEvolutionFile) {
    this.reader = reader;
    this.circuitsData = circDatas;
    this.isHolyCross = isThisHolyCrossFile;
    this.isEvolution = isThisEvolutionFile;
  }

  /**
   * @param elt XML element to parse
   * @param reader XML file reader
   * @return the component built from its XML description
   * @throws XmlReaderException
   */
  static Component getComponent(
      Element elt, XmlReader.ReadContext reader, boolean isHolyCross, boolean isEvolution)
      throws XmlReaderException {

    // Determine the factory that creates this element
    final java.lang.String name = elt.getAttribute("name");
    if (StringUtil.isNullOrEmpty(name)) {
      throw new XmlReaderException(S.get("compNameMissingError"));
    }

    final java.lang.String libName = elt.getAttribute("lib");
    final com.cburch.logisim.tools.Library lib = reader.findLibrary(libName);
    if (lib == null) {
      // FIXME: the "no-lib" thing may not be clear enough
      throw new XmlReaderException(S.get("compUnknownError", "no-lib"));
    }

    final com.cburch.logisim.tools.Tool tool = lib.getTool(name);
    if (!(tool instanceof AddTool)) {
      final java.lang.String msg =
          StringUtil.isNullOrEmpty(libName)
              ? S.get("compUnknownError", name)
              : S.get("compAbsentError", name, libName);
      throw new XmlReaderException(msg);
    }
    final com.cburch.logisim.comp.ComponentFactory source = ((AddTool) tool).getFactory();

    // Determine attributes
    final java.lang.String locStr = elt.getAttribute("loc");
    final com.cburch.logisim.data.AttributeSet attrs = source.createAttributeSet();
    com.cburch.logisim.comp.ComponentFactory defaults = source;
    if (isHolyCross && source instanceof Ram) {
      final com.cburch.logisim.std.memory.RamAttributes ramAttrs = (RamAttributes) attrs;
      ramAttrs.setValue(Mem.ENABLES_ATTR, Mem.USELINEENABLES);
      ramAttrs.updateAttributes();
      defaults = null;
    }
    reader.initAttributeSet(elt, attrs, defaults, isHolyCross, isEvolution);

    // Create component if location known
    if (StringUtil.isNullOrEmpty(locStr)) {
      throw new XmlReaderException(S.get("compLocMissingError", source.getName()));
    }
    try {
      return source.createComponent(Location.parse(locStr), attrs);
    } catch (NumberFormatException e) {
      throw new XmlReaderException(S.get("compLocInvalidError", source.getName(), locStr));
    }
  }

  void addWire(Circuit dest, CircuitMutator mutator, Element elt) throws XmlReaderException {
    Location pt0;
    try {
      final java.lang.String str = elt.getAttribute("from");
      if (str == null || "".equals(str)) {
        throw new XmlReaderException(S.get("wireStartMissingError"));
      }
      pt0 = Location.parse(str);
    } catch (NumberFormatException e) {
      throw new XmlReaderException(S.get("wireStartInvalidError"));
    }

    Location pt1;
    try {
      final java.lang.String str = elt.getAttribute("to");
      if (str == null || "".equals(str)) {
        throw new XmlReaderException(S.get("wireEndMissingError"));
      }
      pt1 = Location.parse(str);
    } catch (NumberFormatException e) {
      throw new XmlReaderException(S.get("wireEndInvalidError"));
    }

    if (!pt0.equals(pt1)) {
      // Avoid zero length wires
      mutator.add(dest, Wire.create(pt0, pt1));
    }
  }

  private void buildCircuit(XmlReader.CircuitData circData, CircuitMutator mutator) {
    final org.w3c.dom.Element element = circData.circuitElement;
    final com.cburch.logisim.circuit.Circuit dest = circData.circuit;
    java.util.Map<org.w3c.dom.Element,com.cburch.logisim.comp.Component> knownComponents = circData.knownComponents;
    if (knownComponents == null) knownComponents = Collections.emptyMap();
    try {
      /* Here we check the attribute circuitnamedbox for backwards compatibility */
      boolean hasNamedBox = false;
      boolean hasNamedBoxFixedSize = false;
      boolean hasAppearAttr = false;
      for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(circData.circuitElement, "a")) {
        if (attrElt.hasAttribute("name")) {
          final java.lang.String name = attrElt.getAttribute("name");
          hasNamedBox |= "circuitnamedbox".equals(name);
          hasAppearAttr |= "appearance".equals(name);
          hasNamedBoxFixedSize |= "circuitnamedboxfixedsize".equals(name);
        }
      }
      reader.initAttributeSet(
          circData.circuitElement, dest.getStaticAttributes(), null, isHolyCross, isEvolution);
      if (circData.circuitElement.hasChildNodes()) {
        if (hasNamedBox) {
          // This situation is clear, it is an older logisim-evolution file
          final com.cburch.logisim.data.AttributeOption appear =
              CollectionUtil.isNotEmpty(circData.appearance)
                  ? CircuitAttributes.APPEAR_CUSTOM
                  : CircuitAttributes.APPEAR_EVOLUTION;
          dest.getStaticAttributes().setValue(CircuitAttributes.APPEARANCE_ATTR, appear);
        } else {
          if (!hasAppearAttr) {
            // Here we have 2 possibilities, either a Holycross file or a logisim-evolution file
            // before the introduction of the named circuit boxes. So let's ask the user.
            com.cburch.logisim.data.AttributeOption appear = CircuitAttributes.APPEAR_CLASSIC;
            if (CollectionUtil.isNotEmpty(circData.appearance)) {
              appear = CircuitAttributes.APPEAR_CUSTOM;
            } else if (isHolyCross) {
              appear = CircuitAttributes.APPEAR_FPGA;
            }
            dest.getStaticAttributes().setValue(CircuitAttributes.APPEARANCE_ATTR, appear);
          }
        }
        if (!hasNamedBoxFixedSize) {
          dest.getStaticAttributes()
              .setValue(CircuitAttributes.NAMED_CIRCUIT_BOX_FIXED_SIZE, false);
        }
      }
    } catch (XmlReaderException e) {
      reader.addErrors(e, circData.circuit.getName() + ".static");
    }

    final java.util.HashMap<com.cburch.logisim.data.Bounds,com.cburch.logisim.comp.Component> componentsAt = new HashMap<Bounds, Component>();
    final java.util.ArrayList<com.cburch.logisim.comp.Component> overlapComponents = new ArrayList<Component>();
    for (final org.w3c.dom.Element subElement : XmlIterator.forChildElements(element)) {
      final java.lang.String subEltName = subElement.getTagName();
      if ("comp".equals(subEltName)) {
        try {
          com.cburch.logisim.comp.Component comp = knownComponents.get(subElement);
          if (comp == null) comp = getComponent(subElement, reader, isHolyCross, isEvolution);
          if (comp != null) {
            /* filter out empty text boxes */
            if (comp.getFactory() instanceof Text) {
              if (comp.getAttributeSet().getValue(Text.ATTR_TEXT).isEmpty()) {
                continue;
              }
            }
            final com.cburch.logisim.data.Bounds bds = comp.getBounds();
            final com.cburch.logisim.comp.Component conflict = componentsAt.get(bds);
            if (conflict != null) {
              final java.lang.String msg =
                  S.get(
                      "fileComponentOverlapError",
                      conflict.getFactory().getName() + conflict.getLocation(),
                      comp.getFactory().getName() + conflict.getLocation());
              reader.addError(msg, circData.circuit.getName());
              overlapComponents.add(comp);
            } else {
              mutator.add(dest, comp);
              componentsAt.put(bds, comp);
            }
          }
        } catch (XmlReaderException e) {
          final java.lang.String context =
              String.format(contextFmt, circData.circuit.getName(), toComponentString(subElement));
          reader.addErrors(e, context);
        }
      } else if ("wire".equals(subEltName)) {
        try {
          addWire(dest, mutator, subElement);
        } catch (XmlReaderException e) {
          final java.lang.String context =
              String.format(contextFmt, circData.circuit.getName(), toWireString(subElement));
          reader.addErrors(e, context);
        }
      }
    }
    for (com.cburch.logisim.comp.Component comp : overlapComponents) {
      final com.cburch.logisim.data.Bounds bds = comp.getBounds();
      if (bds.getHeight() == 0 || bds.getWidth() == 0) {
        // ignore empty boxes
        continue;
      }
      int d = 0;
      do {
        d += 10;
      } while ((componentsAt.get(bds.translate(d, d))) != null && (d < 100_000));
      final com.cburch.logisim.data.Location loc = comp.getLocation().translate(d, d);
      final com.cburch.logisim.data.AttributeSet attrs = (AttributeSet) comp.getAttributeSet().clone();
      comp = comp.getFactory().createComponent(loc, attrs);
      componentsAt.put(comp.getBounds(), comp);
      mutator.add(dest, comp);
    }
  }

  private void buildDynamicAppearance(XmlReader.CircuitData circData) {
    final com.cburch.logisim.circuit.Circuit dest = circData.circuit;
    final java.util.ArrayList<com.cburch.draw.model.AbstractCanvasObject> shapes = new ArrayList<AbstractCanvasObject>();
    for (final org.w3c.dom.Element appearElt : XmlIterator.forChildElements(circData.circuitElement, "appear")) {
      for (final org.w3c.dom.Element sub : XmlIterator.forChildElements(appearElt)) {
        // Dynamic shapes are handled here. Static shapes are already done.
        if (!sub.getTagName().startsWith("visible-")) continue;
        try {
          final com.cburch.draw.model.AbstractCanvasObject m = AppearanceSvgReader.createShape(sub, null, dest);
          if (m == null) {
            final java.lang.String context =
                String.format(contextFmt, circData.circuit.getName(), sub.getTagName());
            reader.addError(S.get("fileAppearanceNotFound", sub.getTagName()), context);
          } else {
            shapes.add(m);
          }
        } catch (RuntimeException e) {
          final java.lang.String context =
              String.format(contextFmt, circData.circuit.getName(), sub.getTagName());
          reader.addError(S.get("fileAppearanceError", sub.getTagName()), context);
        }
      }
    }
    if (!shapes.isEmpty()) {
      if (circData.appearance == null) {
        circData.appearance = shapes;
      } else {
        circData.appearance.addAll(shapes);
      }
    }
    if (CollectionUtil.isNotEmpty(circData.appearance)) {
      dest.getAppearance().setObjectsForce(circData.appearance);
    }
  }

  @Override
  protected Map<Circuit, Integer> getAccessedCircuits() {
    final java.util.HashMap<com.cburch.logisim.circuit.Circuit,java.lang.Integer> access = new HashMap<Circuit, Integer>();
    for (final com.cburch.logisim.file.XmlReader.CircuitData data : circuitsData) {
      access.put(data.circuit, READ_WRITE);
    }
    return access;
  }

  @Override
  protected void run(CircuitMutator mutator) {
    for (final com.cburch.logisim.file.XmlReader.CircuitData circuitData : circuitsData) {
      buildCircuit(circuitData, mutator);
    }
    for (final com.cburch.logisim.file.XmlReader.CircuitData circuitData : circuitsData) {
      buildDynamicAppearance(circuitData);
    }
  }

  private String toComponentString(Element elt) {
    final java.lang.String name = elt.getAttribute("name");
    final java.lang.String loc = elt.getAttribute("loc");
    return String.format("%s(%s)", name, loc);
  }

  private String toWireString(Element elt) {
    final java.lang.String from = elt.getAttribute("from");
    final java.lang.String to = elt.getAttribute("to");
    return String.format("w%s-%s", from, to);
  }
}
