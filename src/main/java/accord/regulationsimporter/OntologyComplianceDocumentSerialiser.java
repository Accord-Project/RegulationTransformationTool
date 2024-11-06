/*
Copyright [2022] [Cardiff University]
*/


package accord.regulationsimporter;

import org.dcom.core.compliancedocument.*;
import org.dcom.core.compliancedocument.inline.*;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import com.owlike.genson.stream.ObjectWriter;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.HashMap;
import java.text.NumberFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;


/**
*A helper class that takes an in memory compliance document and produces a JSON-LD string.
*/
public class OntologyComplianceDocumentSerialiser {

    private static ObjectWriter writer;
    private static final Logger LOGGER = LoggerFactory.getLogger( OntologyComplianceDocumentSerialiser.class );
    private static boolean STATIC_ID_MODE = true;
    private static int figureCount=1;
    private static int tableCount=1;
    private static String base ="";
    private static boolean _simpleRules;
    private static boolean _noExecution;
    private static String _filterPath;
    private static boolean order;
      private static HashSet<String> usedIds = new HashSet<String>(); 

    private OntologyComplianceDocumentSerialiser() {
      
    }

    private static boolean filterPath (String identifier, String path) {
      if (identifier == null || path == null) return false;
      if (path.equals(identifier)) return false;
      path=path+".";
      if (!path.startsWith(identifier) && !identifier.startsWith(path)) return true;
      return false;
    }

    // simple rules means we output the text of the rule not the nested structure
    // flat means we eliminate all nesting apart from those executable
    public static String serialise(ComplianceDocument document,String ontologyURI, boolean simpleRules, boolean noExecution, String filterPath) {
      return serialise(document,ontologyURI,simpleRules,noExecution,filterPath,true);
    }
    
    public static String serialise(ComplianceDocument document,String ontologyURI, boolean simpleRules, boolean noExecution, String filterPath,boolean _order) {
        LOGGER.trace("Serialising "+document);
        order=_order;
        if (simpleRules) System.out.println("With Simple Rules");
        _simpleRules = simpleRules;
        _filterPath = filterPath;
        Genson genson=new GensonBuilder().create();
        ByteArrayOutputStream outputStream=new ByteArrayOutputStream();
        writer=genson.createWriter(outputStream);
        writer.beginObject();
        base="";
        if (document.getMetaDataString("dcterms:coverageSpatial") != null) base += document.getMetaDataString("dcterms:coverageSpatial")+"/";
        base +=document.getMetaDataString("dcterms:title").replace(" ","_")+"/";
        writer.writeString("@base","https://regulations.accordproject.eu/");
        writer.writeName("@context").beginArray().writeString("https://w3id.org/lbd/aec3po/aec3po.jsonld").beginObject();
        writer.writeString("terms","https://identifier.buildingsmart.org/uri/accord/ACCORD-1.0/");
        writer.writeString("functions","https://functions.accordproject.eu/").endObject().endArray();
        writer.writeName("$type").beginArray().writeString("Document").endArray();
        serialiseMetadata(document);
        String identifier="";
        if (document.hasMetaData("dcom:startSectionNumber")) {
          STATIC_ID_MODE = false;
          identifier=document.getMetaDataString("dcom:startSectionNumber");
        }
        writer.writeString("identifier",document.getMetaDataString("dcterms:title").replace(" ","_"));
        writer.writeString("$id",base.substring(0,base.lastIndexOf("/")));
        if (document.getNoSections() > 0) {
          writer.writeName("hasPart").beginArray();
          List<Section> sections = document.getSections();
          if (order) Collections.sort(sections,new PartOrderer());
          for (int i=0; i < sections.size();i++) serialiseSection(sections.get(i),identifier,i+1);
          writer.endArray();
        }
        writer.endObject();
        writer.flush();
        return outputStream.toString();
    }

