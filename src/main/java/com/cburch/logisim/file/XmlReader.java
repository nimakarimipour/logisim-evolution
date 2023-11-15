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
import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMapInfo;
import com.cburch.logisim.circuit.Splitter;
import com.cburch.logisim.circuit.appear.AppearanceSvgReader;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.data.BoardRectangle;
import com.cburch.logisim.fpga.data.MapComponent;
import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.wiring.BitExtender;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Probe;
import com.cburch.logisim.std.wiring.ProbeAttributes;
import com.cburch.logisim.std.wiring.PullResistor;
import com.cburch.logisim.std.wiring.Tunnel;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.MenuTool;
import com.cburch.logisim.tools.PokeTool;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.TextTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.WiringTool;
import com.cburch.logisim.util.InputEventUtil;
import com.cburch.logisim.util.LineBuffer;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.util.XmlUtil;
import com.cburch.logisim.vhdl.base.VhdlContent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

class XmlReader {

  static class CircuitData {
    final Element circuitElement;
    final Circuit circuit;
    Map<Element, Component> knownComponents;
    List<AbstractCanvasObject> appearance;

    public CircuitData(Element circuitElement, Circuit circuit) {
      this.circuitElement = circuitElement;
      this.circuit = circuit;
    }
  }

  class ReadContext {
    final LogisimFile file;
    LogisimVersion sourceVersion;
    final HashMap<String, Library> libs = new HashMap<>();
    private final ArrayList<String> messages;

    ReadContext(LogisimFile file) {
      this.file = file;
      this.messages = new ArrayList<>();
    }

    void addError(String message, String context) {
      messages.add(message + " [" + context + "]");
    }

    void addErrors(XmlReaderException exception, String context) {
      for (final java.lang.String msg : exception.getMessages()) {
        messages.add(msg + " [" + context + "]");
      }
    }

    Library findLibrary(String libName) throws XmlReaderException {
      if (StringUtil.isNullOrEmpty(libName)) return file;
      final com.cburch.logisim.tools.Library ret = libs.get(libName);
      if (ret == null) throw new XmlReaderException(S.get("libMissingError", libName));
      return ret;
    }

    void initAttributeSet(
        Element parent,
        AttributeSet attrs,
        AttributeDefaultProvider defaults,
        boolean isHolyCross,
        boolean isEvolution)
        throws XmlReaderException {
      List<String> messages = null;

      final java.util.HashMap<java.lang.String,java.lang.String> attrsDefined = new HashMap<String, String>();
      for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(parent, "a")) {
        if (!attrElt.hasAttribute("name")) {
          if (messages == null) messages = new ArrayList<>();
          messages.add(S.get("attrNameMissingError"));
        } else {
          final java.lang.String attrName = attrElt.getAttribute("name");
          String attrVal;
          if (attrElt.hasAttribute("val")) {
            attrVal = attrElt.getAttribute("val");
            if ("filePath".equals(attrName)) {
              /* De-relativize the path */
              java.lang.String dirPath = "";
              if (srcFilePath != null)
                dirPath = srcFilePath.substring(0, srcFilePath.lastIndexOf(File.separator));
              final java.nio.file.Path tmp = Paths.get(dirPath, attrVal);
              attrVal = tmp.toString();
            }
          } else {
            attrVal = attrElt.getTextContent();
          }
          attrsDefined.put(attrName, attrVal);
        }
      }

      if (attrs == null) return;

      LogisimVersion ver = sourceVersion;
      boolean setDefaults = defaults != null && !defaults.isAllDefaultValues(attrs, ver);
      // We need to process this in order, and we have to refetch the
      // attribute list each time because it may change as we iterate
      // (as it will for a splitter).
      for (int i = 0; true; i++) {
        final java.util.List<com.cburch.logisim.data.Attribute<?>> attrList = attrs.getAttributes();
        if (i >= attrList.size()) break;
        @SuppressWarnings("unchecked")
        Attribute<Object> attr = (Attribute<Object>) attrList.get(i);
        final java.lang.String attrName = attr.getName();
        final java.lang.String attrVal = attrsDefined.get(attrName);
        if (attrVal == null) {
          if (attr.equals(ProbeAttributes.PROBEAPPEARANCE)) {
            attrs.setValue(ProbeAttributes.PROBEAPPEARANCE, StdAttr.APPEAR_CLASSIC);
          } else if (attr.equals(StdAttr.APPEARANCE)) {
            if (isHolyCross) attrs.setValue(StdAttr.APPEARANCE, StdAttr.APPEAR_CLASSIC);
            else if (isEvolution) attrs.setValue(StdAttr.APPEARANCE, StdAttr.APPEAR_EVOLUTION);
            else {
              Object val = defaults.getDefaultAttributeValue(attr, ver);
              if (val != null) {
                attrs.setValue(attr, val);
              }
            }
          } else if (setDefaults) {
            Object val = defaults.getDefaultAttributeValue(attr, ver);
            if (val != null) {
              attrs.setValue(attr, val);
            }
          }
        } else {
          try {
            Object val = attr.parse(attrVal);
            attrs.setValue(attr, val);
          } catch (NumberFormatException e) {
            if (messages == null) messages = new ArrayList<>();
            messages.add(S.get("attrValueInvalidError", attrVal, attrName));
          }
        }
      }
      if (messages != null) {
        throw new XmlReaderException(messages);
      }
    }

