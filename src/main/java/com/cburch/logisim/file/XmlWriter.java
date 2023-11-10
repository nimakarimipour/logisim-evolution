/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeDefaultProvider;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.data.MapComponent;
import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.wiring.ProbeAttributes;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.InputEventUtil;
import com.cburch.logisim.util.LineBuffer;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.util.XmlUtil;
import com.cburch.logisim.vhdl.base.VhdlContent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

final class XmlWriter {

  private final LogisimFile file;
  private final Document doc;
  /**
   * Path of the file which is being written on disk -- used to relativize components stored in it.
   */
  private final String outFilePath;
  private final String librariesPath;
  private final boolean isProjectExport;
  private final LibraryLoader loader;
  private final HashMap<Library, String> libs = new HashMap<>();

  private XmlWriter(LogisimFile file, Document doc, LibraryLoader loader) {
    this(file, doc, loader, null, null);
  }

  private XmlWriter(LogisimFile file, Document doc, LibraryLoader loader, String outFilePath) {
    this(file, doc, loader, outFilePath, null);
  }

  private XmlWriter(LogisimFile file, Document doc, LibraryLoader loader, String outFilePath, String librariesPath) {
    this.file = file;
    this.doc = doc;
    this.loader = loader;
    this.outFilePath = outFilePath;
    this.librariesPath = librariesPath;
    isProjectExport = StringUtil.isNotEmpty(librariesPath);
  }


  /* We sort some parts of the xml tree, to help with reproducibility and to
   * ease testing (e.g. diff a circuit file). Attribute name=value pairs seem
   * to be sorted already, so we don't worry about those. The code below sorts
   * the nodes, but only in best-effort fashion (some nodes are identical
   * except for their child contents, which seems overkill to bother sorting).
   * Parts of the tree where node order matters (top-level "project", the
   * libraries, and the toolbar, for example) are not sorted.
   */

  static String attrToString(Attr a) {
    String n = a.getName();
    String v = a.getValue().replaceAll("&", "&amp;").replaceAll("\"", "&quot;");
    return n + "=\"" + v + "\"";
  }

  static String attrsToString(NamedNodeMap a) {
    final int n = a.getLength();
    if (n == 0) return "";
    else if (n == 1) return attrToString((Attr) a.item(0));
    final java.util.ArrayList<java.lang.String> lst = new ArrayList<String>();
    for (int i = 0; i < n; i++) {
      lst.add(attrToString((Attr) a.item(i)));
    }
    Collections.sort(lst);
    return String.join(" ", lst);
  }

  private static int stringCompare(String stringA, String stringB) {
    if (stringA == null) return -1;
    if (stringB == null) return 1;
    return stringA.compareTo(stringB);
  }

  private static final Comparator<Node> nodeComparator =
      (nodeA, nodeB) -> {
        int compareResult = stringCompare(nodeA.getNodeName(), nodeB.getNodeName());
        if (compareResult != 0) return compareResult;
        compareResult = stringCompare(attrsToString(nodeA.getAttributes()), attrsToString(nodeB.getAttributes()));
        if (compareResult != 0) return compareResult;
        return stringCompare(nodeA.getNodeValue(), nodeB.getNodeValue());
      };