    private static void serialiseSection(Section s, String identifier, int newNumber) {
        LOGGER.trace("Serialising "+s);
        if (STATIC_ID_MODE && s.hasMetaData("dcterms:identifier") && identifier.equals("")) identifier = s.getMetaDataString("dcterms:identifier");
        else if (STATIC_ID_MODE && s.hasMetaData("dcterms:identifier")) identifier = identifier + "." +s.getMetaDataString("dcterms:identifier");
        else if (identifier.equals("")) identifier = ""+newNumber;
        else identifier = identifier+"."+newNumber;
        boolean skip = filterPath(identifier,_filterPath);
        if (skip) return;
        writer.beginObject();
        writer.writeString("$id",base+identifier);
        if (usedIds.contains(identifier)) System.out.println("Duplicate ID:"+identifier);
        else usedIds.add(identifier);
        writer.writeString("identifier",identifier);
        writer.writeName("$type").beginArray().writeString("DocumentSubdivision");
        if (s.hasMetaData("raseType") && !s.getMetaDataString("raseType").equals("")) writer.writeString(s.getMetaDataString("raseType").replace("Section","Statement"));
        writer.endArray();
       
        serialiseMetadata(s);
        if (s.getNoSubItems() > 0) {
          writer.writeName("hasPart").beginArray();
          List<ComplianceItem> subItems = s.getSubItems();
          if (order) Collections.sort(subItems,new PartOrderer());
          int i=0;
          for (ComplianceItem subItem: subItems) {
            if (subItem instanceof Section ) serialiseSection((Section)subItem,identifier,i+1);
            else if (subItem instanceof Paragraph ) serialiseParagraph((Paragraph)subItem,identifier,i+1);
            i++;
          }
          writer.endArray();
        }
        writer.endObject();
    }
  
    private static void serialiseParagraph(Paragraph p,String identifier, int newNumber) {
        LOGGER.trace("Serialising "+p);
        
        if (STATIC_ID_MODE && p.hasMetaData("dcterms:identifier") && identifier.equals("")) identifier = p.getMetaDataString("dcterms:identifier");
        else if (STATIC_ID_MODE && p.hasMetaData("dcterms:identifier")) identifier = identifier + "." +p.getMetaDataString("dcterms:identifier");
        else if (identifier.equals("")) identifier = ""+newNumber;
        else identifier = identifier +"."+newNumber;
        
        boolean skip = filterPath(identifier,_filterPath);
        if (skip) return;
        
        writer.beginObject();
        serialiseMetadata(p);
        writer.writeName("$type").beginArray();
        if (p.getNoParagraphs()==0) writer.writeString("Statement");
        else writer.writeString("ChecklistStatement");
        if (p.hasMetaData("raseType") && !p.getMetaDataString("raseType").equals("")) writer.writeString(p.getMetaDataString("raseType").replace("Section","Statement"));
        writer.endArray();
        
        writer.writeString("$id",base+identifier);
        if (usedIds.contains(identifier)) System.out.println("Duplicate ID:"+identifier);
        else usedIds.add(identifier);
        writer.writeString("identifier",identifier);
        serialiseInlineItems(p,identifier);

        if (p.getNoSubItems()> 0) {
          writer.writeName("hasPart").beginArray();
          
          List<ComplianceItem> subItems = p.getSubItems();
          if (order) Collections.sort(subItems,new PartOrderer());
          int i=0;

          for (ComplianceItem subItem: subItems) {
            if (subItem instanceof Table || subItem instanceof Figure) serialiseInsert((Insert)subItem,identifier,i+1);
            else if (subItem instanceof Paragraph) serialiseParagraph((Paragraph)subItem,identifier,i+1);
            i++;
          }
          writer.endArray();
        }
        writer.endObject();      
    }

