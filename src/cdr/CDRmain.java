package cdr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import cn.fox.biomedical.Sider;
import cn.fox.nlp.Segment;
import cn.fox.nlp.Sentence;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.stanford.Tokenizer;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.CTDSaxParse;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.FileNameComparator;
import drug_side_effect_utils.MEDI;
import drug_side_effect_utils.RelationEntity;
import drug_side_effect_utils.Tool;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.PropertiesUtils;

public class CDRmain {

	public static void main(String[] args) throws Exception {
		// command line parameters
		FileInputStream fis = new FileInputStream(args[0]); 
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		boolean preprocessTrain = Boolean.parseBoolean(args[1]);
		boolean preprocessTest = Boolean.parseBoolean(args[2]);
		boolean bTrain = Boolean.parseBoolean(args[3]);
		

		String train_xml = properties.getProperty("train_xml");
		String dev_xml = properties.getProperty("dev_xml");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String train_instance_dir = properties.getProperty("train_instance_dir");
		String test_instance_dir = properties.getProperty("test_instance_dir");
		String perceptron_relation_ser = properties.getProperty("perceptron_relation_ser");
		String test_result = properties.getProperty("test_result");
		
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.BOTH);

		Tool tool = new Tool();
		tool.sentSplit = new SentenceSplitter(new Character[]{';'},false, PropertiesUtils.getString(properties, "common_english_abbr", ""));
		tool.tokenizer = new Tokenizer(true, ' ');	
		tool.tagger = new MaxentTagger(PropertiesUtils.getString(properties, "pos_tagger", ""));
		tool.morphology = new Morphology();
		tool.sider = new Sider(PropertiesUtils.getString(properties, "sider_dict", ""));
		tool.ctdParse = new CTDSaxParse(PropertiesUtils.getString(properties, "ctd_chemical_disease", ""));
		tool.medi = new MEDI();
		tool.medi.load(PropertiesUtils.getString(properties, "medi_dict", ""));

		int windowSize = PropertiesUtils.getInt(properties, "windowSize");
		int beamSize = PropertiesUtils.getInt(properties, "beamSize");
		int maxTrainTime = PropertiesUtils.getInt(properties, "maxTrainTime");
		System.out.println("beam_size="+beamSize+", train_times="+maxTrainTime
				+", windowSize="+windowSize);
				
		
		