  static void sort(Node top) {
    final org.w3c.dom.NodeList children = top.getChildNodes();
    final int childrenCount = children.getLength();
    final java.lang.String name = top.getNodeName();
    // project (contains ordered elements, do not sort)
    // - main
    // - toolbar (contains ordered elements, do not sort)
    //   - tool(s)
    //     - a(s)
    // - lib(s) (contains orderd elements, do not sort)
    //   - tool(s)
    //     - a(s)
    // - options
    //   - a(s)
    // - circuit(s)
    //   - a(s)
    //   - comp(s)
    //   - wire(s)
    if ("appear".equals(name)) {
      // the appearance section only has to sort the circuit ports, the rest is static.
      final java.util.ArrayList<java.lang.Integer> circuitPortIndexes = new ArrayList<Integer>();
      for (int nodeIndex = 0; nodeIndex < childrenCount; nodeIndex++)
        if ("circ-port".equals(children.item(nodeIndex).getNodeName())) circuitPortIndexes.add(nodeIndex);
      if (circuitPortIndexes.isEmpty()) return;
      final int numberOfPorts = circuitPortIndexes.size();
      final org.w3c.dom.Node[] nodeSet = new Node[numberOfPorts];
      for (int portIndex = 0; portIndex < numberOfPorts; portIndex++)
        nodeSet[portIndex] = children.item(circuitPortIndexes.get(portIndex));
      Arrays.sort(nodeSet, nodeComparator);
      for (int portIndex = 0; portIndex < numberOfPorts; portIndex++) top.insertBefore(nodeSet[portIndex], null);
      return;
    }
    if (childrenCount > 1 && !name.equals("project") && !name.equals("lib") && !name.equals("toolbar")) {
      final org.w3c.dom.Node[] nodeSet = new Node[childrenCount];
      for (int nodeIndex = 0; nodeIndex < childrenCount; nodeIndex++) nodeSet[nodeIndex] = children.item(nodeIndex);
      Arrays.sort(nodeSet, nodeComparator);
      for (int nodeIndex = 0; nodeIndex < childrenCount; nodeIndex++) top.insertBefore(nodeSet[nodeIndex], null);
    }
    for (int childId = 0; childId < childrenCount; childId++) {
      sort(children.item(childId));
    }
  }

  static void write(LogisimFile file, OutputStream out, LibraryLoader loader, File destFile, String libraryHome)
      throws ParserConfigurationException, TransformerException {

    final javax.xml.parsers.DocumentBuilderFactory docFactory = XmlUtil.getHardenedBuilderFactory();
    final javax.xml.parsers.DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

    final org.w3c.dom.Document doc = docBuilder.newDocument();
    XmlWriter context;
    if (destFile != null) {
      java.lang.String dstFilePath = destFile.getAbsolutePath();
      dstFilePath = dstFilePath.substring(0, dstFilePath.lastIndexOf(File.separator));
      context = new XmlWriter(file, doc, loader, dstFilePath);
    } else if (libraryHome != null) {
      context = new XmlWriter(file, doc, loader, null, libraryHome);
    } else {
      context = new XmlWriter(file, doc, loader);
    }

    context.fromLogisimFile();

    final javax.xml.transform.TransformerFactory tfFactory = TransformerFactory.newInstance();
    try {
      tfFactory.setAttribute("indent-number", 2);
    } catch (IllegalArgumentException ignored) {
      // Do nothing
    }
    final javax.xml.transform.Transformer tf = tfFactory.newTransformer();
    tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    tf.setOutputProperty(OutputKeys.INDENT, "yes");
    try {
      tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    } catch (IllegalArgumentException ignored) {
      // Do nothing
    }

    doc.normalize();
    sort(doc);
    Source src = new DOMSource(doc);
    Result dest = new StreamResult(out);
    tf.transform(src, dest);
  }

  void addAttributeSetContent(Element elt, AttributeSet attrs, AttributeDefaultProvider source, boolean userModifiedOnly) {
    if (attrs == null) return;
    if (source != null && source.isAllDefaultValues(attrs, BuildInfo.version)) return;
    for (final com.cburch.logisim.data.Attribute<?> attrBase : attrs.getAttributes()) {
      @SuppressWarnings("unchecked")
      final Attribute<Object> attr = (Attribute<Object>) attrBase;
      final java.lang.Object val = attrs.getValue(attr);
      if (userModifiedOnly && (attrs.isReadOnly(attr) || attr.isHidden())) continue;
      if (attrs.isToSave(attr) && val != null) {
        final java.lang.Object dflt = source == null ? null : source.getDefaultAttributeValue(attr, BuildInfo.version);
        final java.lang.String defaultValue = dflt == null ? "" : attr.toStandardString(dflt);
        java.lang.String newValue = attr.toStandardString(val);
        if (dflt == null || (!dflt.equals(val) && !defaultValue.equals(newValue))
            || (attr.equals(StdAttr.APPEARANCE) && !userModifiedOnly)
            || (attr.equals(ProbeAttributes.PROBEAPPEARANCE) && !userModifiedOnly && val.equals(ProbeAttributes.APPEAR_EVOLUTION_NEW))) {
          final org.w3c.dom.Element a = doc.createElement("a");
          a.setAttribute("name", attr.getName());
          if ("filePath".equals(attr.getName()) && outFilePath != null) {
            final java.nio.file.Path outFP = Paths.get(outFilePath);
            final java.nio.file.Path attrValP = Paths.get(newValue);
            newValue = (outFP.relativize(attrValP)).toString();
            a.setAttribute("val", newValue);
          } else {
            if (newValue.contains("\n")) {
              a.appendChild(doc.createTextNode(newValue));
            } else {
              a.setAttribute("val", attr.toStandardString(val));
            }
          }
          elt.appendChild(a);
        }
      }
    }
  }