    private static void serialiseInsert(Insert i,String identifier, int newNumber) {
        LOGGER.trace("Serialising "+i);
        writer.beginObject();
        writer.writeName("$type").beginArray().writeString("Container").endArray();
        writer.writeString("$id",base+identifier+"_Container_"+newNumber);
        writer.writeName("contains");
        writer.beginObject();
        if (i instanceof Table) {
            writer.writeName("$type").beginArray().writeString("Table").endArray();
            String actualIdent;
            if (STATIC_ID_MODE) {
              String caption = i.getMetaDataString("caption");
              if (caption == null) caption="";
              actualIdent = identifier+"_"+caption.split(":")[0].replace(" ","_").replace(".","_")+"Table";
            }

            else actualIdent = identifier+"_"+"Table"+tableCount;
            writer.writeString("$id",base+actualIdent.replaceAll(" ","_"));
            if (usedIds.contains(actualIdent.replaceAll(" ","_"))) System.out.println("Duplicate ID:"+actualIdent.replaceAll(" ","_"));
            else usedIds.add(actualIdent.replaceAll(" ","_"));
            writer.writeString("identifier",actualIdent.replaceAll(" ","_"));
            tableCount++;
            serialiseMetadata(i);
            Table t=(Table)i;
            writer.writeName("hasPart").beginArray();
            int rows = 1;
            if (t.getHeader()!=null) rows+=serialiseTableGroup(t.getHeader(),actualIdent);
            if (t.getBody()!=null) rows+=serialiseTableGroup(t.getBody(),actualIdent);
            if (t.getFooter()!=null) rows+=serialiseTableGroup(t.getFooter(),actualIdent);
            writer.endArray();
            
        } else if (i instanceof Figure) {
          writer.writeName("$type").beginArray().writeString("Image").endArray();
          String actualIdent;

          if (STATIC_ID_MODE) actualIdent = i.getMetaDataString("caption").split(":")[0];
          else actualIdent = "Table"+figureCount;
          writer.writeString("$id",base+actualIdent.replaceAll(" ","_")+"Table");
          writer.writeString("identifier",actualIdent.replaceAll(" ","_")+"Table");
          figureCount++;
          serialiseMetadata(i);
          String imageData=((Figure)i).getImageDataString();
          if (imageData!=null) writer.writeString("asText",imageData);
        }
        writer.endObject();
        writer.endObject();
    }

    private static int serialiseTableGroup(TableGroup g,String id) {
        LOGGER.trace("Serialising "+g);
        List<Row> rows = g.getRows();
        if (order) Collections.sort(rows,new PartOrderer());
        for (int i=0; i < rows.size();i++) {
          Row r=rows.get(i);
          writer.beginObject();
          writer.writeName("$type").beginArray().writeString("Row").endArray();
          writer.writeString("identifier",id+"."+i+1);
          writer.writeString("$id",base+id+"."+(i+1));
          serialiseMetadata(r);
          writer.writeName("hasPart").beginArray();
          List<Cell> cells = r.getCells();
          if (order) Collections.sort(cells,new PartOrderer());
          for (int z=0; z< cells.size();z++) {
            writer.beginObject();
            writer.writeName("$type").beginArray().writeString("Cell").endArray();
            writer.writeString("identifier",id+"."+z+1);
            writer.writeString("$id",base+id+"."+(i+1)+"."+(z+1));
            serialiseMetadata(cells.get(z));
            serialiseInlineItems(cells.get(z).getBody(),id+"."+i+"."+z);
            writer.endObject();
          }
          writer.endArray().endObject();

        }
        return g.getNoRows();
    }

    private static void serialiseInlineItems(Paragraph p,String id) {
       if (p.getNoInlineItems()==0) return;
       if (p.getNoInlineItems() == 1 && p.getInlineItem(0) instanceof InlineString) {
          //special case if just one inline item
          List<String> types = serialiseInlineItem(p.getInlineItem(0),id,1,true);
        } else {
          List<InlineItem> inlineItems = p.getInlineItems();
          if (order) Collections.sort(inlineItems,new InlinePartOrderer());
          writer.writeName("hasInlinePart").beginArray();
          for (int k=0; k < inlineItems.size();k++) {
            writer.beginObject();
            List<String> types = serialiseInlineItem(inlineItems.get(k),id,k+1,false);
            writer.writeName("$type").beginArray();
            for (String t: types) writer.writeString(t);
            writer.endArray().endObject();
          }
          writer.endArray();
        }
    }

