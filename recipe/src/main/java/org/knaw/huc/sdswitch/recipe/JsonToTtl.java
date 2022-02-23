package org.knaw.huc.sdswitch.recipe;

import mjson.Json;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class JsonToTtl {

  static String rdfSubject = "";
  static String rdfType = "";
  static Map<String,String> predicates = Collections.EMPTY_MAP;


  public static String jsonToTtl(String json) {
    Document schema = readSchema();
    Json jsonObject = Json.read(json);
    String id = "" + jsonObject.at("id").getValue();
    rdfSubject = rdfSubject.replace("{id}",id);
    String ttl = "<" + rdfSubject + "> a <" + rdfType + ">";
    for (Map.Entry<String, String> entry : predicates.entrySet()) {
      ttl += ";\n  <" + entry.getKey() + "> \"" + entry.getValue();
    }
    ttl += ".";
    return ttl;
  }

  public static Document readSchema() {
    String filename = "src/main/resources/schema_raa.xml";
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    Document doc = null;

    try {
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      doc = db.parse(new File(filename));
      NodeList list = doc.getElementsByTagName("persoon");
      Node node = list.item(0);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) node;
        rdfSubject = element.getAttribute("rdf:subject");
        rdfType = element.getAttribute("rdf:type");
        NodeList children = node.getChildNodes();
        // for (int temp = 0; temp < children.getLength(); temp++) {
        //   short nodeType = children.item(temp).getNodeType();
        //   Element child = (Element) children.item(temp);
        //   String nodePredicate = child.getAttribute("rdf:predicate");
        //   String nodeName = child.getNodeName();
        //   predicates.put(nodeName, nodePredicate);
        // }
      }
    } catch (ParserConfigurationException | SAXException | IOException e) {
      e.printStackTrace();
    }
    return doc;
  }
}
