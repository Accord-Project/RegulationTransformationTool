
package accord.regulationsimporter;

import java.io.FileInputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.hssf.usermodel.HSSFPictureData;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFShape;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.dcom.core.compliancedocument.utils.TextExtractor;


import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import org.dcom.core.compliancedocument.*;

class ImageData {

    public ImageData(String _ext, byte[] _data) {
      ext=_ext;
      data=_data;
    }

    public String ext;
    public byte[] data;
}


public class ExcelComplianceDocumentDeserialiser {

  public static Map<String, String> propertyMap;
  static {
    propertyMap = new HashMap<>();
    propertyMap.put("Title", "dcterms:title");
    propertyMap.put("Project Stage", "ckterms:projectStage");
    propertyMap.put("Subject/Discipline", "dcterms:subject");
    propertyMap.put("Temporal Scope", "dcterms:coverage.temporal");
    propertyMap.put("Spatial Scope", "dcterms:coverage.spatial");
    propertyMap.put("Date", "dcterms:dateCreated");
    propertyMap.put("Identifier", "dcterms:identifer");
    propertyMap.put("Version", "ckterms:version");

}


  private ExcelComplianceDocumentDeserialiser() {
    
  }

  private static String getString(Cell c) {
      if (c.getCellType() == CellType.NUMERIC) {
        String str = ""+c.getNumericCellValue();
        return str.replace(".0","");
      } else if (c.getCellType() == CellType.STRING) {
        return c.getStringCellValue();
      } else return null;
  }

  private static ComplianceItem getOrCreate(ComplianceItem parent, ArrayList<String>lookups, boolean title) {
      
      ComplianceItem located = null;
      if (parent instanceof ComplianceDocument) {

        for (int i=0; i < ((ComplianceDocument)parent).getNoSections();i++) {
            Section s = ((ComplianceDocument)parent).getSection(i);
            if (s.getMetaDataString("dcterms:identifier").equals(lookups.get(0))) {
              located = s;
            }
        }

        if (located == null) {
          located=new Section(parent);
          located.setMetaData("dcterms:identifier",lookups.get(0));
          located.setMetaData("dcterms:title","");
          ((ComplianceDocument)parent).addSection((Section)located);
        }
      } else if (parent instanceof Section) {

        for (int i=0; i < ((Section)parent).getNoSections();i++) {
            Section s = ((Section)parent).getSection(i);
            if (s.getMetaDataString("dcterms:identifier").equals(lookups.get(0))) {
              located = s;
            }
        }
        if (located == null && title) {
           located = new Section(parent);
           ((Section)parent).addSection((Section)located);
        }else if (located == null && lookups.size()==1) {
          located=new Paragraph(parent);
          ((Section)parent).addParagraph((Paragraph)located);
        } else if (located == null && lookups.size() > 1) {
          located = new Section(parent);
          ((Section)parent).addSection((Section)located);
        }
        located.setMetaData("dcterms:identifier",lookups.get(0));
        System.out.println(lookups.get(0));
        
      } else {
        System.out.println("Invalid Item in Document Tree");
        System.exit(1);
      }

      if (lookups.size()==1) {
        return located;
      } else {
        lookups.remove(0);
        return getOrCreate(located,lookups,title);
      }
  }
  