    private static List<String> serialiseInlineItem(InlineItem i, String id, int ident, boolean singular) {
      List<String> types = new ArrayList<String>();
      LOGGER.trace("Serialising "+i);
      if (i instanceof RASEBox) {
        RASEBox box = (RASEBox) i;
        types.add("CheckStatement");
        if (box.getType() == RASEBox.REQUIREMENT_SECTION) types.add("RequirementStatement");
        else if (box.getType() == RASEBox.APPLICATION_SECTION) types.add("ApplicationStatement");
        else if (box.getType() == RASEBox.EXCEPTION_SECTION) types.add("ExceptionStatement");
        else if (box.getType() == RASEBox.SELECTION_SECTION) types.add("SelectionStatement");
        String identString = ""+ident;
        if (i.getId()!=null && !i.getId().equals("")) {
          writer.writeString("$id",base+id+"."+i.getId());
           if (usedIds.contains(id+"."+i.getId())) System.out.println("Duplicate ID:"+id+"."+i.getId());
          else usedIds.add(id+"."+i.getId());
          writer.writeString("identifier",id+"."+i.getId());
          identString = i.getId();
        } else {
           if (usedIds.contains(base+id+"."+identString)) System.out.println("AADuplicate ID:"+base+id+"."+identString);
          else usedIds.add(base+id+"."+identString);
          writer.writeString("$id",base+id+"."+identString);
          writer.writeString("identifier",id+"."+identString);
        }
        writer.writeName("hasInlinePart").beginArray();
        List<InlineItem> inlineItems = box.getAllSubItems();
        if (order) Collections.sort(inlineItems,new InlinePartOrderer());
        for (int x=0; x < inlineItems.size();x++) {
          writer.beginObject();
          List<String> innerTypes = serialiseInlineItem(inlineItems.get(x),id+"."+identString,x+1,false);
          writer.writeName("$type").beginArray();
          for (String t: innerTypes) writer.writeString(t);
          writer.endArray();
          
          writer.endObject();
        }
        writer.endArray();
      }
      else if (i instanceof RASETag) {
        RASETag tag = (RASETag) i;
        types.add("CheckStatement");
        if (tag.getType() == RASETag.REQUIREMENT) types.add("RequirementStatement");
        else if (tag.getType() == RASETag.APPLICATION) types.add("ApplicationStatement");
        else if (tag.getType() == RASETag.EXCEPTION) types.add("ExceptionStatement");
        else if (tag.getType() == RASETag.SELECTION) types.add("SelectionStatement");
        writer.writeString("asText",tag.getBody().trim());
        if (i.getId()!=null) {
          if (usedIds.contains(id+"."+i.getId())) {
             writer.writeString("$id",base+id+"."+ident);
             writer.writeString("identifier",id+"."+ident);
          }
          else {
            usedIds.add(id+"."+i.getId());
            writer.writeString("$id",base+id+"."+i.getId());
            writer.writeString("identifier",id+"."+i.getId());
          }
        
        } else {
          if (usedIds.contains(id+"."+ident)) System.out.println("BBDuplicate ID:"+id+"."+ident);
          else usedIds.add(id+"."+ident);
          writer.writeString("$id",base+id+"."+ident);
          writer.writeString("identifier",id+"."+ident);
        }
        if (tag.getReferences() != "") {
            writer.writeString("references",tag.getReferences());
        }
        if (tag.getProperty() != "") {
          String raseText=":"+tag.getSanitisedProperty().replace("  "," ").replace("&amp;","&").replace(" ","_").replace("(","_").replace(")","_").replace("-","_").replace(",","").replace("+","").replace("/","_").replace(".","_")+" "+tag.getComparator()+" ";
         
          try {
              NumberFormat.getInstance().parse(tag.getValue()).getClass().getName();
              raseText+=tag.getValue();  
          } catch (Exception e) {
            if (tag.getValue().equalsIgnoreCase("true") || tag.getValue().equalsIgnoreCase("false")) raseText+=tag.getValue(); 
            else raseText+="\""+tag.getValue()+"\"";  
          }
          raseText+=" "+tag.getUnit();
         
          //System.out.println(tag.getProperty());
          AccordParser.AccordRulesContext tree = getTree(tag.getProperty());

          if (tree==null) {
            System.out.println("FAILED: "+tag.getProperty()+" -> "+raseText);
            tree = getTree(raseText);
          }

          if (!_noExecution) {
            writer.writeName("isOperationalizedBy");
            if (!_simpleRules) AccordExpressionWalker.parse(tree,writer,base+id+"."+ident+"_"+"method");
            else {
              writer.beginObject();
              writer.writeName("$type").beginArray().writeString("DeclarativeCheckMethod").endArray();
              writer.writeString("$type","DeclarativeCheckMethod");
              writer.writeString("$id",base+id+"."+ident+"_"+"method");
              writer.writeString("asText",tag.getProperty());
              writer.endObject();
            }
          }
         
        }

      } 
      else if (i instanceof InlineString) {
        InlineString str = (InlineString) i;
        if (!singular) {
          if (usedIds.contains(id+"."+ident)) System.out.println("Duplicate ID:"+id+"."+ident);
          else usedIds.add(id+"."+ident);
          writer.writeString("$id",base+id+"."+ident);
          writer.writeString("identifier",id+"."+ident);
        }
        writer.writeString("asText",str.generateText(true).trim());
        types.add("Statement");
      }
      return types;
    }