  Library findLibrary(ComponentFactory source) {
    if (file.contains(source)) return file;
    for (final com.cburch.logisim.tools.Library lib : file.getLibraries()) {
      if (lib.contains(source)) return lib;
    }
    return null;
  }

  Library findLibrary(Tool tool) {
    if (libraryContains(file, tool)) return file;
    for (final com.cburch.logisim.tools.Library lib : file.getLibraries()) {
      if (libraryContains(lib, tool)) return lib;
    }
    return null;
  }

  Element fromCircuit(Circuit circuit) {
    final org.w3c.dom.Element ret = doc.createElement("circuit");
    ret.setAttribute("name", circuit.getName());
    addAttributeSetContent(ret, circuit.getStaticAttributes(), CircuitAttributes.DEFAULT_STATIC_ATTRIBUTES, false);
    if (circuit.getAppearance().hasCustomAppearance()) {
      final org.w3c.dom.Element appear = doc.createElement("appear");
      for (Object obj : circuit.getAppearance().getCustomObjectsFromBottom()) {
        if (obj instanceof AbstractCanvasObject canvasObject) {
          final org.w3c.dom.Element elt = canvasObject.toSvgElement(doc);
          if (elt != null) {
            appear.appendChild(elt);
          }
        }
      }
      ret.appendChild(appear);
    }
    for (final com.cburch.logisim.circuit.Wire wire : circuit.getWires()) {
      ret.appendChild(fromWire(wire));
    }
    for (final com.cburch.logisim.comp.Component comp : circuit.getNonWires()) {
      final org.w3c.dom.Element elt = fromComponent(comp);
      if (elt != null) ret.appendChild(elt);
    }
    for (final java.lang.String board : circuit.getBoardMapNamestoSave()) {
      final org.w3c.dom.Element elt = fromMap(circuit, board);
      if (elt != null) ret.appendChild(elt);
    }
    return ret;
  }

  Element fromVhdl(VhdlContent vhdl) {
    vhdl.aboutToSave();
    final org.w3c.dom.Element ret = doc.createElement("vhdl");
    ret.setAttribute("name", vhdl.getName());
    ret.setTextContent(vhdl.getContent());
    return ret;
  }

  Element fromMap(Circuit circ, String boardName) {
    final org.w3c.dom.Element ret = doc.createElement("boardmap");
    ret.setAttribute("boardname", boardName);
    for (String key : circ.getMapInfo(boardName).keySet()) {
      final org.w3c.dom.Element map = doc.createElement("mc");
      final com.cburch.logisim.circuit.CircuitMapInfo mapInfo = circ.getMapInfo(boardName).get(key);
      if (mapInfo.isOldFormat()) {
        map.setAttribute("key", key);
        if (mapInfo.isOpen()) {
          map.setAttribute(MapComponent.OPEN_KEY, MapComponent.OPEN_KEY);
        } else if (mapInfo.isConst()) {
          map.setAttribute(MapComponent.CONSTANT_KEY, Long.toString(mapInfo.getConstValue()));
        } else {
          final com.cburch.logisim.fpga.data.BoardRectangle rect = mapInfo.getRectangle();
          map.setAttribute("valx", Integer.toString(rect.getXpos()));
          map.setAttribute("valy", Integer.toString(rect.getYpos()));
          map.setAttribute("valw", Integer.toString(rect.getWidth()));
          map.setAttribute("valh", Integer.toString(rect.getHeight()));
        }
      } else {
        final com.cburch.logisim.fpga.data.MapComponent nmap = mapInfo.getMap();
        if (nmap != null)
          nmap.getMapElement(map);
        else {
          map.setAttribute("key", key);
          MapComponent.getComplexMap(map, mapInfo);
        }
      }
      ret.appendChild(map);
    }
    return ret;
  }

