/*
Copyright [2022] [Cardiff University]
*/


package accord.regulationsimporter;


import org.dcom.core.compliancedocument.*;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dcom.core.compliancedocument.utils.TextExtractor;
import org.dcom.core.compliancedocument.inline.InlineItem;
import org.dcom.core.compliancedocument.inline.InlineString;
import org.dcom.core.compliancedocument.inline.RASETag;
import org.dcom.core.compliancedocument.inline.RASEBox;

/**
*A helper class that takes an JSON-LD string and produces an in memory compliance document.
*/
public class OntologyComplianceDocumentDeserialiser {

  private static final Logger LOGGER = LoggerFactory.getLogger( OntologyComplianceDocumentDeserialiser.class );

  private OntologyComplianceDocumentDeserialiser() {
    
  }

  private static <T> ArrayList<T> safeArrayList(Object o) {
    if (o==null) return new ArrayList<T>();
    if (o.getClass().getName().equals("java.util.ArrayList")) {
      return (ArrayList<T>) o;
    }
    ArrayList newList = new ArrayList();
    newList.add(o);
    return newList;
  }
  
  public static ComplianceDocument parseComplianceDocument(String docString) {
    try {
      Genson genson=new GensonBuilder().create();
      HashMap<String, Object> data = (HashMap<String, Object>)genson.deserialize(docString, HashMap.class);
      ComplianceDocument document = new ComplianceDocument();
      parseMetaData(document,data);
      if (data.containsKey("hasPart")) {
        ArrayList<Object> sections =safeArrayList(data.get("hasPart"));
        for (int i=0; i < sections.size();i++) document.addSection(parseSection((HashMap<String,Object>)sections.get(i),document));
      }
      LOGGER.trace("Deserialising "+document);
      return document;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static Section parseSection(HashMap<String,Object> data, ComplianceItem _parent) {
    Section section=new Section(_parent);
    parseMetaData(section,data);
    List<InlineItem> inlineItems = new ArrayList<InlineItem>();
    ArrayList<Object> types = safeArrayList(data.get("$type"));
    String type ="";
    if (types.contains("RequirementStatement")) type ="RequirementSection";
    if (types.contains("ApplicationStatement")) type ="ApplicationSection";
    if (types.contains("ExceptionStatement")) type ="ExceptionSection";
    if (types.contains("SelectionStatement")) type ="SelectionSection";
    section.setMetaData("raseType",type); 

    if (data.containsKey("hasPart")) {
      ArrayList<Object> subItems =  safeArrayList(data.get("hasPart"));
      for (int i=0; i < subItems.size();i++){
        HashMap<String,Object> subItem = (HashMap<String,Object>)subItems.get(i);        
        ArrayList<String> subTypes = safeArrayList(subItem.get("$type"));
        if (subTypes.contains("DocumentSubdivision")) section.addSection(parseSection(subItem,section));
        else section.addParagraph(parseParagraph(subItem,section));
      }
    }
    LOGGER.trace("Deserialising "+section);
    return section;
  }

  private static Paragraph parseParagraph(HashMap<String,Object> e,ComplianceItem _parent) {
    Paragraph paragraph=new Paragraph(_parent);
    parseMetaData(paragraph,e);
    List<InlineItem> inlineItems = new ArrayList<InlineItem>();
    ArrayList<Object> types = safeArrayList(e.get("$type"));
    if (e.containsKey("asText")) paragraph.setInlineItems(TextExtractor.extractStructure(e.get("asText").toString()));
    String type ="";
    if (types.contains("RequirementStatement")) type ="RequirementSection";
    if (types.contains("ApplicationStatement")) type ="ApplicationSection";
    if (types.contains("ExceptionStatement")) type ="ExceptionSection";
    if (types.contains("SelectionStatement")) type ="SelectionSection";
    paragraph.setMetaData("raseType",type); 
    if (e.containsKey("hasPart")) {
      ArrayList<Object> parts =  safeArrayList(e.get("hasPart"));
      for (int i=0; i < parts.size();i++) {
        HashMap<String,Object> part = (HashMap<String,Object>)parts.get(i);
        ArrayList<String> subTypes = safeArrayList(part.get("$type"));
        
        if (subTypes.contains("Container")) paragraph.addInsert(parseInsert(part,paragraph));
        else paragraph.addParagraph(parseParagraph(part,paragraph));
      }
    }
    if (e.containsKey("hasInlinePart")) {
      ArrayList<Object> inLineParts =  safeArrayList(e.get("hasInlinePart"));
      for (int i=0; i < inLineParts.size();i++) inlineItems.add(parseInlineItem((HashMap<String,Object>)inLineParts.get(i)));
    }
    paragraph.setInlineItems(inlineItems);
    LOGGER.trace("Deserialising "+paragraph);
    return paragraph;
  }

  private static InlineItem parseInlineItem(HashMap<String,Object> e){
    ArrayList<Object> types = safeArrayList(e.get("$type"));
    if (e.containsKey("hasInlinePart"))
    {
       // is a RASE Box
      String type ="";
      if (types.contains("RequirementStatement")) type ="RequirementSection";
      if (types.contains("ApplicationStatement")) type ="ApplicationSection";
      if (types.contains("ExceptionStatement")) type ="ExceptionSection";
      if (types.contains("SelectionStatement")) type ="SelectionSection";
      RASEBox box = new RASEBox(type,(String)e.get("identifier"));
      ArrayList<Object> inLineParts =  safeArrayList(e.get("hasInlinePart"));
      for (int i=0; i < inLineParts.size();i++) box.addSubItem(parseInlineItem((HashMap<String,Object>)inLineParts.get(i)));
      return box;
    } else if (types.contains("RequirementStatement") || types.contains("ApplicationStatement") || types.contains("ExceptionStatement") || types.contains("SelectionStatement")) {
      String type ="";
      if (types.contains("RequirementStatement")) type ="Requirement";
      if (types.contains("ApplicationStatement")) type ="Application";
      if (types.contains("ExceptionStatement")) type ="Exception";
      if (types.contains("SelectionStatement")) type ="Selection";
      
      RASETag tag;
      String references;
      if (e.containsKey("references")) references = ""+e.get("references");
      else references ="";
      // now parse the rule
      if (e.containsKey("isOperationalizedBy")) {
        Object o = e.get("isOperationalizedBy");
        if (o instanceof ArrayList) {
          o = ((ArrayList)o).get(0);
        }
        String rule = parseRule((HashMap<String,Object>)o); 
        //System.out.println(rule);
         tag = new RASETag(type,rule,"", "", "", ""+e.get("identifier"), ""+e.get("asText")+" ",references);
      } else  tag = new RASETag(type,"","", "", "", ""+e.get("identifier"), ""+e.get("asText")+" ",references);
      return tag;
    } else {
      //just text
      InlineString string = new InlineString(""+e.get("identifier"),""+e.get("asText")+" ");
      return string;
    }
  }

  private static String parseRule(HashMap<String,Object> e){
      String operator=null;
      if (e.containsKey("hasOperator"))  operator=genOperator(""+e.get("hasOperator"));
      if (e.containsKey("hasComparator"))  operator=genOperator(""+e.get("hasComparator"));
      String unit = "";
      if (e.containsKey("hasUnit")) {
        if (e.get("hasUnit") instanceof HashMap) { 
          HashMap<String,Object> inner = (HashMap<String,Object>)e.get("hasUnit");
          unit = inner.get("$id").toString();
        } else unit = (String)e.get("hasUnit");
        unit = unit.replace("units:",":");
      }
      String target="";
      if (e.containsKey("hasNestedTarget")) {
            if (e.get("hasNestedTarget") instanceof ArrayList) { 
               ArrayList<Object> params = (ArrayList<Object>) e.get("hasNestedTarget");
               for (Object p: params) {
                  HashMap<String,Object> inner = (HashMap<String,Object>)p;
                  target = parseRule(inner);
                  break;
               }
            } else {
              HashMap<String,Object> inner = (HashMap<String,Object>)e.get("hasNestedTarget");
              target = parseRule(inner);
            }
      } else if (e.containsKey("hasBSDDTarget")) {
            if (e.get("hasBSDDTarget") instanceof ArrayList) {
              e.put("hasBSDDTarget",((ArrayList<?>)e.get("hasBSDDTarget")).get(0));
            }
            if (e.get("hasBSDDTarget") instanceof HashMap) {
              HashMap<String,Object> inner = (HashMap<String,Object>)e.get("hasBSDDTarget");
              target = (""+inner.get("$id"));
            } else target = (String)e.get("hasBSDDTarget");
            target = target.replace("terms:",":").replace("functions:",":");

      } else if (e.containsKey("hasTarget")) {
            target = (""+e.get("hasTarget"));
      }
      String value="";
      if (e.containsKey("hasNestedValue")) {
        ArrayList<Object> params =  safeArrayList(e.get("hasNestedValue"));
        if (operator==null) {
          //function
          String rule = target + "(";
          boolean first = true;
          for (Object p: params) {
              if (!first) rule+=",";
              rule +=parseRule((HashMap<String,Object>)p);
              if (first) first = false;
          }
          rule+=")";
          return rule;
        } else if (operator.equals("exists") || operator.equals("not exists") || operator.equals("forall")) {
          // first order login operation
          String rule = target + " " + operator; 
          if (params.size() > 1) {
            rule +="("+parseRule((HashMap<String,Object>)params.get(0))+")";
          }
          rule += " =>("+parseRule((HashMap<String,Object>)params.get(0))+")";
          return rule;
        } else {
         value = parseRule((HashMap<String,Object>)params.get(0));
        }
      } else if (e.containsKey("hasBSDDValue")) {
            if (e.get("hasBSDDValue") instanceof HashMap) {
              HashMap<String,Object> inner = (HashMap<String,Object>)e.get("hasBSDDValue");
              value = (""+inner.get("$id"));
            } else value = (String)e.get("hasBSDDValue");

            value = value.replace("terms:",":").replace("functions:",":");
      } else if (e.containsKey("hasValue")) {
          value = (""+e.get("hasValue"));
          value = escapeString(value);
      }
      String rule;
      rule = target +" " +operator +" "+value + " " +unit;
      return rule;
  }

  private static String escapeString(String value) {
    try {
      Double.parseDouble(value);
      return value;
    } catch (Exception e) {}
    try {
      Integer.parseInt(value);
      return value;
    } catch (Exception e) {}
    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return value;
    return "\""+value+"\"";
  }

  private static String genOperator(String op){
      switch(op) {
        case "CheckMethodComparator-eq": return "==";
        case "CheckMethodComparator-neq": return "!=";
        case "CheckMethodOperator-exists": return "exists";
        case "CheckMethodOperator-notExists": return "not exists";
        case "CheckMethodOperator-forall": return "forall";
        case "CheckMethodComparator-gt": return ">";
        case "CheckMethodComparator-lt": return "<";
        case "CheckMethodComparator-ge": return ">=";
        case "CheckMethodComparator-le": return "<=";
        case "CheckMethodComparator-logicalAND": return "&&";
        case "CheckMethodComparator-logicalOR": return "||";                 
        case "CheckMethodOperator-addition": return "+";    
        case "CheckMethodOperator-subtraction": return "-";    
        case "CheckMethodOperator-division": return "/";    
        case "CheckMethodOperator-multiplication": return "*";    

        default:
          System.out.println("Unknown OP:"+op);
          return op;
      }

    }

  private static Insert parseInsert(HashMap<String,Object> e,ComplianceItem _parent){
      //get the actual thing not the container
      if ( !(e.get("contains") instanceof HashMap)) return new Table(_parent);
      e = (HashMap<String,Object>)e.get("contains");
      if (e.containsKey("asText")) {
        //its an image
        Figure i=new Figure(_parent);
        parseMetaData(i,e);
        i.setImageData(e.get("asText").toString());
        LOGGER.trace("Deserialising "+i);
        return i;
      } else {
        //its a table
        Table t=new Table(_parent);
        parseMetaData(t,e);
        TableBody tg = new TableBody(t);
        t.setBody(tg);
        if (e.containsKey("hasPart")) {
          ArrayList<Object> listRows=safeArrayList(e.get("hasPart"));
          for (int i=0; i < listRows.size();i++) {
            HashMap<String,Object> rowData=(HashMap<String,Object>)(listRows.get(i));
            Row row=new Row(tg);
            parseMetaData(row,rowData);
            tg.addRow(row);
            ArrayList<Object> listCells=safeArrayList(rowData.get("hasPart"));
            for (int x=0; x < listCells.size();x++) {
              HashMap<String,Object> cellData=(HashMap<String,Object>)(listCells.get(x));
              Cell c=new DataCell(row);
              parseMetaData(c,cellData);
              Paragraph p = new Paragraph(c);
              ArrayList<Object> inLineParts =  safeArrayList(cellData.get("hasInlinePart"));
              List<InlineItem> inlineItems = new ArrayList<InlineItem>();
              if (cellData.containsKey("asText")) {
                inlineItems.add(new InlineString("0",cellData.get("asText").toString()));
              }
              if (inLineParts != null) for (int j=0; j < inLineParts.size();j++) inlineItems.add(parseInlineItem((HashMap<String,Object>)inLineParts.get(j)));
              p.setInlineItems(inlineItems);
              c.setBody(p);
              row.addCell(c);
            }
          }
        }
        LOGGER.trace("Deserialising "+t);
        return t;
      }
  }
  private static void parseMetaData(ComplianceItem item, HashMap<String,Object> data) {
        for (String mD:data.keySet()) {
            switch (mD) {
              case "identifier":
                String identifier = "" + data.get(mD);
                if (identifier.contains(".")) {
                  String[] identifierSplit = identifier.split("\\.");
                  identifier = identifierSplit[identifierSplit.length-1];
                }
                item.setMetaData("dcterms:identifier",identifier);
                break;
              case "title":
                item.setMetaData("dcterms:title",""+data.get(mD));
                break;
              case "references":
                item.setMetaData("dcterms:references",""+data.get(mD));
                break;
              case "relation":
                item.setMetaData("dcterms:relation",""+data.get(mD));
                break;
              case "subject":
                item.setMetaData("dcterms:subject",""+data.get(mD));
                break;
              case "caption":
                item.setMetaData("caption",""+data.get(mD));
                break;
              case "coverage":
                item.setMetaData("dcterms:coverageSpatial",""+data.get(mD));
                break;
              case "issued":
                item.setMetaData("dcterms:dateCreated",""+data.get(mD));
                break;
              case "modified":
                item.setMetaData("dcterms:modified",""+data.get(mD));
                break;
            }
        }
  }
}