    private static AccordParser.AccordRulesContext getTree(String text) {
        AccordLexer lexer  = new AccordLexer(CharStreams.fromString(text));   
        TokenStream tokenStream = new CommonTokenStream(lexer);
        AccordParser parser = new AccordParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(new ErrorListener());
        try {
            AccordParser.AccordRulesContext tree = parser.accordRules(); 
            return tree;
        } catch (Exception e) {
          return null;
        }
    }

    private static String mapMetaData(String inName) {
      switch(inName) {
        case "dcterms:identifier":
          return null;
        case "raseId":
          return null;
        case "raseType":
          return null;
        case "numbered":
          return null;
        case "dcterms:title":
          return "title";
        case "dcterms:modified":
          return "modified";
        case "dcterms:dateCreated":
          return "issued";
        case "dcterms:language":
          return null;
        case "dcterms:relation":
          return "relation";
        case "caption":
          return "caption";
        case "dcterms:type":
          return null;
        case "dcterms:coverage.spatial":
          return "coverage";
        case "dcterms:coverage.temporal":
          return "temporal";
        case "dcterms:subject":
          return "subject";
        case "dcterms:sector":
          return null;
        case "dcterms:references":
          return "references";
        default:
          System.out.println("Unknown:"+inName);
          return null;
      }

    } 

    private static void serialiseMetadata(ComplianceItem i) {
        boolean hasId=false;
        for(String mDName: i.getMetaDataList()) {
          String newName = mapMetaData(mDName);
          if (newName ==null) continue;
          if (i.isListMetadata(mDName)){
            writer.writeName(newName).beginArray();
            ArrayList<String> data=i.getMetaDataList(mDName);
            for (String d:data) {
              if (d.equals("")) continue;
              writer.writeString(d);
            }
            writer.endArray();
          }else {
            if (i.getMetaDataString(mDName).equals("")) return;
            writer.writeString(newName,i.getMetaDataString(mDName));
          }
        }
    }

}


class ErrorListener extends BaseErrorListener {

   @Override
   public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e)
      throws ParseCancellationException {
         throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg);
      }
}

class AccordExpressionWalker {