		fixedTrainAndTest(train_xml, dev_xml, xmlParser, tool, train_instance_dir, test_instance_dir, 
				windowSize, beamSize, maxTrainTime, bioc_dtd, perceptron_relation_ser, test_result,
				preprocessTrain, preprocessTest, bTrain);
		
		
		
	}
	
	public static void fixedTrainAndTest(String train_xml, String test_xml, BiocXmlParser xmlParser, Tool tool, 
			String cdr_train_dir, String cdr_test_dir, int windowSize, int beamSize, int maxTrainTime, 
			String bioc_dtd, String perceptron_relation_ser, String test_result,
			boolean preprocessTrain, boolean preprocessTest, boolean bTrain) throws Exception {

		// preprocess train data
		ArrayList<BiocDocument> trainDocs= xmlParser.parseBiocXmlFile(train_xml);
		if(preprocessTrain) {
			preprocess(trainDocs, tool, cdr_train_dir, windowSize);
		}
		
		// begin to train
		Perceptron perceptronRelation = null;
		if(bTrain) {
			File trainDataDir = new File(cdr_train_dir);
				
			ArrayList<PerceptronInputData> trainInputDatas = new ArrayList<PerceptronInputData>();
			ArrayList<PerceptronOutputData> trainOutputDatas = new ArrayList<PerceptronOutputData>();
			for(File dir:trainDataDir.listFiles()) {
				// add all the instances in this dir considering the order
				List<File> filesPerDir = Arrays.asList(dir.listFiles());
				Collections.sort(filesPerDir, new FileNameComparator());
				for(File file:filesPerDir) {
					if(file.getName().indexOf("input") != -1) {
						trainInputDatas.add((PerceptronInputData)ObjectSerializer.readObjectFromFile(file.getAbsolutePath()));
					} else {
						trainOutputDatas.add((PerceptronOutputData)ObjectSerializer.readObjectFromFile(file.getAbsolutePath()));
					}
				}
			}
			
			// create a relation perceptron
			ArrayList<String> alphabetRelationType = new ArrayList<String>(Arrays.asList("CID"));
			perceptronRelation = new Perceptron(alphabetRelationType);
			perceptronRelation.setWindowSize(windowSize);
			ArrayList<PerceptronFeatureFunction> featureFunctions2 = new ArrayList<PerceptronFeatureFunction>(
					Arrays.asList(new RelationFeatures(perceptronRelation)));
			perceptronRelation.setFeatureFunction(null, featureFunctions2);
			perceptronRelation.buildFeatureAlphabet(trainInputDatas, trainOutputDatas, tool);
			System.out.println("begin to train perceptronRelation, features "+perceptronRelation.featureAlphabet.size());
			
			perceptronRelation.trainPerceptron(maxTrainTime, beamSize, trainInputDatas, trainOutputDatas, tool);
			
			ObjectSerializer.writeObjectToFile(perceptronRelation, perceptron_relation_ser);
		} else {
			perceptronRelation = (Perceptron)ObjectSerializer.readObjectFromFile(perceptron_relation_ser);
		}
			
		// preprocess test data
		ArrayList<BiocDocument> testDocs = xmlParser.parseBiocXmlFile(test_xml);
		if(preprocessTest) {
			preprocess(testDocs, tool, cdr_test_dir, windowSize);
		}
				
		
		OutputStreamWriter testResultFile = new OutputStreamWriter(new FileOutputStream(test_result), "utf-8");
		File testDataDir = new File(cdr_test_dir);
		for(File dir:testDataDir.listFiles()) {
			
			ArrayList<PerceptronInputData> inputDatas = new ArrayList<PerceptronInputData>();
			ArrayList<PerceptronOutputData> goldDatas = new ArrayList<>();
			List<File> files = Arrays.asList(dir.listFiles());
			Collections.sort(files, new FileNameComparator());
			for(File file:files) {
				if(file.getName().indexOf("input") != -1) {
					inputDatas.add((PerceptronInputData)ObjectSerializer.readObjectFromFile(file.getAbsolutePath()));
				} else {
					goldDatas.add((PerceptronOutputData)ObjectSerializer.readObjectFromFile(file.getAbsolutePath()));
				}
			}
			// predict
			ArrayList<RelationEntity> preRelationEntitys = new ArrayList<RelationEntity>();
			for(int i=0;i<inputDatas.size();i++) {
				PerceptronInputData inputdata = inputDatas.get(i);
				PerceptronOutputData goldData = goldDatas.get(i); // we need entities of gold entities
				
				// prepare the window, we assume the data is ordered
				ArrayList<PerceptronInputData> preInputs = new ArrayList<PerceptronInputData>();
				ArrayList<PerceptronOutputData> preOutputs = new ArrayList<PerceptronOutputData>();
				for(int k=0;k<windowSize;k++) {
					int index = i-windowSize+k;
					if(index<0) continue;
					// if the previous sentence and current sentence are not in the same document, ignore it
					if(!inputDatas.get(index).id.equals(inputdata.id))
						continue;
					
					preInputs.add(inputDatas.get(index));
					preOutputs.add(goldDatas.get(index));
				}
				
				PerceptronStatus returnType = perceptronRelation.beamSearch((PerceptronInputData)inputdata, goldData, false, beamSize, tool, preInputs, preOutputs);
				PerceptronOutputData output = returnType.z;
								
				preRelationEntitys.addAll(output.relations);
			}
			
			// for each entity in the RelationEntity, give it a mesh id
			// if we cannot find id, delete this relation
			ArrayList<RelationEntity> toBeDeletedRelation = new ArrayList<>();
			for(RelationEntity re:preRelationEntitys) {
				if(re.entity1.mesh.equals("-1"))
					toBeDeletedRelation.add(re); 
				if(re.entity2.mesh.equals("-1"))
					toBeDeletedRelation.add(re); 
				
			}
			for(RelationEntity re: toBeDeletedRelation) {
				preRelationEntitys.remove(re);
			}
			// we output all the relations and evaluate them with official tools
			for(RelationEntity predict:preRelationEntitys) {
				String line = dir.getName();
				line += "\t";
				line += "CID";
				line += "\t";
				line += predict.getChemical().mesh;
				line += "\t";
				line += predict.getDisease().mesh; 
				line +="\n";
				testResultFile.write(line);
			}
			
			
			
		}
		
		testResultFile.close();
		
		
	}
	

	public static void preprocess(ArrayList<BiocDocument> docs, Tool tool, String instance_dir, int windowSize) throws Exception {
		File fInstanceDir = new File(instance_dir);
		IoUtils.clearDirectory(fInstanceDir);
		
		for(BiocDocument doc:docs) {
			doc.fillCoreChemical();
			/*if(doc.id.equals("10091617"))
				System.out.println();*/
			ArrayList<PerceptronInputData> inputDatas = new ArrayList<PerceptronInputData>();
			ArrayList<PerceptronOutputData> outputDatas = new ArrayList<PerceptronOutputData>();
							
			List<Sentence> mySentences = prepareNlpInfo(doc, tool);
			buildInputData(inputDatas, mySentences, doc);
			buildGoldOutputData(doc, outputDatas, mySentences, windowSize, doc.id);
			
			File documentDir = new File(instance_dir+"/"+doc.id);
			documentDir.mkdir();
			for(int k=0;k<inputDatas.size();k++) {
				ObjectSerializer.writeObjectToFile(inputDatas.get(k), documentDir+"/"+k+".input");
				ObjectSerializer.writeObjectToFile(outputDatas.get(k), documentDir+"/"+k+".output");
				/*PerceptronOutputData data = (PerceptronOutputData)ObjectSerializer.readObjectFromFile(documentDir+"/"+k+".output");
				if(data.meshOfCoreChemical==null || data.meshOfCoreChemical.size()==0)
					System.out.println();*/
			}
			
		}
	}

	public static List<Sentence> prepareNlpInfo(BiocDocument doc, Tool tool) {
		List<Sentence> mySentences = new ArrayList<Sentence>();
	
		String content = doc.title+" "+doc.abstractt;
		List<String> sentences = tool.sentSplit.splitWithFilters(content);
		int offset = 0;
		for(String sentence:sentences) {
			int sentenceLength = sentence.length();
			Sentence mySentence = new Sentence();
			mySentence.text = sentence;
			mySentence.offset = offset;
			mySentence.length = sentenceLength;
			
			ArrayList<Segment> given = new ArrayList<Segment>();
			ArrayList<Segment> segments = TokenizerWithSegment.tokenize(mySentence.offset, mySentence.text, given);
			List<CoreLabel> tokens = new ArrayList<CoreLabel>();
			for(Segment segment:segments) {
				CoreLabel token = new CoreLabel();
				token.setWord(segment.word);
				token.setValue(segment.word);
				token.setBeginPosition(segment.begin);
				token.setEndPosition(segment.end);
				tokens.add(token);
			}
			
			// pos tagging
			tool.tagger.tagCoreLabels(tokens);
			// lemma
			for(int i=0;i<tokens.size();i++)
				tool.morphology.stem(tokens.get(i));

			
			mySentence.tokens = tokens;
			mySentences.add(mySentence);
			
			offset += sentenceLength;
		}
		
				
		return mySentences;
	}
	
	public static void buildInputData(ArrayList<PerceptronInputData> inputDatas,List<Sentence> mySentences, BiocDocument doc) {
		// build input data
		for(int j=0;j<mySentences.size();j++) {
			Sentence mySentence = mySentences.get(j);
			PerceptronInputData inputdata = new PerceptronInputData(doc.id, j);
			for(int i=0;i<mySentence.tokens.size();i++) {
				CoreLabel token = mySentence.tokens.get(i);
				// build input data
				inputdata.tokens.add(token.word());
				inputdata.offset.add(token.beginPosition());
			}
			inputdata.sentInfo = mySentence;
			inputdata.meshOfCoreChemical = doc.meshOfCoreChemical;
			inputDatas.add(inputdata);
		}
	}
	
	public static void buildGoldOutputData(BiocDocument doc, ArrayList<PerceptronOutputData> outDatas,
			List<Sentence> mySentences, int windowSize, String id) throws Exception {
		// build output data
		
		for(int m=0;m<mySentences.size();m++) {
			Sentence mySentence = mySentences.get(m);
			PerceptronOutputData outputdata = new PerceptronOutputData(true, id, m);
			Entity entity = new Entity(null, null, 0, null, null);
			Entity oldGold = null;
			// for each token
			for(int i=0;i<mySentence.tokens.size();i++) {
				CoreLabel token = mySentence.tokens.get(i);
				// build the segments of output data begin
				Entity newGold = doc.isInsideAGoldEntityAndReturnIt(token.beginPosition(), token.endPosition()-1);
				if(newGold == null) {
					if(entity.text!=null) { // save the old
						outputdata.segments.add(entity);
						entity = new Entity(null, null, 0, null, null);
					}
					// save the current, because the empty type segment has only one length.
					entity.sentIdx = m;
					entity.type = Perceptron.EMPTY;
					entity.offset = token.beginPosition();
					entity.text = token.word();
					entity.start = i;
					entity.end = i;
					outputdata.segments.add(entity);
					entity = new Entity(null, null, 0, null, null);
				} else {
					if(oldGold!=newGold) { // it's a new entity
						if(entity.text!=null) { // save the old
							outputdata.segments.add(entity);
							entity = new Entity(null, null, 0, null, null);
						}
						// it's the begin of a new entity, and we set its information but don't save it,
						// because a entity may be more than one length.
						entity.sentIdx = m;
						entity.type = newGold.type;
						entity.offset = token.beginPosition();
						entity.text = token.word();
						entity.start = i;
						entity.end = i;
						entity.mesh = newGold.mesh;
						
						oldGold = newGold;
					} else { // it's a old entity with more than one length
						int whitespaceToAdd = token.beginPosition()-(entity.offset+entity.text.length());
						for(int j=0;j<whitespaceToAdd;j++)
							entity.text += " ";
						// append the old entity with the current token
						entity.text += token.word();
						entity.end = i;	
					}
				}
				// build the segments of output data end
				
			}
			if(entity.text!=null) { // save the old
				outputdata.segments.add(entity);
			}
			// build the relations of output data begin
			if(doc.relations !=null && !doc.relations.isEmpty()) {
				/*
				 *  here we need to consider all the relations in this document including inner and inter
				 *  but considering the beam search algorithm, the gold answer we need is just the relations
				 *  in the window (e.g. if window=3 and current=m, we need m-3, m-2, m-1 and m).
				 *  This means that we need to repreprocess when the window has changed. 
				 */
				// the sentences before the current
				for(int k=0;k<windowSize;k++) {
					int index = m-windowSize+k;
					if(index<0) continue;
					PerceptronOutputData preOutData = (PerceptronOutputData)outDatas.get(index);
					// for each entity in the current sentence, consider whether it has relation with an entity in the previous sentence
					for(int i=0;i<outputdata.segments.size();i++) {
						Entity entity1 = outputdata.segments.get(i);
						if(entity1.type.equals(Perceptron.EMPTY))
							continue;
						for(int j=0;j<preOutData.segments.size();j++) {
							Entity entity2 = preOutData.segments.get(j);
							if(entity2.type.equals(Perceptron.EMPTY)) 
								continue;
							if(doc.twoEntitiesHaveRelation(entity1, entity2)) {
								RelationEntity relationEntity = new RelationEntity("CID", entity1, entity2);
								outputdata.relations.add(relationEntity);
							}
						}
					}
				}
				// the current sentence
				for(int i=0;i<outputdata.segments.size();i++) {
					for(int j=0;j<i;j++) {
						Entity entity1 = outputdata.segments.get(i);
						Entity entity2 = outputdata.segments.get(j);
						if(entity1.type.equals(Perceptron.EMPTY) || entity2.type.equals(Perceptron.EMPTY)) continue;
						if(doc.twoEntitiesHaveRelation(entity1, entity2)) {
							RelationEntity relationEntity = new RelationEntity("CID", entity1, entity2);
							outputdata.relations.add(relationEntity);
						}
					}
				}
			}
			// build the relations of output data end
			
			outDatas.add(outputdata);
			
			
		}
	}
	
	public static BiocDocument getBiocDocumentByID(String id, ArrayList<BiocDocument> documents) {
		for(BiocDocument document:documents) {
			if(document.id.equals(id))
				return document;
		}
		return null;
	}
}
