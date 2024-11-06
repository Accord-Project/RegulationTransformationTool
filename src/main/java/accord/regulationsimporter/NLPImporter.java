package accord.regulationsimporter;

import org.dcom.core.compliancedocument.*;

import java.io.FileInputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.dcom.core.compliancedocument.utils.TextExtractor;


class Tag {
	public int paragraph;
	public int start;
	public int end;
	public String value;
	public String type;
}

class Relationship {
	public Relationship() {
		items = new ArrayList<Integer>();
	}
	List<Integer> items;
	int paragraph;
	String type;
}

class RASEToAdd {

	public RASEToAdd(Tag t) {
		start=t.start;
		end=t.end;
		value=t.value;
	}

	int start;
	int end;
	String value;
	String type;
	String raseValue;
	String raseTarget;
	String raseComparison;

	public int getStart() {
		return start;
	}
}


public class NLPImporter {

	private static String getString(Cell c) {
      if (c==null) return "";
      if (c.getCellType() == CellType.NUMERIC) {
        String str = ""+c.getNumericCellValue();
        return str.replace(".0","");
      } else if (c.getCellType() == CellType.STRING) {
        return c.getStringCellValue();
      } else return null;
  	}

  	private static ArrayList<String> paragraphs = new ArrayList<String>();
	private static ArrayList<Tag> tags = new ArrayList<Tag>();
	private static ArrayList<Relationship> rels = new ArrayList<Relationship>();

  	private static int getOrCreateParagraph(String text) {
  		if (text==null || text.equals("")) return -1;
  		int i = paragraphs.indexOf(text);
  		if (i==-1) {
  			paragraphs.add(text);
  			return paragraphs.size()-1;
  		}
  		return i;
  	}


  	public static void makeRASE(int paragraph, ArrayList<RASEToAdd> tagsToAdd) {
  		List<RASEToAdd> tagsToAddSorted = tagsToAdd.stream().filter(c -> c!=null).sorted(Comparator.comparing(RASEToAdd::getStart)).collect(Collectors.toList());
  		String text = paragraphs.get(paragraph);
  		int addition=0;
  		for (int i=0; i < tagsToAddSorted.size(); i++) {
  			RASEToAdd tA = tagsToAddSorted.get(i);
  			if (tA==null) continue;
			String assemble = "<span data-raseType='"+tA.type+"' data-raseTarget='"+tA.raseTarget+"' data-raseValue='"+tA.raseValue+"' data-raseComparator='"+tA.raseComparison+"' data-raseId='R"+raseId+"'>"+tA.value+"</span>";
  			raseId++;
  			String first = text.substring(0,tA.start+addition);
  			String second = text.substring(tA.end+addition,text.length());
  			text = first+assemble+second;
  			addition+=(assemble.length()-tA.value.length());
  		}
  		paragraphs.set(paragraph,text);
  	}

  	public static int raseId=0;
  	public static HashSet<Integer> processedTags = new HashSet<Integer>();

  	public static int getOrCreateTag(int paragraph, String _start, String _end, String value, String type)  {
  		if (_start==null || _start.equals("") || _end==null || _end.equals("") || value ==null || value.equals("") || type == null || type.equals("")) return -1;
  		int start = Integer.parseInt(_start);
  		int end = Integer.parseInt(_end);
  		for (int i=0; i < tags.size();i++) {
  			Tag t = tags.get(i);
  			if (t.paragraph == paragraph && t.start == start && t.end==end && t.value == value && t.type==type) return i;
  		}
  		Tag t = new Tag();
  		t.paragraph = paragraph;
  		t.start = start;
  		t.end = end;
  		t.value = value;
  		t.type = type;
  		tags.add(t);
  		return tags.size()-1;
  	}



	public static ComplianceDocument parse(String fileName) {
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
	  Sheet sheet = workbook.getSheetAt(1);
	 
	  
	  for (int i=1; i < sheet.getLastRowNum();i++) {
      	Row r = sheet.getRow(i);
        if (r == null) continue;
  		int paragraph = getOrCreateParagraph(getString(r.getCell(2)));	
        int tag1 = getOrCreateTag(paragraph,getString(r.getCell(6)),getString(r.getCell(7)),getString(r.getCell(8)),getString(r.getCell(9)));
		int tag2 = getOrCreateTag(paragraph,getString(r.getCell(11)),getString(r.getCell(12)),getString(r.getCell(13)),getString(r.getCell(14)));
		if (paragraph==-1 || tag1 ==-1 || tag2 ==-1) continue;
		Relationship rel = new Relationship();
		rel.items.add(tag1);
		rel.items.add(tag2);
		rel.paragraph=paragraph;
		rel.type = getString(r.getCell(15));
		rels.add(rel);
      }

      System.out.println("Parsed "+paragraphs.size()+" paragraphs "+tags.size()+" tags and "+rels.size()+" relationships");

	  Section section= new Section(document);
	  document.addSection(section);
	  HashSet<Integer> processedTags = new HashSet<Integer>();
	  for (int i=0; i < paragraphs.size();i++) {
	  	ArrayList<RASEToAdd> tagsToAdd = new ArrayList<RASEToAdd>();

	  	for (int j=0; j < rels.size();j++) {
	  		Relationship r=rels.get(j);
	  		if (r.paragraph!=i) continue;
	  		switch (r.type) {
	  			case "selection":
	  				//if (tags.get(r.a).type.equals("Object")) tagsToAdd.add(processTag(r.a,"Application"));
	  				//if (tags.get(r.a).type.equals("Object")) tagsToAdd.add(processTag(r.b,"Application"));
	  				break;
	  			case "part-of":
	  			case "not-part-of":

	  				break;
	  			case "necessity":
	  			case "less-equal":
	  			case "greater-equal":
	  			case "equal":
	  			case "less":
	  			case "greater":	
	  				List<Integer> value = r.items.stream().filter( x -> tags.get(x).type.equals("quality") || tags.get(x).type.equals("value")).collect(Collectors.toList());
	  				if (value.size() != 1) {
	  					System.out.println("No(or to many) targets on relationship");
	  				} else {
	  					RASEToAdd newTag=new RASEToAdd(tags.get(value.get(0)));
	  					newTag.type="Requirement";
	  					newTag.raseValue=tags.get(value.get(0)).value;
	  					List<Integer> target = r.items.stream().filter( x -> x!=value.get(0)).collect(Collectors.toList());
	  					newTag.raseTarget = tags.get(target.get(0)).value;
	  					tagsToAdd.add(newTag);
	  				}
	  				break;
	  			default:
	  				System.out.println("Invalid Rel Type:"+r.type);
	  		}
	  	}
	  	makeRASE(i,tagsToAdd);


	  	Paragraph p = new Paragraph(section);
	  	p.setInlineItems(TextExtractor.extractStructure(paragraphs.get(i)));
	  	section.addParagraph(p);
	  }
	  return document;

	}

}