    public static int VARIABLE = 1;
    public static int OTHER = 0;
    public static int NUMERIC = 2;
 
    public static void parse(AccordParser.AccordRulesContext ctx, ObjectWriter writer, String id) {
        writer.beginObject();
        if (ctx.lOp() != null) {
            writer.writeName("$type").beginArray().writeString("CompositeCheckMethod").endArray();
            writer.writeString("$id",id);
            String operator = genOperator(ctx.lOp().getText());
            if (operator.contains("Operator")) writer.writeString("hasOperator",operator);
            else writer.writeString("hasComparator",operator);
            writer.writeName("hasNestedTarget");
            parseRule(ctx.accordRule(),writer,id+".1",false);
            writer.writeName("hasNestedValue").beginArray();
            parse(ctx.accordRules(),writer,id+".2");
            writer.endArray();
        } else parseRule(ctx.accordRule(),writer,id,true);
        writer.endObject();
    }

    private static void parseRule(AccordParser.AccordRuleContext ctx, ObjectWriter writer, String id, boolean root) 
    {
      if (!root) writer.beginObject();
      writer.writeString("$id",id);
      writer.writeString("identifier",id.substring(id.lastIndexOf("/")+1));
      parseTarget(ctx.target(0),writer,id+".1",true,false,true);
      if (ctx.bOp() != null) {
          String operator = genOperator(ctx.bOp().getText());
          if (operator.contains("Operator")) writer.writeString("hasOperator",operator);
          else writer.writeString("hasComparator",operator);
          if (ctx.target(1)!=null) { 
            
            int retVal = parseTarget(ctx.target(1),writer,id+".2",false,true,true);
            if (retVal== VARIABLE)  writer.writeName("$type").beginArray().writeString("CategoryCheckMethod").endArray();
            else if (retVal== NUMERIC)  writer.writeName("$type").beginArray().writeString("NumericalCheckMethod").endArray();
            else writer.writeName("$type").beginArray().writeString("CompositeCheckMethod").endArray();
          }
          if (ctx.value() != null) {
            AccordParser.ValueContext c2 = ctx.value();
            if (c2.NUMBER() != null )
            {
              writer.writeName("$type").beginArray().writeString("NumericalCheckMethod").endArray();
              writer.writeString("hasValue",c2.NUMBER().getText());
              if (c2.unit()!=null) {
                writer.writeName("hasUnit");
                writer.beginObject();
                writer.writeName("$type").beginArray().writeString("$id").endArray();
                writer.writeString("$id","units"+c2.unit().getText());
                writer.endObject();
              }
            } else if (c2.boolean_() != null ) {
              writer.writeName("$type").beginArray().writeString("BooleanCheckMethod").endArray();
              writer.writeString("hasValue",ctx.value().getText());
            } else if (c2.stringConstant() != null ) { 
              writer.writeName("$type").beginArray().writeString("CategoryCheckMethod").endArray();
              writer.writeString("hasValue",ctx.value().getText().replace("\"",""));
            } else if (c2.constant() != null ) { 
              writer.writeName("$type").beginArray().writeString("NumericalCheckMethod").endArray();
              writer.writeString("hasValue",ctx.value().getText());
            }
          }
      } else if (ctx.flOp() != null){
        writer.writeName("$type").beginArray().writeString("CompositeCheckMethod").endArray();
        AccordParser.FlOpContext flOp=ctx.flOp();
        String operator="";
        if (flOp.NOT() != null) operator+="!";
        if (flOp.FORALL() != null) operator+="forall";
        if (flOp.EXISTS() != null) operator+="exists";
        
        writer.writeName("hasNestedValue").beginArray();
        parse(ctx.accordRules(),writer,id+".2");

        if (flOp.accordRules() != null ) {
          parse(flOp.accordRules(),writer,id+".3");
        } 
        writer.endArray();

        operator = genOperator(operator);
        if (operator.contains("Operator")) writer.writeString("hasOperator",operator);
        else writer.writeString("hasComparator",operator);
      } else {
        System.out.println("Something invalid in parsing");
      }

      if (!root) writer.endObject();
    }