    private void initMouseMappings(Element elt, boolean isHolyCross, boolean isEvolution) {
      final com.cburch.logisim.file.MouseMappings map = file.getOptions().getMouseMappings();
      for (final org.w3c.dom.Element sub_elt : XmlIterator.forChildElements(elt, "tool")) {
        Tool tool;
        try {
          tool = toTool(sub_elt);
        } catch (XmlReaderException e) {
          addErrors(e, "mapping");
          continue;
        }

        final java.lang.String modsStr = sub_elt.getAttribute("map");
        if (modsStr == null || "".equals(modsStr)) {
          loader.showError(S.get("mappingMissingError"));
          continue;
        }
        int mods;
        try {
          mods = InputEventUtil.fromString(modsStr);
        } catch (NumberFormatException e) {
          loader.showError(S.get("mappingBadError", modsStr));
          continue;
        }

        tool = tool.cloneTool();
        try {
          initAttributeSet(sub_elt, tool.getAttributeSet(), tool, isHolyCross, isEvolution);
        } catch (XmlReaderException e) {
          addErrors(e, "mapping." + tool.getName());
        }

        map.setToolFor(mods, tool);
      }
    }

    private void initToolbarData(Element elt, boolean isHolyCross, boolean isEvolution) {
      final com.cburch.logisim.file.ToolbarData toolbar = file.getOptions().getToolbarData();
      for (final org.w3c.dom.Element subElement : XmlIterator.forChildElements(elt)) {
        if ("sep".equals(subElement.getTagName())) {
          toolbar.addSeparator();
        } else if ("tool".equals(subElement.getTagName())) {
          Tool tool;
          try {
            tool = toTool(subElement);
          } catch (XmlReaderException e) {
            addErrors(e, "toolbar");
            continue;
          }
          if (tool != null) {
            tool = tool.cloneTool();
            try {
              initAttributeSet(subElement, tool.getAttributeSet(), tool, isHolyCross, isEvolution);
            } catch (XmlReaderException e) {
              addErrors(e, "toolbar." + tool.getName());
            }
            if (tool.getAttributeSet() != null) {
              if (tool.getAttributeSet().containsAttribute(ProbeAttributes.PROBEAPPEARANCE))
                tool.getAttributeSet()
                    .setValue(
                        ProbeAttributes.PROBEAPPEARANCE,
                        ProbeAttributes.getDefaultProbeAppearance());
              if (tool.getAttributeSet().containsAttribute(StdAttr.APPEARANCE))
                tool.getAttributeSet()
                    .setValue(StdAttr.APPEARANCE, AppPreferences.getDefaultAppearance());
            }
            toolbar.addTool(tool);
          }
        }
      }
    }

    private Map<Element, Component> loadKnownComponents(Element elt, boolean isHolyCross, boolean isEvolution) {
      final java.util.HashMap<org.w3c.dom.Element,com.cburch.logisim.comp.Component> known = new HashMap<Element, Component>();
      for (final org.w3c.dom.Element sub : XmlIterator.forChildElements(elt, "comp")) {
        try {
          final com.cburch.logisim.comp.Component comp = XmlCircuitReader.getComponent(sub, this, isHolyCross, isEvolution);
          if (comp != null) known.put(sub, comp);
        } catch (XmlReaderException ignored) {
        }
      }
      return known;
    }

    void loadMap(Element board, String boardName, Circuit circ) {
      final java.util.HashMap<java.lang.String,com.cburch.logisim.circuit.CircuitMapInfo> map = new HashMap<String, CircuitMapInfo>();
      for (final org.w3c.dom.Element cmap : XmlIterator.forChildElements(board, "mc")) {
        int x, y, w, h;
        final java.lang.String key = cmap.getAttribute("key");
        if (StringUtil.isNullOrEmpty(key)) continue;
        if (cmap.hasAttribute("open")) {
          map.put(key, new CircuitMapInfo());
        } else if (cmap.hasAttribute("vconst")) {
          long v;
          try {
            v = Long.parseLong(cmap.getAttribute("vconst"));
          } catch (NumberFormatException e) {
            continue;
          }
          map.put(key, new CircuitMapInfo(v));
        } else if (cmap.hasAttribute("valx")
            && cmap.hasAttribute("valy")
            && cmap.hasAttribute("valw")
            && cmap.hasAttribute("valh")) {
          /* Backward compatibility: */
          try {
            x = Integer.parseUnsignedInt(cmap.getAttribute("valx"));
            y = Integer.parseUnsignedInt(cmap.getAttribute("valy"));
            w = Integer.parseUnsignedInt(cmap.getAttribute("valw"));
            h = Integer.parseUnsignedInt(cmap.getAttribute("valh"));
          } catch (NumberFormatException e) {
            continue;
          }
          final com.cburch.logisim.fpga.data.BoardRectangle br = new BoardRectangle(x, y, w, h);
          map.put(key, new CircuitMapInfo(br));
        } else {
          final com.cburch.logisim.circuit.CircuitMapInfo cmapi = MapComponent.getMapInfo(cmap);
          if (cmapi != null)
            map.put(key, cmapi);
        }
      }
      if (!map.isEmpty()) circ.addLoadedMap(boardName, map);
    }

    void loadAppearance(Element appearElt, XmlReader.CircuitData circData, String context) {
      final java.util.ArrayList<com.cburch.logisim.circuit.appear.AppearanceSvgReader.PinInfo> pins = new ArrayList<AppearanceSvgReader.PinInfo>();
      for (final com.cburch.logisim.comp.Component comp : circData.knownComponents.values()) {
        if (comp.getFactory() == Pin.FACTORY) {
          pins.add(AppearanceSvgReader.getPinInfo(comp.getLocation(), Instance.getInstanceFor(comp)));
        }
      }

      final java.util.ArrayList<com.cburch.draw.model.AbstractCanvasObject> shapes = new ArrayList<AbstractCanvasObject>();
      for (final org.w3c.dom.Element sub : XmlIterator.forChildElements(appearElt)) {
        // Dynamic shapes are skipped here. They are resolved later in
        // XmlCircuitReader once the full Circuit tree has been built.
        // Static shapes (e.g. pins and anchors) need to be done here.
        if (sub.getTagName().startsWith("visible-")) continue;
        try {
          final com.cburch.draw.model.AbstractCanvasObject m = AppearanceSvgReader.createShape(sub, pins, null);
          if (m == null) {
            addError(S.get("fileAppearanceNotFound", sub.getTagName()), context + "." + sub.getTagName());
          } else {
            shapes.add(m);
          }
        } catch (RuntimeException e) {
          addError(S.get("fileAppearanceError", sub.getTagName()), context + "." + sub.getTagName());
        }
      }
      if (!shapes.isEmpty()) {
        if (circData.appearance == null) {
          circData.appearance = shapes;
        } else {
          circData.appearance.addAll(shapes);
        }
      }
    }

