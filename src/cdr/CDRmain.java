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

import cc.mallet.types.SparseVector;
import cn.fox.biomedical.Sider;
import cn.fox.machine_learning.BrownCluster;
import cn.fox.nlp.Segment;
import cn.fox.nlp.Sentence;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.nlp.Word2Vec;
import cn.fox.utils.Evaluater;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import cn.fox.utils.ObjectShuffle;
import deprecated.RelationGold;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.FileNameComparator;
import drug_side_effect_utils.MeshDict;
import drug_side_effect_utils.Relation;
import drug_side_effect_utils.RelationEntity;
import drug_side_effect_utils.Tool;
import drug_side_effect_utils.WordClusterReader;
import edu.mit.jwi.IDictionary;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectObjectProcedure;

public class CDRmain {

	public static void main(String[] args) throws Exception {
		FileInputStream fis = new FileInputStream(args[0]); // property file
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();

		String train_xml = properties.getProperty("train_xml");
		String dev_xml = properties.getProperty("dev_xml");
		String sample_xml = properties.getProperty("sample_xml");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String train_instance_dir = properties.getProperty("train_instance_dir");
		String test_instance_dir = properties.getProperty("test_instance_dir");
		String pos_tagger = properties.getProperty("pos_tagger");
		String perceptron_relation_ser = properties.getProperty("perceptron_relation_ser");
		String group = properties.getProperty("group");
		String group_err = properties.getProperty("group_err");
		String test_result = properties.getProperty("test_result");
		String brown_cluster_path = properties.getProperty("brown_cluster_path");
		String sider_dict = properties.getProperty("sider_dict");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String vector = properties.getProperty("vector");
		String vector_cluster = properties.getProperty("vector_cluster");
		String train_dev_xml = properties.getProperty("train_dev_xml");
		String group_10 = properties.getProperty("group_10");
		String nvalid_instance_dir = properties.getProperty("nvalid_instance_dir");
		String parser = properties.getProperty("parser");
		String err_instance_dir = properties.getProperty("err_instance_dir");
		String generated_bioc_xml = properties.getProperty("generated_bioc_xml");
		
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.BOTH);
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'}, false, common_english_abbr);
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		Morphology morphology = new Morphology();
		BrownCluster entityBC = new BrownCluster(brown_cluster_path, 100);
		Sider sider = new Sider(sider_dict);
		IDictionary dict = new edu.mit.jwi.Dictionary(new URL("file", null, wordnet_dict));
		dict.open();
		Word2Vec w2v = new Word2Vec();
		w2v.loadWord2VecOutput(vector);
		WordClusterReader wcr = new WordClusterReader(vector_cluster);
		LexicalizedParser lp = LexicalizedParser.loadModel(parser);
		
		Tool tool = new Tool();
		tool.sentSplit = sentSplit;
		tool.tagger = tagger;
		tool.morphology = morphology;
		tool.entityBC = entityBC;
		tool.sider = sider;
		tool.dict = dict;
		tool.w2v = w2v;
		tool.wcr = wcr;
		tool.lp = lp;
		
		// parameters
		int windowSize = Integer.parseInt(args[1]); // window size
		int beamSize = Integer.parseInt(args[2]); // beam size
		int maxTrainTime = Integer.parseInt(args[3]);
		double learningRate = Double.parseDouble(args[4]);
		
		
		fixedTrainAndTest(train_dev_xml, generated_bioc_xml, xmlParser, tool, train_instance_dir, test_instance_dir, 
				windowSize, beamSize, maxTrainTime, learningRate, bioc_dtd, group, perceptron_relation_ser, test_result);
		