    private static void genName(ObjectWriter writer,boolean nested,boolean bsdd,boolean target,boolean value) {
      String name="";
      if (value) name="Value";
      else if (target) name="Target";
      if (nested) name="Nested"+name;
      else if (bsdd) name="BSDD"+name;
      name="has"+name;
      writer.writeName(name);
      if (nested) writer.beginArray();
    }

    private static int parseTarget(AccordParser.TargetContext ctx, ObjectWriter writer, String id, boolean target, boolean value, boolean showName)
    {
      if (ctx.variable() != null ) {
        if (showName) genName(writer,false,true,target,value);
        writer.beginObject().writeString("$id",ctx.variable().getText().replace(":","terms:")).writeName("$type").beginArray().writeString("$id").endArray().endObject();
        return VARIABLE;
      } else if (ctx.expression() != null ) {
        if (showName) genName(writer,true,false,target,value);
        writer.beginObject();
        AccordParser.ExpressionContext ct  = ctx.expression();

        AccordParser.TargetNumberConstantContext tNC1  = ct.targetNumberConstant(0);
        if (tNC1.target() != null ) parseTarget(tNC1.target(),writer,id+".1",true,false,true);
        else if(tNC1.constant() != null ) {
          if (showName) genName(writer,false,false,true,false);
          writer.writeValue(tNC1.constant().getText().replace("\"",""));
        } else if (tNC1.NUMBER() != null) {
          if (showName) genName(writer,false,false,true,false);
          writer.writeValue(tNC1.NUMBER().getText());
        }
       
        AccordParser.TargetNumberConstantContext tNC2  = ct.targetNumberConstant(1);    
        if (tNC2.target() != null ) {
          if (parseTarget(tNC2.target(),writer,id+".2",false,true,true) == VARIABLE) writer.writeName("$type").beginArray().writeString("CategoryCheckMethod").endArray();
          else writer.writeName("$type").beginArray().writeString("CompositeCheckMethod").endArray();
        }else if(tNC2.constant() != null ) {
           if (showName) genName(writer,false,false,true,false);
          writer.writeValue(tNC2.constant().getText());
          writer.writeName("$type").beginArray().writeString("NumericalCheckMethod").endArray();
        }else if (tNC2.NUMBER() != null) {
           if (showName) genName(writer,false,false,false,true);
          writer.writeValue(tNC2.NUMBER().getText());
          writer.writeName("$type").beginArray().writeString("NumericalCheckMethod").endArray();
        }else {
          if (showName) genName(writer,false,false,false,true);
          writer.writeName("$type").beginArray().writeString("CompositeCheckMethod").endArray();
        }
        String operator = genOperator(ct.mathOp().getText());
        if (operator.contains("Operator")) writer.writeString("hasOperator",operator);
        else writer.writeString("hasComparator",operator);


        writer.writeString("$id",id);
        writer.writeString("identifier",id.substring(id.lastIndexOf("/")+1));
        writer.endObject();
        writer.endArray();
        return NUMERIC;
      } else if (ctx.function() != null) {
         if (showName) genName(writer,true,false,target,value);
        writer.beginObject();
        writer.writeName("$type").beginArray().writeString("FunctionCheckMethod").endArray();
        writer.writeString("$id",id);
        writer.writeString("identifier",id.substring(id.lastIndexOf("/")+1));
        AccordParser.FunctionContext context = ctx.function();
        writer.writeName("executes").beginObject();
        writer.writeName("$type").beginArray().writeString("$id").endArray();
        writer.writeString("$id","functions:"+context.variable().getText().replace(":",""));
        writer.endObject();
        if (context.targetOrValue() != null && context.targetOrValue().size()>=0)
        {
          int i=1; 
           if (showName && context.targetOrValue().size() > 0) genName(writer,true,false,false,true);
          for (AccordParser.TargetOrValueContext ct: context.targetOrValue()) 
          {
              if (ct.target() != null) 
              {
                String tId = id+"."+i;
                parseTarget(ct.target(),writer,tId,false,true,false);
              }
              if (ct.value() != null)  {
                writer.beginObject();
                writer.writeString("$id",id+"."+i);
                AccordParser.ValueContext ct2 = ct.value();
                if (ct2.boolean_() !=  null) {
                  writer.writeString("hasValue",ct2.boolean_().getText());
                  writer.writeName("$type").beginArray().writeString("BooleanCheckMethod").endArray();
                }
                if (ct2.stringConstant() != null) {
                  writer.writeString("hasValue",ct2.stringConstant().getText().replace("\"",""));
                  writer.writeName("$type").beginArray().writeString("CategoryCheckMethod").endArray();
                }
                if (ct2.NUMBER() != null) {
                  writer.writeString("hasValue",ct2.NUMBER().getText());
                  writer.writeName("$type").beginArray().writeString("NumericalCheckMethod").endArray();
                }
                if (ct2.constant() != null) {
                  writer.writeString("hasValue",ct2.constant().getText());
                  writer.writeName("$type").beginArray().writeString("NumericalCheckMethod").endArray();
                }
                if (ct2.unit() != null) {
                  writer.writeName("hasUnit");
                  writer.beginObject();
                  writer.writeName("$type").beginArray().writeString("$id").endArray();
                  writer.writeString("$id","units"+ct2.unit().getText());
                  writer.endObject();
                }
                writer.endObject();
              }
              i++;
          }
          if (showName && context.targetOrValue().size() > 0) writer.endArray();
        }

        writer.endObject();
        writer.endArray();
        return OTHER;
      }
      return OTHER;
    }