    private Library toLibrary(Element elt, boolean isHolyCross, boolean isEvolution) {
      if (!elt.hasAttribute("name")) {
        loader.showError(S.get("libNameMissingError"));
        return null;
      }
      if (!elt.hasAttribute("desc")) {
        loader.showError(S.get("libDescMissingError"));
        return null;
      }
      final java.lang.String name = elt.getAttribute("name");
      final java.lang.String desc = elt.getAttribute("desc");
      final com.cburch.logisim.tools.Library ret = loader.loadLibrary(desc);
      if (ret == null) return null;
      libs.put(name, ret);
      for (final org.w3c.dom.Element subElt : XmlIterator.forChildElements(elt, "tool")) {
        if (!subElt.hasAttribute("name")) {
          loader.showError(S.get("toolNameMissingError"));
        } else {
          final java.lang.String toolStr = subElt.getAttribute("name");
          final com.cburch.logisim.tools.Tool tool = ret.getTool(toolStr);
          if (tool != null) {
            try {
              initAttributeSet(subElt, tool.getAttributeSet(), tool, isHolyCross, isEvolution);
            } catch (XmlReaderException e) {
              addErrors(e, "lib." + name + "." + toolStr);
            }
          }
        }
      }
      return ret;
    }

    private void toLogisimFile(Element elt, Project proj) {
      // determine the version producing this file
      final java.lang.String versionString = elt.getAttribute("source");
      boolean isHolyCrossFile = false;
      boolean isEvolutionFile = true;
      if ("".equals(versionString)) {
        sourceVersion = BuildInfo.version;
      } else {
        sourceVersion = LogisimVersion.fromString(versionString);
        isHolyCrossFile = versionString.endsWith("-HC");
      }

      // If we are opening a pre-logisim-evolution file, there might be
      // some components
      // (such as the RAM or the counters), that have changed their shape
      // and other details.
      // We have therefore to warn the user that things might be a little
      // strange in their
      // circuits...
      if (sourceVersion.compareTo(new LogisimVersion(2, 7, 2)) < 0) {
        isEvolutionFile = true;
        OptionPane.showMessageDialog(
            null,
            // FIXME: hardcoded string
            """
                You are opening a file created with original Logisim code.
                You might encounter some problems in the execution, since some components evolved since then.
                Moreover, labels will be converted to match VHDL limitations for variable names.""",
            "Old file format -- compatibility mode",
            OptionPane.WARNING_MESSAGE);
      }

      // first, load the sublibraries
      final java.util.HashSet<com.cburch.logisim.tools.Library> libsToAddAfter = new HashSet<Library>();
      final java.util.HashSet<java.lang.String> baseLibsToEnable = new HashSet<String>();
      final java.util.HashSet<java.lang.String> libsLoaded = new HashSet<String>();
      for (final org.w3c.dom.Element o : XmlIterator.forChildElements(elt, "lib")) {
        final com.cburch.logisim.tools.Library lib = toLibrary(o, isHolyCrossFile, isEvolutionFile);
        if (lib instanceof LoadedLibrary loadedLib) {
          if (loadedLib.getBase() instanceof LogisimFile) {
            libsToAddAfter.add(lib);
            continue;
          }
        }
        if (lib != null) {
          file.addLibrary(lib);
          libsLoaded.add(lib.getName());
        }
      }
      // do a post-processing on the .circ libraries
      for (final com.cburch.logisim.tools.Library logiLib : libsToAddAfter) {
        // first cleanup step: remove unused libraries from loaded library
        LibraryManager.removeUnusedLibraries(logiLib);
        // second cleanup step: promote base libraries
        baseLibsToEnable.addAll(LibraryManager.getUsedBaseLibraries(logiLib));
      }
      // promote the none visible base libraries to toplevel
      final java.util.Set<java.lang.String> builtinLibraries = LibraryManager.getBuildinNames((Loader) loader);
      for (final com.cburch.logisim.tools.Library lib : libsToAddAfter) {
        final java.lang.String libName = lib.getName();
        if (baseLibsToEnable.contains(libName) || !builtinLibraries.contains(libName)) {
          baseLibsToEnable.remove(libName);
        }
      }
      // remove the promoted base libraries from the loaded library and add them
      for (final com.cburch.logisim.tools.Library newLib : libsToAddAfter) {
        LibraryManager.removeBaseLibraries(newLib, baseLibsToEnable);
        file.addLibrary(newLib);
      }

      // second, create the circuits - empty for now - and the vhdl entities
      final java.util.ArrayList<com.cburch.logisim.file.XmlReader.CircuitData> circuitsData = new ArrayList<CircuitData>();
      for (final org.w3c.dom.Element circElt : XmlIterator.forChildElements(elt)) {
        String name;
        switch (circElt.getTagName()) {
          case "vhdl" -> {
            name = circElt.getAttribute("name");
            if (name == null || "".equals(name)) {
              addError(S.get("circNameMissingError"), "C??");
            }
            final java.lang.String vhdl = circElt.getTextContent();
            final com.cburch.logisim.vhdl.base.VhdlContent contents = VhdlContent.parse(name, vhdl, file);
            if (contents != null) {
              file.addVhdlContent(contents);
            }
          }
          case "circuit" -> {
            name = circElt.getAttribute("name");
            if (name == null || "".equals(name)) {
              addError(S.get("circNameMissingError"), "C??");
            }
            final com.cburch.logisim.file.XmlReader.CircuitData circData = new CircuitData(circElt, new Circuit(name, file, proj));
            file.addCircuit(circData.circuit);
            circData.knownComponents = loadKnownComponents(circElt, isHolyCrossFile,
                isEvolutionFile);
            for (Element appearElt : XmlIterator.forChildElements(circElt, "appear")) {
              loadAppearance(appearElt, circData, name + ".appear");
            }
            for (final org.w3c.dom.Element boardMap : XmlIterator.forChildElements(circElt, "boardmap")) {
              final java.lang.String boardName = boardMap.getAttribute("boardname");
              if (StringUtil.isNullOrEmpty(boardName))
                continue;
              loadMap(boardMap, boardName, circData.circuit);
            }
            circuitsData.add(circData);
          }
          default -> {
            // do nothing
          }
        }
      }

      // third, process the other child elements
      for (Element sub_elt : XmlIterator.forChildElements(elt)) {
        final java.lang.String name = sub_elt.getTagName();

        switch (name) {
          case "circuit":
          case "vhdl":
          case "lib":
            // Nothing to do: Done earlier.
            break;
          case "options":
            try {
              initAttributeSet(
                  sub_elt,
                  file.getOptions().getAttributeSet(),
                  null,
                  isHolyCrossFile,
                  isEvolutionFile);
            } catch (XmlReaderException e) {
              addErrors(e, "options");
            }
            break;
          case "mappings":
            initMouseMappings(sub_elt, isHolyCrossFile, isEvolutionFile);
            break;
          case "toolbar":
            initToolbarData(sub_elt, isHolyCrossFile, isEvolutionFile);
            break;
          case "main":
            final java.lang.String main = sub_elt.getAttribute("name");
            final com.cburch.logisim.circuit.Circuit circ = file.getCircuit(main);
            if (circ != null) {
              file.setMainCircuit(circ);
            }
            break;
          case "message":
            file.addMessage(sub_elt.getAttribute("value"));
            break;
          default:
            throw new IllegalArgumentException("Invalid node in logisim file: " + name);
        }
      }

      // fourth, execute a transaction that initializes all the circuits
      XmlCircuitReader builder;
      builder = new XmlCircuitReader(this, circuitsData, isHolyCrossFile, isEvolutionFile);
      builder.execute();
    }