  Element fromComponent(Component comp) {
    final com.cburch.logisim.comp.ComponentFactory source = comp.getFactory();
    final com.cburch.logisim.tools.Library lib = findLibrary(source);
    String libName;
    if (lib == null) {
      loader.showError(source.getName() + " component not found");
      return null;
    } else if (lib == file) {
      libName = null;
    } else {
      libName = libs.get(lib);
      if (libName == null) {
        loader.showError("unknown library within file");
        return null;
      }
    }
    if ("Text".equals(source.getName())) {
      /* check if the text element is empty, in this case we do not save */
      final java.lang.String value = comp.getAttributeSet().getValue(Text.ATTR_TEXT);
      if (value.isEmpty()) return null;
    }

    final org.w3c.dom.Element ret = doc.createElement("comp");
    if (libName != null) ret.setAttribute("lib", libName);
    ret.setAttribute("name", source.getName());
    ret.setAttribute("loc", comp.getLocation().toString());
    addAttributeSetContent(ret, comp.getAttributeSet(), comp.getFactory(), false);
    return ret;
  }

  Element fromLibrary(Library lib) {
    final org.w3c.dom.Element ret = doc.createElement("lib");
    if (libs.containsKey(lib)) return null;
    final java.lang.String name = Integer.toString(libs.size());
    java.lang.String desc = loader.getDescriptor(lib);
    if (desc == null) {
      loader.showError("library location unknown: " + lib.getName());
      return null;
    }
    libs.put(lib, name);
    if (isProjectExport || AppPreferences.REMOVE_UNUSED_LIBRARIES.getBoolean()) {
      // first we check if the library is used and if this is not the case we do not add it
      boolean isUsed = false;
      final java.util.List<? extends com.cburch.logisim.tools.Tool> tools = lib.getTools();
      for (final com.cburch.logisim.circuit.Circuit circuit : file.getCircuits()) {
        for (final com.cburch.logisim.comp.Component tool : circuit.getNonWires()) {
          isUsed |= lib.contains(tool.getFactory());
        }
      }
      for (final com.cburch.logisim.tools.Tool tool : file.getOptions().getToolbarData().getContents()) {
        isUsed |= tools.contains(tool);
      }
      for (final java.util.Map.Entry<java.lang.Integer,com.cburch.logisim.tools.Tool> entry : file.getOptions().getMouseMappings().getMappings().entrySet()) {
        isUsed |= tools.contains(entry.getValue());
      }
      if (!isUsed && !"#Base".equals(desc)) {
        return null;
      }
    }
    if (isProjectExport) {
      if (lib instanceof LoadedLibrary) {
        final java.lang.String origFile = LibraryManager.getLibraryFilePath(file.getLoader(), desc);
        if (origFile != null) {
          final java.lang.String[] names = origFile.split(Pattern.quote(File.separator));
          final java.lang.String filename = names[names.length - 1];
          final java.lang.String newFile = String.format("%s%s%s", librariesPath, File.separator, filename);
          try {
            Files.copy(Paths.get(origFile), Paths.get(newFile), StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            //TODO: error message to user
            return null;
          }
          final java.lang.String newFilePath = LineBuffer.format("..{{1}}{{2}}{{1}}{{3}}", File.separator, Loader.LOGISIM_LIBRARY_DIR, filename);
          desc = LibraryManager.getReplacementDescriptor(file.getLoader(), desc, newFilePath);
        }
      }
    }
    ret.setAttribute("name", name);
    ret.setAttribute("desc", desc);
    for (Tool t : lib.getTools()) {
      final com.cburch.logisim.data.AttributeSet attrs = t.getAttributeSet();
      if (attrs != null) {
        final org.w3c.dom.Element toAdd = doc.createElement("tool");
        toAdd.setAttribute("name", t.getName());
        addAttributeSetContent(toAdd, attrs, t, true);
        if (toAdd.getChildNodes().getLength() > 0) {
          ret.appendChild(toAdd);
        }
      }
    }
    return ret;
  }

  Element fromLogisimFile() {
    final org.w3c.dom.Element ret = doc.createElement("project");
    doc.appendChild(ret);
    ret.appendChild(
        doc.createTextNode(
            "\nThis file is intended to be "
                + "loaded by "
                + BuildInfo.displayName
                + "("
                + BuildInfo.url
                + ").\n"));
    ret.setAttribute("version", "1.0");
    ret.setAttribute("source", BuildInfo.version.toString());

    for (final com.cburch.logisim.tools.Library lib : file.getLibraries()) {
      final org.w3c.dom.Element elt = fromLibrary(lib);
      if (elt != null) ret.appendChild(elt);
    }

    if (file.getMainCircuit() != null) {
      final org.w3c.dom.Element mainElt = doc.createElement("main");
      mainElt.setAttribute("name", file.getMainCircuit().getName());
      ret.appendChild(mainElt);
    }

    ret.appendChild(fromOptions());
    ret.appendChild(fromMouseMappings());
    ret.appendChild(fromToolbarData());

    for (final com.cburch.logisim.circuit.Circuit circ : file.getCircuits()) {
      ret.appendChild(fromCircuit(circ));
    }
    for (final com.cburch.logisim.vhdl.base.VhdlContent vhdl : file.getVhdlContents()) {
      ret.appendChild(fromVhdl(vhdl));
    }
    return ret;
  }

  Element fromMouseMappings() {
    final org.w3c.dom.Element elt = doc.createElement("mappings");
    final com.cburch.logisim.file.MouseMappings map = file.getOptions().getMouseMappings();
    for (final java.util.Map.Entry<java.lang.Integer,com.cburch.logisim.tools.Tool> entry : map.getMappings().entrySet()) {
      final java.lang.Integer mods = entry.getKey();
      final com.cburch.logisim.tools.Tool tool = entry.getValue();
      final org.w3c.dom.Element toolElt = fromTool(tool);
      final java.lang.String mapValue = InputEventUtil.toString(mods);
      toolElt.setAttribute("map", mapValue);
      elt.appendChild(toolElt);
    }
    return elt;
  }

  Element fromOptions() {
    final org.w3c.dom.Element elt = doc.createElement("options");
    addAttributeSetContent(elt, file.getOptions().getAttributeSet(), null, false);
    return elt;
  }

  Element fromTool(Tool tool) {
    final com.cburch.logisim.tools.Library lib = findLibrary(tool);
    String libName;
    if (lib == null) {
      loader.showError(String.format("tool `%s' not found", tool.getDisplayName()));
      return null;
    } else if (lib == file) {
      libName = null;
    } else {
      libName = libs.get(lib);
      if (libName == null) {
        loader.showError("unknown library within file");
        return null;
      }
    }

    final org.w3c.dom.Element elt = doc.createElement("tool");
    if (libName != null) elt.setAttribute("lib", libName);
    elt.setAttribute("name", tool.getName());
    addAttributeSetContent(elt, tool.getAttributeSet(), tool, true);
    return elt;
  }

  Element fromToolbarData() {
    final org.w3c.dom.Element elt = doc.createElement("toolbar");
    final com.cburch.logisim.file.ToolbarData toolbar = file.getOptions().getToolbarData();
    for (final com.cburch.logisim.tools.Tool tool : toolbar.getContents()) {
      if (tool == null) {
        elt.appendChild(doc.createElement("sep"));
      } else {
        elt.appendChild(fromTool(tool));
      }
    }
    return elt;
  }

  Element fromWire(Wire w) {
    final org.w3c.dom.Element ret = doc.createElement("wire");
    ret.setAttribute("from", w.getEnd0().toString());
    ret.setAttribute("to", w.getEnd1().toString());
    return ret;
  }

  boolean libraryContains(Library lib, Tool query) {
    for (final com.cburch.logisim.tools.Tool tool : lib.getTools()) {
      if (tool.sharesSource(query)) return true;
    }
    return false;
  }
}
