package accord.regulationsimporter;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.io.FileWriter;
import java.io.StringWriter;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Arrays;

import org.dcom.core.compliancedocument.ComplianceDocument;
import org.dcom.core.compliancedocument.deserialisers.XMLComplianceDocumentDeserialiser;
import org.dcom.core.compliancedocument.deserialisers.JSONComplianceDocumentDeserialiser;
import org.dcom.core.compliancedocument.serialisers.XMLComplianceDocumentSerialiser;
import org.dcom.core.compliancedocument.serialisers.JSONComplianceDocumentSerialiser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.apicatalog.rdf.RdfDataset;
import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.rdf.io.nquad.NQuadsWriter;

import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.query.Dataset;
import com.github.bjansen.ssv.SwaggerValidator;
import java.io.InputStreamReader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;

public class RegulationsImporter {


	private static String ONTOLOGY_URI="https://data.accordproject.eu/";

	public static void main(String[] args) throws IOException, ProcessingException {
		if (args.length < 1) {
			System.out.println("Please provie filename to import");
			System.exit(1);
		}

		System.out.println("Loading "+args[0]+"->"+args[1]);
		String[] flags;
		if (args.length > 2) flags = Arrays.copyOfRange(args, 2, args.length);   
		else flags = new String[0];
		String fileName = args[0];
		String outputFilename = args[1];
		File documentFile = new File(fileName);

		ComplianceDocument document = null;
		if (fileName.equals("NLP")) {
			document = NLPImporter.parse("NLP.xls");
		} else if (fileName.endsWith(".html") || fileName.endsWith(".json")) {

				String cDocumentString  = "";
				try {
	
					cDocumentString = Files.readString(documentFile.toPath(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (fileName.endsWith(".html")) {
					document = XMLComplianceDocumentDeserialiser.parseComplianceDocument(cDocumentString);
				} else if (fileName.endsWith(".json")) {
					document = JSONComplianceDocumentDeserialiser.parseComplianceDocument(cDocumentString);
				}
		} else if (fileName.endsWith(".xls")) {
				document = ExcelComplianceDocumentDeserialiser.parseComplianceDocument(fileName);
		} else if (fileName.endsWith(".jsonld") || fileName.endsWith(".nq") || fileName.endsWith(".ttl") || fileName.endsWith(".yaml")) {
			
			String jsonData = "";
			if (fileName.endsWith(".ttl") || fileName.endsWith(".nq")) {
				Dataset dataset = RDFDataMgr.loadDataset(fileName);
 				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				RDFDataMgr.write(stream, dataset, RDFLanguages.JSONLD) ;
				jsonData = new String(stream.toByteArray());
			}	
			
			if (fileName.endsWith(".yaml")) {
				try {
					jsonData = Files.readString(documentFile.toPath(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (fileName.endsWith(".yaml")) {
					try {
						ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
   						Object obj = yamlReader.readValue(jsonData, Object.class);
						ObjectMapper jsonWriter = new ObjectMapper();
    					jsonData = jsonWriter.writeValueAsString(obj);
    				} catch (Exception e) {
    					e.printStackTrace();
    				}
				}
			} 
			document = OntologyComplianceDocumentDeserialiser.parseComplianceDocument(jsonData);

		} else {
			System.out.println("Invalid File Exentsion");
			System.exit(1);
		}

		document.setMetaData("dcterms:coverageSpatial",fileName.substring(0,2));


		if (outputFilename.endsWith(".html")) {

			String data = XMLComplianceDocumentSerialiser.serialise(document,true);
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename));
   				writer.write(data);
   				writer.flush();
   			} catch (Exception e){
   				e.printStackTrace();
   			}
   		} else if (outputFilename.endsWith(".json")) {


			String data = JSONComplianceDocumentSerialiser.serialise(document);
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename));
   				writer.write(data);
   				writer.flush();
   			} catch (Exception e){
   				e.printStackTrace();
   			}


		} else if (outputFilename.endsWith(".yaml") || outputFilename.endsWith(".yaml-flat") ||outputFilename.endsWith(".nq") || outputFilename.endsWith(".jsonld") || outputFilename.endsWith(".ttl") ) {

			String documentName = document.getMetaDataString("dcterms:title").replaceAll("[^A-Za-z0-9]", "");
			String data = OntologyComplianceDocumentSerialiser.serialise(document,documentName,Arrays.asList(flags).contains("simpleRules"),false,null,false);
			SwaggerValidator validator = SwaggerValidator.forYamlSchema(new InputStreamReader(RegulationsImporter.class.getClassLoader().getResourceAsStream("BuildingCodesAndRules.yaml")));
			ProcessingReport report = validator.validate(data, "/components/schemas/BCRL");
            if (report.isSuccess()) {
            	System.out.println("FAILED to validate");
            	System.exit(1);
            }

            JsonDocument json = null;
			try {
				json=JsonDocument.of(new ByteArrayInputStream(data.getBytes()));
				if (outputFilename.endsWith(".yaml-flat")) {
					json=JsonDocument.of(JsonLd.flatten(json).get());
					json=JsonDocument.of(JsonLd.compact(json,JsonDocument.of(new URL("https://ci.mines-stetienne.fr/aec3po/aec3po.jsonld").openStream())).get());
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			if (outputFilename.endsWith(".yaml") || outputFilename.endsWith(".yaml-flat")) {
        		try {
        			YAMLMapper mapper = new YAMLMapper();
        			mapper.configure(YAMLGenerator.Feature.SPLIT_LINES,false);
        			mapper.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES,true);
        			mapper.configure(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS,true);
        			data = mapper.writeValueAsString(new ObjectMapper().readTree(json.getJsonContent().get().toString()));
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
			}
			if (outputFilename.endsWith(".nq") || outputFilename.endsWith(".ttl")) {
				try {
					RdfDataset dataSet = JsonLd.toRdf(json).base(ONTOLOGY_URI).get();
					if (outputFilename.equals(".ttl")) {
						String nQ;
						StringWriter strW = new StringWriter();
						NQuadsWriter writer = new NQuadsWriter(strW);
						writer.write(dataSet);
						nQ=strW.toString();
						Dataset dataset =  RDFParser.create().source(new ByteArrayInputStream(nQ.getBytes())).lang(RDFLanguages.NQ).base(ONTOLOGY_URI).toDataset();
						RDFWriter rdfWriter = RDFWriter.create().source(dataset).lang(Lang.TTL).build();
						FileOutputStream output = new FileOutputStream(outputFilename);
						rdfWriter.output(output);
						return;
					} else {
						NQuadsWriter writer = new NQuadsWriter(new FileWriter(outputFilename));
						writer.write(dataSet);
						return;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return;
			}
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename));
   				writer.write(data);
   				writer.flush();
   			} catch (Exception e){
   				e.printStackTrace();
   			}

		} else {
			System.out.println("Invalid Output File Name");
		}

		
	}
}