    Tool toTool(Element elt) throws XmlReaderException {
      final com.cburch.logisim.tools.Library lib = findLibrary(elt.getAttribute("lib"));
      final java.lang.String name = elt.getAttribute("name");
      if (name == null || "".equals(name)) {
        throw new XmlReaderException(S.get("toolNameMissing"));
      }
      final com.cburch.logisim.tools.Tool tool = lib.getTool(name);
      if (tool == null) {
        throw new XmlReaderException(S.get("toolNotFound"));
      }
      return tool;
    }
  }

  public static final Logger logger = LoggerFactory.getLogger(XmlReader.class);
  private final LibraryLoader loader;

  /**
   * Path of the source file -- it is used to make the paths of the components stored in the file
   * absolute, to prevent the system looking for them in some strange directories.
   */
  private final String srcFilePath;

  XmlReader(Loader loader, File file) {
    this.loader = loader;
    if (file != null) this.srcFilePath = file.getAbsolutePath();
    else this.srcFilePath = null;
  }


  /**
   * Change label names in an XML tree according to a list of suggested labels.
   *
   * @param root root element of the XML tree
   * @param nodeType type of nodes to consider
   * @param attrType type of attributes to consider
   * @param validLabels label set of correct label names
   */
  public static void applyValidLabels(
      Element root, String nodeType, String attrType, Map<String, String> validLabels)
      throws IllegalArgumentException {

    if (root == null) throw new RuntimeException("Value of 'root' cannot be null");
    if (nodeType == null) throw new RuntimeException("Value of 'nodeType' cannot be null");
    if (attrType == null) throw new RuntimeException("Value of 'attrType' cannot be null");
    if (nodeType.length() == 0) throw new RuntimeException("Empty string is not a valid value of 'nodeType'.");
    if (attrType.length() == 0) throw new RuntimeException("Empty string is not a valid value of 'attrType'.");
    if (validLabels == null) throw new RuntimeException("Value of 'validLabels' cannot be null");

    switch (nodeType) {
      case "circuit" -> replaceCircuitNodes(root, attrType, validLabels);
      case "comp" -> replaceCompNodes(root, validLabels);
      default -> throw new IllegalArgumentException("Invalid node type requested: " + nodeType);
    }
  }

  /**
   * Sets to the empty string any label attribute in tool nodes derived from elt.
   *
   * @param root root node
   */
  private static void cleanupToolsLabel(Element root) {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null");

    // Iterate on tools
    for (final org.w3c.dom.Element toolElt : XmlIterator.forChildElements(root, "tool")) {
      // Iterate on attribute nodes
      for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(toolElt, "a")) {
        // Each attribute node should have a name field
        if (attrElt.hasAttribute("name")) {
          final java.lang.String aName = attrElt.getAttribute("name");
          if ("label".equals(aName)) {
            // Found a label node in a tool, clean it up!
            attrElt.setAttribute("val", "");
          }
        }
      }
    }
  }

  public static Element ensureLogisimCompatibility(Element elt) {
    java.util.Map<java.lang.String,java.lang.String> validLabels = findValidLabels(elt, "circuit", "name");
    applyValidLabels(elt, "circuit", "name", validLabels);
    validLabels = findValidLabels(elt, "circuit", "label");
    applyValidLabels(elt, "circuit", "label", validLabels);
    validLabels = findValidLabels(elt, "comp", "label");
    applyValidLabels(elt, "comp", "label", validLabels);
    // In old, buggy Logisim versions, labels where incorrectly
    // stored also in toolbar and lib components. If this is the
    // case, clean them up.
    fixInvalidToolbarLib(elt);
    return (elt);
  }

  private static void findLibraryUses(ArrayList<Element> dest, String label, Iterable<Element> candidates) {
    for (final org.w3c.dom.Element elt : candidates) {
      String lib = elt.getAttribute("lib");
      if (lib.equals(label)) {
        dest.add(elt);
      }
    }
  }

  /**
   * Check an XML tree for VHDL-incompatible labels, then propose a list of valid ones. Here valid
   * means: [a-zA-Z][a-zA-Z0-9_]* This applies, in our context, to circuit's names and labels (and
   * their corresponding component's names, of course), and to comp's labels.
   *
   * @param root root element of the XML tree
   * @param nodeType type of nodes to consider
   * @param attrType type of attributes to consider
   * @return map containing the original attribute values as keys, and the corresponding valid
   *     attribute values as the values
   */
  public static Map<String, String> findValidLabels(Element root, String nodeType, String attrType) {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null");
    if (nodeType == null) throw new RuntimeException("Value of 'nodeType' cannot be null");
    if (attrType == null) throw new RuntimeException("Value of 'attrType' cannot be null");
    if (nodeType.length() == 0) throw new RuntimeException("Empty string is not a valid value of 'nodeType'.");
    if (attrType.length() == 0) throw new RuntimeException("Empty string is not a valid value of 'attrType'.");

    final java.util.HashMap<java.lang.String,java.lang.String> validLabels = new HashMap<String, String>();

    final java.util.List<java.lang.String> initialLabels = getXMLLabels(root, nodeType, attrType);

    for (java.lang.String label : initialLabels) {
      if (!validLabels.containsKey(label)) {
        // Check if the name is invalid, in which case create
        // a valid version and put it in the map
        if (VhdlContent.labelVHDLInvalid(label)) {
          final java.lang.String initialLabel = label;
          label = generateValidVHDLLabel(label);
          validLabels.put(initialLabel, label);
        }
      }
    }

    return validLabels;
  }

  /**
   * In some old version of Logisim, buggy Logisim versions, labels where incorrectly stored also in
   * toolbar and lib components. If this is the case, clean them up..
   *
   * @param root root element of the XML tree
   */
  private static void fixInvalidToolbarLib(Element root) {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null");

    // Iterate on toolbars -- though there should be only one!
    for (Element toolbarElt : XmlIterator.forChildElements(root, "toolbar")) {
      cleanupToolsLabel(toolbarElt);
    }

    // Iterate on libs
    for (Element libsElt : XmlIterator.forChildElements(root, "lib")) {
      cleanupToolsLabel(libsElt);
    }
  }

  /**
   * Given a label, generates a valid VHDL label by removing invalid characters, putting a letter at
   * the beginning, and putting a shortened (8 characters) UUID at the end if the name has been
   * altered. Whitespaces at the beginning and at the end of the string are trimmed by default (if
   * this is the only change, then no suffix is appended).
   *
   * @param initialLabel initial (possibly invalid) label
   * @return a valid VHDL label
   */
  public static String generateValidVHDLLabel(String initialLabel) {
    return (generateValidVHDLLabel(initialLabel, UUID.randomUUID().toString().substring(0, 8)));
  }

  /**
   * Given a label, generates a valid VHDL label by removing invalid characters, putting a letter at
   * the beginning, and putting the requested suffix at the end if the name has been altered.
   * Whitespaces at the beginning and at the end of the string are trimmed by default (if this is
   * the only change, then no suffix is appended).
   *
   * @param initialLabel initial (possibly invalid) label
   * @param suffix string that has to be appended to a modified label
   * @return a valid VHDL label
   */
  public static String generateValidVHDLLabel(String initialLabel, String suffix) {
    if (initialLabel == null) throw new RuntimeException("Value of 'initialLabel' cannot be null.");

    // As a default, trim whitespaces at the beginning and at the end
    // of a label (no risks with that potentially, therefore avoid
    // to append the suffix if that was the only change)
    initialLabel = initialLabel.trim();

    java.lang.String label = initialLabel;
    if (label.isEmpty()) {
      logger.warn("Empty label is not a valid VHDL label");
      label = "L_";
    }

    // If the string has a ! or ~ symbol, then replace it with "NOT"
    label = label.replaceAll("[!~]", "NOT_");

    // Force string to start with a letter
    if (!label.matches("^[A-Za-z].*$")) label = "L_" + label;

    // Force the rest to be either letters, or numbers, or underscores
    label = label.replaceAll("\\W", "_");
    // Suppress multiple successive underscores and an underscore at the end
    label = label.replaceAll("_+", "_");
    if (label.endsWith("_")) label = label.substring(0, label.length() - 1);

    if (!label.equals(initialLabel)) {
      // Concatenate a unique ID if the string has been altered
      label = label + "_" + suffix;
      // Replace the "-" characters in the UUID with underscores
      label = label.replaceAll("-", "_");
    }

    return (label);
  }

  /**
   * Traverses an XML tree and gets a list of attribute values for the given attribute and node
   * types.
   *
   * @param root root element of the XML tree
   * @param nodeType type of nodes to consider
   * @param attrType type of attributes to consider
   * @return list of names for the considered node/attribute pairs
   */
  public static List<String> getXMLLabels(Element root, String nodeType, String attrType) throws IllegalArgumentException {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null.");
    if (nodeType == null) throw new RuntimeException("Value of 'nodeType' cannot be null.");
    if (attrType == null) throw new RuntimeException("Value of 'attrType' cannot be null.");
    if (nodeType.length() == 0) throw new RuntimeException("Empty string is not a valid value of 'nodeType'.");
    if (attrType.length() == 0) throw new RuntimeException("Empty string is not a valid value of 'attrType'.");

    final java.util.ArrayList<java.lang.String> attrValuesList = new ArrayList<String>();

    switch (nodeType) {
      case "circuit" -> inspectCircuitNodes(root, attrType, attrValuesList);
      case "comp" -> inspectCompNodes(root, attrValuesList);
      default -> throw new IllegalArgumentException("Invalid node type requested: " + nodeType);
    }
    return attrValuesList;
  }

  /**
   * Check XML's circuit nodes, and return a list of values corresponding to the desired attribute.
   *
   * @param root XML's root
   * @param attrType attribute type (either name or label)
   * @param attrValuesList empty list that will contain the values found
   */
  private static void inspectCircuitNodes(Element root, String attrType, List<String> attrValuesList) throws IllegalArgumentException {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null.");
    if (attrType == null) throw new RuntimeException("Value of 'attrType' cannot be null.");
    if (attrValuesList == null) throw new RuntimeException("Value of 'attrValuesList' cannot be null.");

    // Circuits are top-level in the XML file
    switch (attrType) {
      case "name":
        for (final org.w3c.dom.Element circElt : XmlIterator.forChildElements(root, "circuit")) {
          // Circuit's name is directly available as an attribute
          final java.lang.String name = circElt.getAttribute("name");
          attrValuesList.add(name);
        }
        break;
      case "label":
        for (final org.w3c.dom.Element circElt : XmlIterator.forChildElements(root, "circuit")) {
          // label is available through its a child node
          for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(circElt, "a")) {
            if (attrElt.hasAttribute("name")) {
              final java.lang.String aName = attrElt.getAttribute("name");
              if ("label".equals(aName)) {
                final java.lang.String label = attrElt.getAttribute("val");
                if (label.length() > 0) {
                  attrValuesList.add(label);
                }
              }
            }
          }
        }
        break;
      default:
        throw new IllegalArgumentException(
            LineBuffer.format("Invalid attribute type requested: {{1}} for node type: circuit", attrType));
    }
  }

  /**
   * Check XML's comp nodes, and return a list of values corresponding to the desired attribute. The
   * checked comp nodes are NOT those referring to circuits -- we can see if this is the case by
   * checking whether the lib attribute is present or not.
   *
   * @param root XML's root
   * @param attrValuesList empty list that will contain the values found
   */
  private static void inspectCompNodes(Element root, List<String> attrValuesList) {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null.");
    if (attrValuesList == null) throw new RuntimeException("Value of 'attrValuesList' cannot be null.");
    if (!attrValuesList.isEmpty()) throw new RuntimeException("The 'attrValuesList' must be empty.");

    for (final org.w3c.dom.Element circElt : XmlIterator.forChildElements(root, "circuit")) {
      // In circuits, we have to look for components, then take just those components
      // that do have a lib attribute and look at their a child nodes.
      for (final org.w3c.dom.Element compElt : XmlIterator.forChildElements(circElt, "comp")) {
        if (compElt.hasAttribute("lib")) {
          for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(compElt, "a")) {
            if (attrElt.hasAttribute("name")) {
              final java.lang.String aName = attrElt.getAttribute("name");
              if ("label".equals(aName)) {
                final java.lang.String label = attrElt.getAttribute("val");
                if (label.length() > 0) {
                  attrValuesList.add(label);
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Check if a given label could be a valid VHDL variable name.
   *
   * @param label candidate VHDL variable name
   * @return true if the label is NOT a valid name, false otherwise
   */
  public static boolean labelVHDLInvalid(String label) {
    return !label.matches("^[A-Za-z]\\w*") || label.endsWith("_") || label
        .matches(".*__.*");
  }

  /**
   * Replace invalid labels in circuit nodes.
   *
   * @param root XML's root
   * @param attrType attribute type (either name or label)
   * @param validLabels map containing valid label values
   */
  private static void replaceCircuitNodes(Element root, String attrType, Map<String, String> validLabels) throws IllegalArgumentException {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null.");
    if (attrType == null) throw new RuntimeException("Value of 'attrType' cannot be null.");
    if (validLabels == null) throw new RuntimeException("Value of 'validLabels' cannot be null.");

    if (validLabels.isEmpty()) {
      // Particular case, all the labels were good!
      return;
    }

    // Circuits are top-level in the XML file
    switch (attrType) {
      case "name":
        // We have not only to replace the circuit names in each circuit,
        // but in the corresponding comps too!
        for (final org.w3c.dom.Element circElt : XmlIterator.forChildElements(root, "circuit")) {
          // Circuit's name is directly available as an attribute
          final java.lang.String name = circElt.getAttribute("name");
          if (validLabels.containsKey(name)) {
            circElt.setAttribute("name", validLabels.get(name));
            // Also, it is present as value for the "circuit" attribute
            for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(circElt, "a")) {
              if (attrElt.hasAttribute("name")) {
                final java.lang.String aName = attrElt.getAttribute("name");
                if (aName.equals("circuit")) {
                  attrElt.setAttribute("val", validLabels.get(name));
                }
              }
            }
          }
          // Now do the comp part
          for (final org.w3c.dom.Element compElt : XmlIterator.forChildElements(circElt, "comp")) {
            // Circuits are components without lib
            if (!compElt.hasAttribute("lib")) {
              if (compElt.hasAttribute("name")) {
                final java.lang.String cName = compElt.getAttribute("name");
                if (validLabels.containsKey(cName)) {
                  compElt.setAttribute("name", validLabels.get(cName));
                }
              }
            }
          }
        }
        break;
      case "label":
        for (final org.w3c.dom.Element circElt : XmlIterator.forChildElements(root, "circuit")) {
          // label is available through its a child node
          for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(circElt, "a")) {
            if (attrElt.hasAttribute("name")) {
              final java.lang.String aName = attrElt.getAttribute("name");
              if ("label".equals(aName)) {
                final java.lang.String label = attrElt.getAttribute("val");
                if (validLabels.containsKey(label)) {
                  attrElt.setAttribute("val", validLabels.get(label));
                }
              }
            }
          }
        }
        break;
      default:
        throw new IllegalArgumentException(
            "Invalid attribute type requested: " + attrType + " for node type: circuit");
    }
  }

  /**
   * Replace invalid labels in comp nodes.
   *
   * @param root XML's root
   * @param validLabels map containing valid label values
   */
  private static void replaceCompNodes(Element root, Map<String, String> validLabels) {
    if (root == null) throw new RuntimeException("Value of 'root' cannot be null.");
    if (validLabels == null) throw new RuntimeException("Value of 'validLabels' cannot be null.");

    if (validLabels.isEmpty()) {
      // Particular case, all the labels were good!
      return;
    }

    for (Element circElt : XmlIterator.forChildElements(root, "circuit")) {
      // In circuits, we have to look for components, then take
      // just those components that do have a lib attribute and look at
      // their
      // a child nodes
      for (final org.w3c.dom.Element compElt : XmlIterator.forChildElements(circElt, "comp")) {
        if (compElt.hasAttribute("lib")) {
          for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(compElt, "a")) {
            if (attrElt.hasAttribute("name")) {
              final java.lang.String aName = attrElt.getAttribute("name");
              if ("label".equals(aName)) {
                final java.lang.String label = attrElt.getAttribute("val");
                if (validLabels.containsKey(label)) {
                  attrElt.setAttribute("val", validLabels.get(label));
                }
              }
            }
          }
        }
      }
    }
  }

  private void addToLabelMap(HashMap<String, String> labelMap, String srcLabel, String dstLabel, String toolNames) {
    if (srcLabel != null && dstLabel != null) {
      for (final java.lang.String tool : toolNames.split(";")) {
        labelMap.put(srcLabel + ":" + tool, dstLabel);
      }
    }
  }

  private void considerRepairs(Document doc, Element root) {
    final com.cburch.logisim.LogisimVersion version = LogisimVersion.fromString(root.getAttribute("source"));
    if (version.compareTo(new LogisimVersion(2, 3, 0)) < 0) {
      // This file was saved before an Edit tool existed. Most likely
      // we should replace the Select and Wiring tools in the toolbar
      // with the Edit tool instead.
      for (final org.w3c.dom.Element toolbar : XmlIterator.forChildElements(root, "toolbar")) {
        Element wiring = null;
        Element select = null;
        Element edit = null;
        for (final org.w3c.dom.Element elt : XmlIterator.forChildElements(toolbar, "tool")) {
          final java.lang.String eltName = elt.getAttribute("name");
          if (StringUtil.isNotEmpty(eltName)) {
            if (eltName.equals(SelectTool._ID)) select = elt;
            if (eltName.equals(WiringTool._ID)) wiring = elt;
            if (eltName.equals(EditTool._ID)) edit = elt;
          }
        }
        if (select != null && wiring != null && edit == null) {
          select.setAttribute("name", EditTool._ID);
          toolbar.removeChild(wiring);
        }
      }
    }
    if (version.compareTo(new LogisimVersion(2, 6, 3)) < 0) {
      for (final org.w3c.dom.Element circElt : XmlIterator.forChildElements(root, "circuit")) {
        for (final org.w3c.dom.Element attrElt : XmlIterator.forChildElements(circElt, "a")) {
          final java.lang.String name = attrElt.getAttribute("name");
          if (StringUtil.startsWith(name, "label")) {
            attrElt.setAttribute("name", "c" + name);
          }
        }
      }

      repairForWiringLibrary(doc, root);
      repairForLegacyLibrary(doc, root);
    }
  }

  private Document loadXmlFrom(InputStream is) throws SAXException, IOException {
    final javax.xml.parsers.DocumentBuilderFactory factory = XmlUtil.getHardenedBuilderFactory();
    factory.setNamespaceAware(true);
    try {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (ParserConfigurationException ex) {
      // All implementations are required to support FEATURE_SECURE_PROCESSING.
    }
    DocumentBuilder builder = null;
    try {
      builder = factory.newDocumentBuilder();
    } catch (ParserConfigurationException ignored) {
    }
    return builder.parse(is);
  }

  LogisimFile readLibrary(InputStream is, Project proj) throws IOException, SAXException {
    final org.w3c.dom.Document doc = loadXmlFrom(is);
    org.w3c.dom.Element elt = doc.getDocumentElement();
    elt = ensureLogisimCompatibility(elt);

    considerRepairs(doc, elt);
    final com.cburch.logisim.file.LogisimFile file = new LogisimFile((Loader) loader);
    final com.cburch.logisim.file.XmlReader.ReadContext context = new ReadContext(file);

    context.toLogisimFile(elt, proj);

    if (file.getCircuitCount() == 0) {
      file.addCircuit(new Circuit("main", file, proj));
    }
    if (!context.messages.isEmpty()) {
      final java.lang.StringBuilder all = new StringBuilder();
      for (final java.lang.String msg : context.messages) {
        all.append(msg).append("\n");
      }
      loader.showError(all.substring(0, all.length() - 1));
    }
    return file;
  }

  private void relocateTools(Element src, Element dest, HashMap<String, String> labelMap) {
    if (src == null || src == dest) return;
    final java.lang.String srcLabel = src.getAttribute("name");
    if (srcLabel == null) return;

    final java.util.ArrayList<org.w3c.dom.Element> toRemove = new ArrayList<Element>();
    for (final org.w3c.dom.Element elt : XmlIterator.forChildElements(src, "tool")) {
      final java.lang.String name = elt.getAttribute("name");
      if (name != null && labelMap.containsKey(srcLabel + ":" + name)) {
        toRemove.add(elt);
      }
    }
    for (final org.w3c.dom.Element elt : toRemove) {
      src.removeChild(elt);
      if (dest != null) {
        dest.appendChild(elt);
      }
    }
  }

  private void repairForLegacyLibrary(Document doc, Element root) {
    Element legacyElt = null;
    String legacyLabel = null;
    for (final org.w3c.dom.Element libElt : XmlIterator.forChildElements(root, "lib")) {
      final java.lang.String desc = libElt.getAttribute("desc");
      final java.lang.String label = libElt.getAttribute("name");
      if ("#Legacy".equals(desc)) {
        legacyElt = libElt;
        legacyLabel = label;
      }
    }

    if (legacyElt != null) {
      root.removeChild(legacyElt);

      final java.util.ArrayList<org.w3c.dom.Element> toRemove = new ArrayList<Element>();
      findLibraryUses(toRemove, legacyLabel, XmlIterator.forDescendantElements(root, "comp"));
      boolean componentsRemoved = !toRemove.isEmpty();
      findLibraryUses(toRemove, legacyLabel, XmlIterator.forDescendantElements(root, "tool"));
      for (final org.w3c.dom.Element elt : toRemove) {
        elt.getParentNode().removeChild(elt);
      }
      if (componentsRemoved) {
        final java.lang.String error = "Some components have been deleted. The Legacy library is not supported.";
        final org.w3c.dom.Element elt = doc.createElement("message");
        elt.setAttribute("value", error);
        root.appendChild(elt);
      }
    }
  }

  private void repairForWiringLibrary(Document doc, Element root) {
    Element oldBaseElt = null;
    String oldBaseLabel = null;
    Element gatesElt = null;
    String gatesLabel = null;
    int maxLabel = -1;
    Element firstLibElt = null;
    Element lastLibElt = null;
    for (final org.w3c.dom.Element libElt : XmlIterator.forChildElements(root, "lib")) {
      final java.lang.String desc = libElt.getAttribute("desc");
      final java.lang.String label = libElt.getAttribute("name");

      if (desc != null) {
        switch (desc) {
          case "#Base":
            oldBaseElt = libElt;
            oldBaseLabel = label;
            break;
          case "#Wiring":
            // Wiring library already in file. This shouldn't happen, but if
            // somehow it does, we don't want to add it again.
            return;
          case "#Gates":
            gatesElt = libElt;
            gatesLabel = label;
            break;
        }
      }

      if (firstLibElt == null) firstLibElt = libElt;
      lastLibElt = libElt;
      try {
        if (label != null) {
          final int thisLabel = Integer.parseInt(label);
          if (thisLabel > maxLabel) maxLabel = thisLabel;
        }
      } catch (NumberFormatException ignored) {
      }
    }

    Element wiringElt;
    String wiringLabel;
    Element newBaseElt;
    String newBaseLabel;
    if (oldBaseElt != null) {
      wiringLabel = oldBaseLabel;
      wiringElt = oldBaseElt;
      wiringElt.setAttribute("desc", "#Wiring");

      newBaseLabel = "" + (maxLabel + 1);
      newBaseElt = doc.createElement("lib");
      newBaseElt.setAttribute("desc", "#Base");
      newBaseElt.setAttribute("name", newBaseLabel);
      root.insertBefore(newBaseElt, lastLibElt.getNextSibling());
    } else {
      wiringLabel = "" + (maxLabel + 1);
      wiringElt = doc.createElement("lib");
      wiringElt.setAttribute("desc", "#Wiring");
      wiringElt.setAttribute("name", wiringLabel);
      root.insertBefore(wiringElt, lastLibElt.getNextSibling());

      newBaseLabel = null;
      newBaseElt = null;
    }

    final java.util.HashMap<java.lang.String,java.lang.String> labelMap = new HashMap<String, String>();
    addToLabelMap(
        labelMap,
        oldBaseLabel,
        newBaseLabel,
        String.join(
            ";",
            Arrays.asList(
                PokeTool._ID,
                EditTool._ID,
                SelectTool._ID,
                WiringTool._ID,
                TextTool._ID,
                MenuTool._ID,
                Text._ID)));
    addToLabelMap(
        labelMap,
        oldBaseLabel,
        wiringLabel,
        String.join(
            ";",
            Arrays.asList(
                Splitter._ID,
                Pin._ID,
                Probe._ID,
                Tunnel._ID,
                Clock._ID,
                PullResistor._ID,
                BitExtender._ID)));
    addToLabelMap(labelMap, gatesLabel, wiringLabel, "Constant");
    relocateTools(oldBaseElt, newBaseElt, labelMap);
    relocateTools(oldBaseElt, wiringElt, labelMap);
    relocateTools(gatesElt, wiringElt, labelMap);
    updateFromLabelMap(XmlIterator.forDescendantElements(root, "comp"), labelMap);
    updateFromLabelMap(XmlIterator.forDescendantElements(root, "tool"), labelMap);
  }

  private void updateFromLabelMap(Iterable<Element> elts, HashMap<String, String> labelMap) {
    for (final org.w3c.dom.Element elt : elts) {
      final java.lang.String oldLib = elt.getAttribute("lib");
      final java.lang.String name = elt.getAttribute("name");
      if (oldLib != null && name != null) {
        final java.lang.String newLib = labelMap.get(oldLib + ":" + name);
        if (newLib != null) {
          elt.setAttribute("lib", newLib);
        }
      }
    }
  }
}