    private static String genOperator(String op){
      switch(op) {
        case "==": return "CheckMethodComparator-eq";
        case "!=": return "CheckMethodComparator-neq";
        case "exists": return "CheckMethodOperator-exists";
        case "!exists": return "CheckMethodOperator-notExists";
        case "forall": return "CheckMethodOperator-forall";
        case ">": return "CheckMethodComparator-gt";
        case "<": return "CheckMethodComparator-lt";
        case ">=": return "CheckMethodComparator-ge";
        case "<=": return "CheckMethodComparator-le";
        case "&&": return "CheckMethodComparator-logicalAND";
        case "||": return "CheckMethodComparator-logicalOR";
        case "+": return "CheckMethodOperator-addition";
        case "-": return "CheckMethodOperator-subtraction";
        case "/": return "CheckMethodOperator-division";
        case "*": return "CheckMethodOperator-multiplication";
        default:
          System.out.println("Missing Operator:"+op);
          return op;
      }
    }
}


class PartOrderer implements Comparator<ComplianceItem> {
  @Override
    public int compare(ComplianceItem a, ComplianceItem b) {
        if (a.getMetaDataString("dcterms:identifier") == null || b.getMetaDataString("dcterms:identifier") == null ) return -1;
        try {
          int aV = Integer.parseInt(a.getMetaDataString("dcterms:identifier"));
          int bV = Integer.parseInt(b.getMetaDataString("dcterms:identifier"));
          if (aV < bV) return -1;
          if (aV==bV) return 0;
          return 1;
        } catch (Exception e) {
          return a.getMetaDataString("dcterms:identifier").compareTo(b.getMetaDataString("dcterms:identifier"));
        }
    }
}

class InlinePartOrderer implements Comparator<InlineItem> {
  @Override
    public int compare(InlineItem a, InlineItem b) {
        if (a.getId() == null || b.getId() == null ) return -1;
         try {
          int aV = Integer.parseInt(a.getId());
          int bV = Integer.parseInt(b.getId());
          if (aV < bV) return -1;
          if (aV==bV) return 0;
          return 1;
        } catch (Exception e) {
           return a.getId().compareTo(b.getId());
        }
    }
}