  public static ComplianceDocument parseComplianceDocument(String fileName) {
      HashMap<String,ImageData> imageData = new HashMap<String,ImageData>();
      HashMap<String,ArrayList<ArrayList<String>>> tableData = new HashMap<String,ArrayList<ArrayList<String>>>();
      Workbook workbook = null;
      try {
        FileInputStream fis = new FileInputStream(fileName);
        workbook = new HSSFWorkbook(fis);
      }catch (Exception e) {
        e.printStackTrace();
        System.out.println("Error Opening Excel File");
        System.exit(1);
        return new ComplianceDocument();
      }
      ComplianceDocument document = new ComplianceDocument();
      int numberOfSheets = workbook.getNumberOfSheets();

      Sheet sheet = workbook.getSheetAt(0);
      //parse the intro sheet
      for (int i=0; i < sheet.getLastRowNum();i++) {
          Row r = sheet.getRow(i);
          if (r == null) continue;;
          for (int j=0; j < r.getLastCellNum();j++) {
            Cell c = r.getCell(j);
            if (c==null) continue;
            try {
                String field = getString(c);
                if (propertyMap.keySet().contains(field)) {
                  Cell newC = r.getCell(j+1);
                  if (newC == null) continue;
                  String val = getString(newC);
                  if (val==null || val.equals("")) continue;
                  document.setMetaData(propertyMap.get(field),val);
                }
            } catch(Exception e) {}

          }
      }

      //now get the images
      for (int i=2; i < numberOfSheets;i++){
        Sheet imageSheet = workbook.getSheetAt(i);
        if (imageSheet == null ) continue;
        HSSFPatriarch images = (HSSFPatriarch) imageSheet.createDrawingPatriarch();
        for(HSSFShape shape : images.getChildren()){
            if(shape instanceof Picture){
                HSSFPictureData imageDataRaw = ((HSSFPicture)shape).getPictureData();
                imageData.put(imageSheet.getSheetName(),new ImageData(imageDataRaw.suggestFileExtension(),imageDataRaw.getData()));
                System.out.println("Found Image:"+imageSheet.getSheetName());
                
            }
        }
        if (images.getChildren().size()==0) {
          System.out.println("Parsing Table"+imageSheet.getSheetName());
          tableData.put(imageSheet.getSheetName(),new ArrayList<ArrayList<String>>());
          for (int k=0; k <= imageSheet.getLastRowNum();k++) {
              Row r = imageSheet.getRow(k);
              tableData.get(imageSheet.getSheetName()).add(new ArrayList<String>());
              if (r == null) continue;
              for (int j=0; j <= r.getLastCellNum();j++) {
                Cell c = r.getCell(j);
                if (c == null) continue;
                String tS = getString(c);
                if (tS==null) tS="";
                tableData.get(imageSheet.getSheetName()).get(k).add(tS);
              }
            }
          }
      }
      //now parse the main sheet
      Sheet mainSheet = workbook.getSheetAt(1);
      Row r = mainSheet.getRow(3);
      if (r == null)
      {
        System.out.println("Main Sheet too short");
        System.exit(1);
        return document;
      }
      int englishRow = -1;
      for (int j=0; j < r.getLastCellNum();j++) {
         Cell c = r.getCell(j);
         if (c==null) continue;
         String data = getString(c);
         if (data.contains("English")) {
          englishRow=j;
          break;
        }
      }
      if (englishRow == -1){
        System.out.println("Could not find english column");
        System.exit(1);
        return document;
      }

      String[] sections = new String[englishRow-1];
      for (int i=0; i < englishRow-1;i++) sections[i]="";

      for (int i=4; i < mainSheet.getLastRowNum()+1;i++) {
        r = mainSheet.getRow(i);
        if (r==null) continue;
        if (r.getCell(englishRow) == null) continue;
        String text = getString(r.getCell(englishRow));
        ArrayList<String> lookups = new ArrayList<String>();
        for (int z=0; z < englishRow-1;z++) {
          if (r.getCell(z) != null && getString(r.getCell(z)) != null && !getString(r.getCell(z)).equals("")) {
            lookups.add(getString(r.getCell(z)));
          }
        }
        if (lookups.size()==0) continue;
        System.out.println(lookups.get(lookups.size()-1));
        if (tableData.keySet().contains(lookups.get(lookups.size()-1))) {
           String tableKey=lookups.get(lookups.size()-1);
           lookups.remove(lookups.size()-1);
           ComplianceItem item = getOrCreate(document,lookups,false);
           System.out.println("Embedding:"+tableKey);
           Table t = new Table(item);
           if (text==null) text="";
            t.setMetaData("caption",tableKey+": "+text);
            TableBody tG = new TableBody(t);
            t.setBody(tG);
            for (int j=0; j < tableData.get(tableKey).size();j++) {
              org.dcom.core.compliancedocument.Row r1 = new org.dcom.core.compliancedocument.Row(tG);
              tG.addRow(r1);
              for (int k=0; k < tableData.get(tableKey).get(j).size();k++){
                DataCell c = new DataCell(r1);
                r1.addCell(c);
                Paragraph p = new Paragraph(c);
                p.setInlineItems(TextExtractor.extractStructure(tableData.get(tableKey).get(j).get(k).replace("&nbsp;"," ")));
                c.setBody(p);
              }
            }
            if (item instanceof Paragraph) {
              ((Paragraph)item).addInsert(t);
            } else {
              Section s = (Section) item;
              Paragraph p=null;
              if (s.getNoParagraphs()>=1) {
                p=s.getParagraph(s.getNoParagraphs()-1);
              } else {
                p=new Paragraph(item);
                s.addParagraph(p);
              }
              p.addInsert(t);
            }
        } else if (imageData.keySet().contains(lookups.get(lookups.size()-1))) {
          String imageKey=lookups.get(lookups.size()-1);
          lookups.remove(lookups.size()-1);
          ComplianceItem item = getOrCreate(document,lookups,false);
          System.out.println("Embedding:"+imageKey);
          Figure f = new Figure(item);
          f.setImageData(imageData.get(imageKey).data);
          if (text==null) text="";
          f.setMetaData("caption",imageKey+": "+text);
          if (item instanceof Paragraph) {
            ((Paragraph)item).addInsert(f);
          } else {
            Section s = (Section) item;
            Paragraph p=null;
            if (s.getNoParagraphs()>=1) {
              p=s.getParagraph(s.getNoParagraphs()-1);
            } else {
              p=new Paragraph(item);
              s.addParagraph(p);
            }
            p.addInsert(f);
          }
        } else {
          ComplianceItem item = getOrCreate(document,lookups,text.startsWith("Title:"));
          if (item instanceof Paragraph) {
            Paragraph p = (Paragraph) item;
            
              p.setInlineItems(TextExtractor.extractStructure(text.replace("&nbsp;"," ")));
             // System.out.println(text);
            
          } else if (item instanceof Section) {
            Section s = (Section) item;
            s.setMetaData("dcterms:title",text.replace("Title:",""));
          } else {
            System.out.println("Something went wrong with parsing");
          }
          
        }
        
      }

      return document;
  
  }


}