		//nValidate(train_dev_xml, xmlParser, tool, nvalid_instance_dir, windowSize, beamSize, maxTrainTime, learningRate, bioc_dtd, group_10);
		
	}
	
	// use the development documents in the group file as test set, use the training data as training set
	public static void fixedTrainAndTest(String train_xml, String test_xml, BiocXmlParser xmlParser, Tool tool, 
			String cdr_train_dir, String cdr_test_dir,
			int windowSize, int beamSize, int maxTrainTime, double learningRate, String bioc_dtd, String group,
			String perceptron_relation_ser, String test_result) 
	throws Exception {
		// begin to train and test
		System.out.println("beam_size="+beamSize+", train_times="+maxTrainTime
				+", windowSize="+windowSize+", learningRate="+learningRate);
		
		// set parameters here !!!!!!!!!!!!
		boolean preprocess1 = true;
		boolean preprocess2 = true;
		boolean bTrain = true;
		boolean debugFPFN = false;
		
		// preprocess train data
		ArrayList<BiocDocument> trainDocs= xmlParser.parseBiocXmlFile(train_xml);
		
		if(preprocess1) {
			preprocess(trainDocs, tool, cdr_train_dir, windowSize);
		}
		
		// preprocess test data
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(group), "utf-8"));
		ArrayList<String> testDocIds = new ArrayList<>();
		String thisLine = null;
		while ((thisLine = br.readLine()) != null) {
			if(thisLine.isEmpty())
				continue;
			testDocIds.add(thisLine);
		}
		br.close();
		
		ArrayList<BiocDocument> wholeTestDocs= xmlParser.parseBiocXmlFile(test_xml);
		MeshDict meshDict = new MeshDict(test_xml, bioc_dtd);
		tool.meshDict = meshDict;
		
		ArrayList<BiocDocument> testDocs = new ArrayList<>();
		for(String id:testDocIds) {
			testDocs.add(getBiocDocumentByID(id, wholeTestDocs));
		}
		
		
		if(preprocess2) {
			preprocess(wholeTestDocs, tool, cdr_test_dir, windowSize);
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
			perceptronRelation = new Perceptron(alphabetRelationType, -1, -1);
			perceptronRelation.setWindowSize(windowSize);
			perceptronRelation.learningRate = learningRate;
			perceptronRelation.average = false;
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
			
		
		int countPredictMesh = 0;
		int countTrueMesh = 0;
		int countCorrectMesh = 0;
		
		OutputStreamWriter testResultFile = new OutputStreamWriter(new FileOutputStream(test_result), "utf-8");
		File testDataDir = new File(cdr_test_dir);
		for(File dir:testDataDir.listFiles()) {
			if(getBiocDocumentByID(dir.getName(), testDocs)==null)
				continue;
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
			// if we cannot find id, delete this re
			ArrayList<RelationEntity> toBeDeletedRelation = new ArrayList<>();
			for(RelationEntity re:preRelationEntitys) {
				String mesh1 = tool.meshDict.getMesh(re.entity1.text);
				if(mesh1.equals("-1"))
					toBeDeletedRelation.add(re); 
				else {
					re.entity1.mesh = mesh1;
				}
				
				String mesh2 = tool.meshDict.getMesh(re.entity2.text);
				if(mesh2.equals("-1"))
					toBeDeletedRelation.add(re); 
				else {
					re.entity2.mesh = mesh2;
				}
			}
			for(RelationEntity re: toBeDeletedRelation) {
				preRelationEntitys.remove(re);
			}
			// now we can make relations with mesh id, and get rid of the overlapped
			HashSet<Relation> predictRelations = new HashSet<Relation>();
			for(RelationEntity predict:preRelationEntitys) {
				Relation r = new Relation(null, "CID", predict.entity1.mesh, predict.entity2.mesh);
				if(!predictRelations.contains(r))
					predictRelations.add(r);
				
			}
			
			// debug FP and FN
			if(debugFPFN) {
				System.out.println(dir.getName());
				System.out.println("FP");
				for(RelationEntity predict:preRelationEntitys) {
					Relation r = new Relation(null, "CID", predict.entity1.mesh, predict.entity2.mesh);
					if(!getBiocDocumentByID(dir.getName(), testDocs).relations.contains(r)) { // FP
						System.out.println(predict.entity1+" && "+predict.entity2);
					} 
				}
				System.out.println("FN");
				for(Relation r:getBiocDocumentByID(dir.getName(), testDocs).relations) {
					if(!predictRelations.contains(r)) // FN
						System.out.println(r.mesh1+" && "+r.mesh2);
				}
				
			}
			
			// we output all the relations and evaluate them with official tools
			for(Relation predict:predictRelations) {
				String line = dir.getName();
				line += "\t";
				line += "CID";
				line += "\t";
				line += predict.mesh1;
				line += "\t";
				line += predict.mesh2; 
				line +="\n";
				testResultFile.write(line);
			}
			
			// we evaluate by ourselves
			countPredictMesh += predictRelations.size();
			countTrueMesh += getBiocDocumentByID(dir.getName(), wholeTestDocs).relations.size();
			predictRelations.retainAll(getBiocDocumentByID(dir.getName(), wholeTestDocs).relations);
			countCorrectMesh += predictRelations.size();
			
		}
		
		testResultFile.close();
		
		double precisionMesh = Evaluater.getPrecisionV2(countCorrectMesh, countPredictMesh);
		double recallMesh  = Evaluater.getRecallV2(countCorrectMesh, countTrueMesh);
		double f1Mesh = Evaluater.getFMeasure(precisionMesh, recallMesh, 1);
		System.out.println(precisionMesh+"\t"+recallMesh+"\t"+f1Mesh); 
			
			
	}
	
		
	// use training and development data to perform 10-fold cross-validation
	public static void nValidate(String train_dev_xml, BiocXmlParser xmlParser, Tool tool, String nvalid_instance_dir
			, int windowSize, int beamSize, int maxTrainTime, double learningRate, String bioc_dtd, String group) 
	throws Exception {
		// begin to train and test
		System.out.println("beam_size="+beamSize+", train_times="+maxTrainTime
				+", windowSize="+windowSize+", learningRate="+learningRate);
		ArrayList<BiocDocument> docs= xmlParser.parseBiocXmlFile(train_dev_xml);
		boolean preprocess = true;
		if(preprocess) {
			preprocess(docs, tool, nvalid_instance_dir, windowSize);
		}
		
		
		List<List> splitted = new ArrayList<>();	
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(group), "utf-8"));
		String thisLine = null;
		List<File> groupFile = new ArrayList<File>();
		int groupCount=0;
		while ((thisLine = br.readLine()) != null) {
			if(thisLine.isEmpty()) {
				splitted.add(groupFile);
				groupCount++;
				groupFile = new ArrayList<File>();
			} else
				groupFile.add(new File(nvalid_instance_dir+"/"+thisLine));
		}
		//splitted.add(groupFile);
		br.close();
		
		// we need the test doc as gold answers
		HashMap<String, BiocDocument> goldAnswers = new HashMap<String, BiocDocument>();
		for(BiocDocument doc:docs) {
			goldAnswers.put(doc.id, doc);
		}
		MeshDict meshDict = new MeshDict(train_dev_xml, bioc_dtd);
		tool.meshDict = meshDict;
		
		double sumPrecisionMesh = 0;
		double sumRecallMesh = 0;
		double sumF1Mesh = 0;
		
		for(int ctFold=1;ctFold<=splitted.size();ctFold++) { // for each fold
			List<File> trainDataDir = new ArrayList<File>();
			List<File> testDataDir = new ArrayList<File>();
			
			for(int j=0;j<splitted.size();j++) {
				if(j==ctFold-1) {
					for(int k=0;k<splitted.get(j).size();k++)
						testDataDir.add((File)splitted.get(j).get(k));
				} else {
					for(int k=0;k<splitted.get(j).size();k++)
						trainDataDir.add((File)splitted.get(j).get(k));
				}
			}

			ArrayList<PerceptronInputData> trainInputDatas = new ArrayList<PerceptronInputData>();
			ArrayList<PerceptronOutputData> trainOutputDatas = new ArrayList<PerceptronOutputData>();
			for(File dir:trainDataDir) {
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
			Perceptron perceptronRelation = new Perceptron(alphabetRelationType, -1, -1);
			perceptronRelation.setWindowSize(windowSize);
			perceptronRelation.learningRate = learningRate;
			perceptronRelation.average = false;
			ArrayList<PerceptronFeatureFunction> featureFunctions2 = new ArrayList<PerceptronFeatureFunction>(
					Arrays.asList(new RelationFeatures(perceptronRelation)));
			perceptronRelation.setFeatureFunction(null, featureFunctions2);
			perceptronRelation.buildFeatureAlphabet(trainInputDatas, trainOutputDatas, tool);
			System.out.println("begin to train perceptronRelation, features "+perceptronRelation.featureAlphabet.size());
			
			perceptronRelation.trainPerceptron(maxTrainTime, beamSize, trainInputDatas, trainOutputDatas, tool);
			
			int countPredictMesh = 0;
			int countTrueMesh = 0;
			int countCorrectMesh = 0;
			
			for(File dir:testDataDir) {
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
				// if we cannot find id, delete this re
				ArrayList<RelationEntity> toBeDeletedRelation = new ArrayList<>();
				for(RelationEntity re:preRelationEntitys) {
					String mesh1 = tool.meshDict.getMesh(re.entity1.text);
					if(mesh1.equals("-1"))
						toBeDeletedRelation.add(re); 
					else {
						re.entity1.mesh = mesh1;
					}
					
					String mesh2 = tool.meshDict.getMesh(re.entity2.text);
					if(mesh2.equals("-1"))
						toBeDeletedRelation.add(re); 
					else {
						re.entity2.mesh = mesh2;
					}
				}
				for(RelationEntity re: toBeDeletedRelation) {
					preRelationEntitys.remove(re);
				}
				// now we can make relations with mesh id, and get rid of the overlapped
				HashSet<Relation> predictRelations = new HashSet<Relation>();
				for(RelationEntity predict:preRelationEntitys) {
					Relation r = new Relation(null, "CID", predict.entity1.mesh, predict.entity2.mesh);
					if(!predictRelations.contains(r))
						predictRelations.add(r);
					
				}
				
				// we evaluate by ourselves
				countPredictMesh += predictRelations.size();
				countTrueMesh += goldAnswers.get(dir.getName()).relations.size();
				predictRelations.retainAll(goldAnswers.get(dir.getName()).relations);
				countCorrectMesh += predictRelations.size();
				
			}
			
			double precisionMesh = Evaluater.getPrecisionV2(countCorrectMesh, countPredictMesh);
			double recallMesh  = Evaluater.getRecallV2(countCorrectMesh, countTrueMesh);
			double f1Mesh = Evaluater.getFMeasure(precisionMesh, recallMesh, 1);
			System.out.println(precisionMesh+"\t"+recallMesh+"\t"+f1Mesh); 
			
			sumPrecisionMesh += precisionMesh;
			sumRecallMesh += recallMesh;
			sumF1Mesh += f1Mesh;
		}
		
		System.out.println("The macro average of p,r,f1 are "+sumPrecisionMesh/splitted.size()+"\t"+
		sumRecallMesh/splitted.size()+"\t"+Evaluater.getFMeasure(sumPrecisionMesh/splitted.size(), sumRecallMesh/splitted.size(), 1)); 
	}
	
	
	
	public static void preprocess(ArrayList<BiocDocument> docs, Tool tool, String instance_dir, int windowSize) throws Exception {
		File fInstanceDir = new File(instance_dir);
		IoUtils.clearDirectory(fInstanceDir);
		
		for(BiocDocument doc:docs) {
			ArrayList<PerceptronInputData> inputDatas = new ArrayList<PerceptronInputData>();
			ArrayList<PerceptronOutputData> outputDatas = new ArrayList<PerceptronOutputData>();
							
			List<Sentence> mySentences = prepareNlpInfo(doc, tool);
			buildInputData(inputDatas, mySentences, doc.id);
			buildGoldOutputData(doc, outputDatas, mySentences, windowSize, doc.id);
			
			File documentDir = new File(instance_dir+"/"+doc.id);
			documentDir.mkdir();
			for(int k=0;k<inputDatas.size();k++) {
				ObjectSerializer.writeObjectToFile(inputDatas.get(k), documentDir+"/"+k+".input");
				ObjectSerializer.writeObjectToFile(outputDatas.get(k), documentDir+"/"+k+".output");
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
			// parsing
			/*Tree root = tool.lp.apply(tokens);
			root.indexLeaves();
			root.setSpans();
			List<Tree> leaves = root.getLeaves();*/
			
			mySentence.tokens = tokens;
			/*mySentence.root = root;
			mySentence.leaves = leaves;*/
			mySentences.add(mySentence);
			
			offset += sentenceLength;
		}
		
				
		return mySentences;
	}
	
	public static void buildInputData(ArrayList<PerceptronInputData> inputDatas,List<Sentence> mySentences, String id) {
		// build input data
		for(int j=0;j<mySentences.size();j++) {
			Sentence mySentence = mySentences.get(j);
			PerceptronInputData inputdata = new PerceptronInputData(id, j);
			for(int i=0;i<mySentence.tokens.size();i++) {
				CoreLabel token = mySentence.tokens.get(i);
				// build input data
				inputdata.tokens.add(token.word());
				inputdata.offset.add(token.beginPosition());
			}
			inputdata.sentInfo = mySentence;
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
