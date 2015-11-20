package deprecated;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import cn.fox.biomedical.Dictionary;
import cn.fox.biomedical.Sider;
import cn.fox.utils.StopWord;
import cn.fox.machine_learning.Perceptron;
import cn.fox.machine_learning.PerceptronFeatureFunction;
import cn.fox.machine_learning.PerceptronInputData;
import cn.fox.machine_learning.PerceptronOutputData;
import cn.fox.machine_learning.PerceptronStatus;
import cn.fox.nlp.EnglishPos;
import cn.fox.nlp.Segment;
import cn.fox.nlp.Sentence;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.utils.CharCode;
import cn.fox.utils.Evaluater;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import cn.fox.utils.ObjectShuffle;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.FileNameComparator;
import drug_side_effect_utils.LexicalPattern;
import drug_side_effect_utils.MeshDict;
import drug_side_effect_utils.Relation;
import drug_side_effect_utils.RelationEntity;
import drug_side_effect_utils.Tool;
import edu.mit.jwi.IDictionary;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import gnu.trove.TIntArrayList;



public class JointMain {

	public static ArrayList<String> alphabetEntityType = new ArrayList<String>(Arrays.asList("Disease","Chemical"));
	public static ArrayList<String> alphabetRelationType = new ArrayList<String>(Arrays.asList("CID"));
	
	
	
	public static void main(String[] args) throws Exception{
		
		FileInputStream fis = new FileInputStream(args[1]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String pos_tagger = properties.getProperty("pos_tagger");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String bioc_documents = properties.getProperty("bioc_documents");
		String parser = properties.getProperty("parser");
		String sider_dict = properties.getProperty("sider_dict");
		String instance_dir = properties.getProperty("instance_dir");
		//String mesh_dict = properties.getProperty("mesh_dict");
		String jochem_dict = properties.getProperty("jochem_dict");
		String ctdchem_dict = properties.getProperty("ctdchem_dict");
		String ctdmedic_dict = properties.getProperty("ctdmedic_dict");
		String chemical_element_abbr = properties.getProperty("chemical_element_abbr");
		String drug_dict = properties.getProperty("drug_dict");
		String disease_dict = properties.getProperty("disease_dict");
		String beam_size = properties.getProperty("beam_size");
		String disease_max_len = properties.getProperty("disease_max_len");
		String chemical_max_len = properties.getProperty("chemical_max_len");
		String perceptron_ser = properties.getProperty("perceptron_ser");
		String max_train_times = properties.getProperty("max_train_times");
		String converge_threshold = properties.getProperty("converge_threshold");
		String max_weight = properties.getProperty("max_weight");
		String stop_word = properties.getProperty("stop_word");
		
		TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "ptb3Escaping=false");
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'}, false, common_english_abbr);
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.BOTH);
		Morphology morphology = new Morphology();
		LexicalizedParser lp = LexicalizedParser.loadModel(parser);
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
	    IDictionary dict = new edu.mit.jwi.Dictionary(new URL("file", null, wordnet_dict));
		dict.open();
		Sider sider = new Sider(sider_dict);
		MeshDict meshDict = new MeshDict(bioc_documents, bioc_dtd);
		Pattern complexNounPattern = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]*(-[a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]+)+[a-zA-Z0-9]");
		Dictionary jochem = new Dictionary(jochem_dict, 6);
		Dictionary ctdchem = new Dictionary(ctdchem_dict, 6);
		Dictionary ctdmedic = new Dictionary(ctdmedic_dict, 6);
		Dictionary chemElem = new Dictionary(chemical_element_abbr, 1);
		Dictionary drugbank = new Dictionary(drug_dict, 6);
		Dictionary humando = new Dictionary(disease_dict, 6);
		StopWord stopWord = new StopWord(stop_word);

		Tool tool = new Tool();
		tool.tokenizerFactory = tokenizerFactory;
		tool.sentSplit = sentSplit;
		tool.tagger = tagger;
		tool.morphology = morphology;
		tool.lp = lp;
		tool.gsf = gsf;
		tool.dict = dict;
		tool.sider = sider;
		tool.meshDict = meshDict;
		tool.complexNounPattern = complexNounPattern;
		tool.jochem = jochem;
		tool.ctdchem = ctdchem;
		tool.ctdmedic = ctdmedic;
		tool.chemElem = chemElem;
		tool.drugbank = drugbank;
		tool.humando = humando;
		tool.stopWord = stopWord;
		
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D://biocreative//2015//cdr_sample//CDR_sample.xml");
		//ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile(bioc_documents);
		
		/*File fInstanceDir = new File(instance_dir);
		IoUtils.clearDirectory(fInstanceDir);
		for(int j=0;j<documents.size();j++) {
			BiocDocument document = documents.get(j);
			ArrayList<PerceptronInputData> inputDatas = new ArrayList<PerceptronInputData>();
			ArrayList<PerceptronOutputData> outputDatas = new ArrayList<PerceptronOutputData>();
			List<Sentence> mySentences = prepareNlpInfo(document, tool);
			buildInputData(inputDatas, mySentences);
			buildGoldOutputData(document, outputDatas, mySentences);
			
			File documentDir = new File(instance_dir+"/"+document.id);
			documentDir.mkdir();
			for(int k=0;k<inputDatas.size();k++) {
				ObjectSerializer.writeObjectToFile(inputDatas.get(k), documentDir+"/"+k+".input");
				ObjectSerializer.writeObjectToFile(outputDatas.get(k), documentDir+"/"+k+".output");
			}
		}*/
		
		
		
		// prepare information for creating perceptron
		int beamSize = Integer.parseInt(beam_size);
		TIntArrayList d = new TIntArrayList();
		d.add(Integer.parseInt(disease_max_len));
		d.add(Integer.parseInt(chemical_max_len));
		
		
		nValidate(1, documents, tool, beamSize, d, instance_dir, perceptron_ser, Integer.parseInt(max_train_times), 
				Float.parseFloat(converge_threshold), Double.parseDouble(max_weight));
		

	}
	
	public static void nValidate(int nFold, ArrayList<BiocDocument> documents, Tool tool, int beamSize, TIntArrayList d, 
			String instance_dir, String ser, int max_train_times, float converge_threshold, double max_weight) throws Exception {
		double testPercent = 1.0/nFold;
		double trainPercent = 1-testPercent;
				
		
		double sumPrecisionEntity = 0;
		double sumRecallEntity = 0;
		double sumF1Entity = 0;
		double sumPrecisionMesh = 0;
		double sumRecallMesh = 0;
		double sumF1Mesh = 0;
		
		Perceptron best = null;
		double bestf1 = 0;

		for(int i=1;i<=nFold;i++) { // for each fold
			// split the data
			List[] splitted = null;
			if(nFold!=1)
				splitted = ObjectShuffle.split(documents, new double[] {trainPercent, testPercent});
			else {
				splitted = new ArrayList[2];
				splitted[0] = new ArrayList<>(documents);
				splitted[1] = new ArrayList<>(documents);
			}
			

			// prepare the training data
			ArrayList<PerceptronInputData> trainInputDatas = new ArrayList<PerceptronInputData>();
			ArrayList<PerceptronOutputData> trainOutputDatas = new ArrayList<PerceptronOutputData>();
			for(int j=0;j<splitted[0].size();j++) {
				BiocDocument document = (BiocDocument)splitted[0].get(j);
				List<File> files = Arrays.asList(new File(instance_dir+"/"+document.id).listFiles());
				Collections.sort(files, new FileNameComparator());
				for(File file:files) {
					if(file.getName().indexOf("input") != -1) {
						trainInputDatas.add((PerceptronInputData)ObjectSerializer.readObjectFromFile(file.getAbsolutePath()));
					} else {
						trainOutputDatas.add((PerceptronOutputData)ObjectSerializer.readObjectFromFile(file.getAbsolutePath()));
					}
				}
			}
			
			// create a perceptron
			Perceptron perceptron = new Perceptron1(alphabetEntityType, alphabetRelationType, d, converge_threshold, max_weight);
			ArrayList<PerceptronFeatureFunction> featureFunctions1 = new ArrayList<PerceptronFeatureFunction>(
					Arrays.asList(new EntityFeatures(perceptron)
					));
			ArrayList<PerceptronFeatureFunction> featureFunctions2 = new ArrayList<PerceptronFeatureFunction>(
					Arrays.asList(new RelationFeatures(perceptron)
					));
			perceptron.setFeatureFunction(featureFunctions1, featureFunctions2);
			perceptron.buildFeatureAlphabet(trainInputDatas, trainOutputDatas, tool);
			System.out.println("begin to train, features "+perceptron.featureAlphabet.size());
			
			// train
			/*Object[] keys = perceptron.featureAlphabet.keys();
			int[] values = perceptron.featureAlphabet.getValues();
			for(int kk=0;kk<values.length;kk++) {
				if(values[kk]==62) {
					System.out.println(keys[kk]);
				}
				if(values[kk]==69) {
					System.out.println(keys[kk]);
				}
			}*/
			perceptron.trainPerceptron(max_train_times, beamSize, trainInputDatas, trainOutputDatas, tool);
			System.out.println(perceptron.getW1().toString(true));
			System.out.println(perceptron.getW2().toString(true));
			// test
			long startTime = System.currentTimeMillis();
			int countPredictEntity = 0;
			int countTrueEntity = 0;
			int countCorrectEntity = 0;
			int countPredictMesh = 0;
			int countTrueMesh = 0;
			int countCorrectMesh = 0;
			for(int j=0;j<splitted[1].size();j++) { // for each test file
				
				ArrayList<Entity> wrongEntity = new ArrayList<Entity>(); // debug
				ArrayList<Entity> lostEntity = new ArrayList<Entity>();
				// prepare input
				BiocDocument document = (BiocDocument)splitted[1].get(j);
				ArrayList<PerceptronInputData> inputDatas = new ArrayList<PerceptronInputData>();
				List<File> files = Arrays.asList(new File(instance_dir+"/"+document.id).listFiles());
				Collections.sort(files, new FileNameComparator());
				for(File file:files) {
					if(file.getName().indexOf("input") != -1) {
						inputDatas.add((PerceptronInputData)ObjectSerializer.readObjectFromFile(file.getAbsolutePath()));
					} 
				}
				// predict
				ArrayList<Entity> preEntities = new ArrayList<Entity>();
				ArrayList<RelationEntity> preRelationEntitys = new ArrayList<RelationEntity>();
				for(PerceptronInputData inputdata:inputDatas) {
					PerceptronStatus returnType = perceptron.beamSearch((PerceptronInputData1)inputdata, null, false, beamSize, tool);
					PerceptronOutputData1 output = (PerceptronOutputData1)returnType.z;
					
					for(int k=0;k<output.segments.size();k++) {
						Entity segment = output.segments.get(k);
						if(segment.type.equals("Disease") || segment.type.equals("Chemical"))
							preEntities.add(segment);
						else {
							// we use some rules to improve the recall
							//postprocessLocal(inputdata, output, k, tool, preEntities);
						}
					}
					preRelationEntitys.addAll(output.relations);
					
				}
				
				/*for(int k=0;k<preEntities.size();k++) {
					for(int m=0;m<k;m++) {
						postprocessRelation(preEntities.get(k), preEntities.get(m), tool, preRelationEntitys);
					}
				}*/
				
				// after the entired document has been done, some obvious errors can be fixed here to improve the precision
				//postprocessGlobal(tool, preEntities, preRelationEntitys);
				
				// evaluate entity first, this should perform before "add mesh", because "add mesh" may delete some entities.
				countPredictEntity+= preEntities.size();
				wrongEntity.addAll(preEntities);
				
				ArrayList<Entity> goldEntities = document.entities;
				countTrueEntity += goldEntities.size();
				lostEntity.addAll(goldEntities);

				for(Entity preEntity:preEntities) {
					for(Entity goldEntity:goldEntities) {
						if(preEntity.equals(goldEntity)) {
							countCorrectEntity++;
							wrongEntity.remove(preEntity);
							lostEntity.remove(goldEntity);
							break;
						}
					}
				}
				
				/*System.out.println("!!! "+document.id);
				System.out.println("!!! wrong Entity");
				for(Entity wrong:wrongEntity)
					System.out.println(wrong);
				System.out.println("!!! lost Entity");
				for(Entity lost:lostEntity)
					System.out.println(lost);*/
			
				// add mesh for the predicted entities
				ArrayList<Entity> toBeDeleted = new ArrayList<>();
				for(Entity pre:preEntities) {
					
					String mesh = tool.meshDict.getMesh(pre.text);
					if(mesh.equals("-1"))
						toBeDeleted.add(pre); // because the entity without mesh is useless for relation
					else {
						pre.mesh = mesh;
					}
				}
				// some relationentitys may has the entity without mesh, we delete them too.
				ArrayList<RelationEntity> toBeDeletedRelation = new ArrayList<>();
				for(RelationEntity re: preRelationEntitys) {
					if(toBeDeleted.contains(re.entity1) || toBeDeleted.contains(re.entity2))
						toBeDeletedRelation.add(re);
				}
				for(RelationEntity re: toBeDeletedRelation) {
					preRelationEntitys.remove(re);
				}
				for(Entity delete:toBeDeleted) {
					preEntities.remove(delete);
				}
				
				// compute with mesh
				HashSet<Relation> predictRelations = new HashSet<Relation>();
				for(RelationEntity predict:preRelationEntitys) {
					Relation r = new Relation(null, "CID", predict.entity1.mesh, predict.entity2.mesh);
					predictRelations.add(r);
				}
				countPredictMesh += predictRelations.size();
				countTrueMesh += document.relations.size();
				predictRelations.retainAll(document.relations);
				countCorrectMesh += predictRelations.size();
			}
			long endTime = System.currentTimeMillis();
			System.out.println("test finished with "+(endTime-startTime)+" ms");
			
			double precisionEntity = Evaluater.getPrecisionV2(countCorrectEntity, countPredictEntity);
			double recallEntity  = Evaluater.getRecallV2(countCorrectEntity, countTrueEntity);
			double f1Entity = Evaluater.getFMeasure(precisionEntity, recallEntity, 1);
			System.out.println(i+" fold: entity p,r,f1 are "+precisionEntity+" "+recallEntity+" "+f1Entity); 
			
			double precisionMesh = Evaluater.getPrecisionV2(countCorrectMesh, countPredictMesh);
			double recallMesh  = Evaluater.getRecallV2(countCorrectMesh, countTrueMesh);
			double f1Mesh = Evaluater.getFMeasure(precisionMesh, recallMesh, 1);
			System.out.println(i+" fold: Mesh p,r,f1 are "+precisionMesh+" "+recallMesh+" "+f1Mesh); 
			
			sumPrecisionEntity += precisionEntity;
			sumRecallEntity += recallEntity;
			sumF1Entity += f1Entity;
			sumPrecisionMesh += precisionMesh;
			sumRecallMesh += recallMesh;
			sumF1Mesh += f1Mesh;
			
			
			if(f1Mesh>bestf1) {
				bestf1 = f1Mesh;
				best = perceptron;
			}
			
			
		}
		ObjectSerializer.writeObjectToFile(best, ser);
		System.out.println("The macro average of entity p,r,f1 are "+sumPrecisionEntity/nFold+" "+sumRecallEntity/nFold+" "+sumF1Entity/nFold); 
		System.out.println("The macro average of Mesh p,r,f1 are "+sumPrecisionMesh/nFold+" "+sumRecallMesh/nFold+" "+sumF1Mesh/nFold); 
	}
	
	public static void postprocessRelation(Entity entity1, Entity entity2, Tool tool, ArrayList<RelationEntity> preRelationEntitys) {
		Entity drug = null;
		Entity disease = null;
		if(entity1.type.equals("Chemical") && entity2.type.equals("Disease")) {
			drug = entity1;
			disease = entity2;
		}
		else if(entity1.type.equals("Disease") && entity2.type.equals("Chemical")) {
			drug = entity2;
			disease = entity1;
		} else 
			return;
		
		for(RelationEntity pre:preRelationEntitys) {
			RelationEntity temp = new RelationEntity("CID", drug, disease);
			if(temp.equals(pre))
				return;
		}
			
		// search sider
		if(tool.sider.contains(drug.text, disease.text)) {
			preRelationEntitys.add(new RelationEntity("CID", drug, disease));
			return;
		}
		
		// coreference
		for(RelationEntity pre:preRelationEntitys) {
			if(pre.entity1.text.equalsIgnoreCase(drug.text) && pre.entity2.text.equalsIgnoreCase(disease.text)) {
				preRelationEntitys.add(new RelationEntity("CID", drug, disease));
				return;
			} else if(pre.entity1.text.equalsIgnoreCase(disease.text) && pre.entity2.text.equalsIgnoreCase(drug.text)) {
				preRelationEntitys.add(new RelationEntity("CID", drug, disease));
				return;
			} 
		}
		
	}
	
	public static void postprocessGlobal(Tool tool, ArrayList<Entity> preEntities, ArrayList<RelationEntity> preRelationEntitys) {
		ArrayList<Entity> toBeDeleted = new ArrayList<>();
		
		for(Entity pre:preEntities) {
			if(tool.stopWord.contains(pre.text))
				toBeDeleted.add(pre);
			else if(pre.text.length()==1 && CharCode.isLowerCase(pre.text.charAt(0)))
				toBeDeleted.add(pre);
			else if(LexicalPattern.getAlphaNum(pre.text)==0)
				toBeDeleted.add(pre);
		}
		
		// some relationentitys may has the entity to be deleted, we delete them too.
		ArrayList<RelationEntity> toBeDeletedRelation = new ArrayList<>();
		for(RelationEntity re: preRelationEntitys) {
			if(toBeDeleted.contains(re.entity1) || toBeDeleted.contains(re.entity2))
				toBeDeletedRelation.add(re);
			else if(re.entity1.type.equals(re.entity2.type))
				toBeDeletedRelation.add(re);
		}
		for(RelationEntity re: toBeDeletedRelation) {
			preRelationEntitys.remove(re);
		}
		for(Entity delete:toBeDeleted) {
			preEntities.remove(delete);
		}
		
	}
	
	// don't need to use StopWord here, we will delete them in the  postprocessGlobal
	public static void postprocessLocal(PerceptronInputData input, PerceptronOutputData1 output, int k, Tool tool, ArrayList<Entity> preEntities) {
		Entity segment = output.segments.get(k);
		// dict
		if((tool.humando.contains(segment.text) || tool.ctdmedic.contains(segment.text)) 
				/*&& !StopWord.contains(segment.text)*/) {
			preEntities.add(new Entity(null, "Disease", segment.offset, segment.text, null));
			return;
		}
		if((tool.chemElem.containsCaseSensitive(segment.text) || tool.drugbank.contains(segment.text) ||
				tool.jochem.contains(segment.text) || tool.ctdchem.contains(segment.text))
				/*&& !StopWord.contains(segment.text)*/) {
			preEntities.add(new Entity(null, "Chemical", segment.offset, segment.text, null));
			return;
		}
		
		// trigger word: if a segment contains "-"+trigger, it may be a chemical entity
		String word = segment.text.toLowerCase();
		String[] triggers = new String[]{"-induced","-associated","-related"};
		for(String trigger:triggers) {
			int posEnd = word.indexOf(trigger);
			int posStart = word.lastIndexOf(" ", posEnd)+1;
			
			if(posEnd != -1 && posStart != -1) {
				if(segment.text.charAt(posEnd-1) == ')')
					posEnd --;
				String s = segment.text.substring(posStart,posEnd);
				
				/*if(!StopWord.contains(s))*/ {
					int entityStart = segment.offset+posStart;
					preEntities.add(new Entity(null, "Chemical", entityStart, s, null));
					return;
				}
			}
		}
		// coreference: if a token has been regonized as a entity before, it should be now
		for(Entity pre:preEntities) {
			if(pre.text.equalsIgnoreCase(segment.text)) {
				preEntities.add(new Entity(null, pre.type, segment.offset, segment.text,null));
				return;
			}
		}
		
		//  abbr
		if(JointMain.isAbbr((PerceptronInputData1)input, output, k)) {
			if(!preEntities.isEmpty()) {
				// the type is the same with the pre-closest entity
				Entity pre = preEntities.get(preEntities.size()-1);
				// if each letter of the token is in the previous entity
				char[] letters = segment.text.toLowerCase().toCharArray();
				String preText = pre.text.toLowerCase();
				int i=0;
				int from = 0;  // record the matched postion
				for(;i<letters.length;i++) {
					if((from=preText.indexOf(letters[i], from)) == -1) {
						break;
					} else
						from++;
				}
				if(i==letters.length) {
					preEntities.add(new Entity(null, pre.type, segment.offset, segment.text,null));
					return;
				}
			}
			
		}
		
	}
	
	
	
	public static List<Sentence> prepareNlpInfo(BiocDocument document, Tool tool) {
		List<Sentence> mySentences = new ArrayList<Sentence>();
		String content = document.title+" "+document.abstractt;
		//content = content.replaceAll("-", " ");
		int offset = 0;
		// sentence segmentation
		List<String> sentences = tool.sentSplit.splitWithFilters(content);
		for(int mmm = 0;mmm<sentences.size();mmm++) {
			String sentence = sentences.get(mmm);
			
			// tokenize
			/*Tokenizer<CoreLabel> tok = tool.tokenizerFactory.getTokenizer(new StringReader(sentence));
			List<CoreLabel> tokens = tok.tokenize();*/
			ArrayList<Segment> given = new ArrayList<Segment>();
			/*Matcher m1 = tool.complexNounPattern.matcher(sentence);
			while(m1.find()) {
				String temp = m1.group();
				int offsetInSent = m1.start();
				given.add(new Segment(null, offset+offsetInSent, offset+offsetInSent+temp.length()));
			}*/
			
			ArrayList<Segment> segments = TokenizerWithSegment.tokenize(offset, sentence, given);
			List<CoreLabel> tokens = new ArrayList<CoreLabel>();
			for(Segment segment:segments) {
				if(segment.word.indexOf("'-") != -1)
					System.out.print("");
				CoreLabel token = new CoreLabel();
				token.setWord(segment.word);
				token.setValue(segment.word);
				token.setBeginPosition(segment.begin);
				token.setEndPosition(segment.end);
				tokens.add(token);
			}
			
			int sentenceLength = sentence.length();
			/*for(int i=0;i<tokens.size();i++) {
				CoreLabel token = tokens.get(i);
				token.setBeginPosition(offset+token.beginPosition());
				token.setEndPosition(offset+token.endPosition());
			}*/
			// pos tagging
			tool.tagger.tagCoreLabels(tokens);
			// lemma
			for(int i=0;i<tokens.size();i++)
				tool.morphology.stem(tokens.get(i));
			// parsing
			Tree root = tool.lp.apply(tokens);
			root.indexLeaves();
			root.setSpans();
			List<Tree> leaves = root.getLeaves();
			// depend
			GrammaticalStructure gs = tool.gsf.newGrammaticalStructure(root);
			List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
		    SemanticGraph depGraph = new SemanticGraph(tdl);
		    
		    Sentence mySentence = new Sentence();
			//mySentence.text = sentence;
			mySentence.offset = offset;
			mySentence.length = sentenceLength;
			mySentence.tokens = tokens;
			mySentence.root = root;
			mySentence.leaves = leaves;
			mySentence.depGraph = depGraph;
			mySentences.add(mySentence);
			
			offset += sentenceLength;
		}
		
		return mySentences;
	}
	
	public static void buildInputData(ArrayList<PerceptronInputData> inputDatas,List<Sentence> mySentences) {
		// build input data
		for(Sentence mySentence:mySentences) {
			PerceptronInputData1 inputdata = new PerceptronInputData1();
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
	
	public static void buildGoldOutputData(BiocDocument document, ArrayList<PerceptronOutputData> outDatas,List<Sentence> mySentences) {
		// build output data
		for(Sentence mySentence:mySentences) {
			PerceptronOutputData1 outputdata = new PerceptronOutputData1(true, mySentence.tokens.size());
			Entity entity = new Entity(null, null, 0, null, null);
			Entity oldGold = null;
			// for each token
			for(int i=0;i<mySentence.tokens.size();i++) {
				CoreLabel token = mySentence.tokens.get(i);
				// build the segments of output data begin
				Entity newGold = document.isInsideAGoldEntityAndReturnIt(token.beginPosition(), token.endPosition()-1);
				if(newGold == null) {
					if(entity.text!=null) { // save the old
						outputdata.segments.add(entity);
						entity = new Entity(null, null, 0, null, null);
					}
					// save the current, because the empty type segment has only one length.
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
			/*
			 * Note: This way may cause the problem like, if two entities with a relation occur in different sentences,
			 * we cannot find this kind of relation. However, it seems OK because in the official data guidline, there 
			 * is a rule named "The relation should be explicitly mentioned in the abstract" which means mostly two entities
			 * should be mentioned in one sentence when they have a relation. 
			 */
			for(int i=0;i<outputdata.segments.size();i++) {
				for(int j=0;j<i;j++) {
					Entity entity1 = outputdata.segments.get(i);
					Entity entity2 = outputdata.segments.get(j);
					if(entity1.type.equals(Perceptron.EMPTY) || entity2.type.equals(Perceptron.EMPTY)) continue;
					//if(RelationGold.twoEntitiesHaveRelation(document,entity1, entity2)) {
						RelationEntity relationEntity = new RelationEntity(alphabetRelationType.get(0), entity1, entity2);
						outputdata.relations.add(relationEntity);
					//}
				}
			}
			
			// build the relations of output data end
			outDatas.add(outputdata);
		}
	}
	
	public static CoreLabel getHeadWordOfSegment(Entity segment, PerceptronInputData1 input) {
		if(segment.start==segment.end)
			return input.sentInfo.tokens.get(segment.start);
		else {
			int i=segment.start;
			for(;i<=segment.end;i++){ // in order to find a prep
				if(EnglishPos.getType(input.sentInfo.tokens.get(i).tag()) == EnglishPos.Type.PREP)
					break;
			}
			if(i>segment.end || i==segment.start) { // not find a prep or the first token is prep
				return input.sentInfo.tokens.get(segment.end);
			} else { // use the word before prep as head
				return input.sentInfo.tokens.get(i-1);
			}
			
		}
	}
	
	public static String getPrefix(CoreLabel token) {
		String lem = token.lemma().toLowerCase();
		int len = lem.length()>4 ? 4:lem.length();
		return lem.substring(0, len);
	}
	
	public static String getSuffix(CoreLabel token) {
		String lem = token.lemma().toLowerCase();
		int len = lem.length()>4 ? 4:lem.length();
		return lem.substring(lem.length()-len, lem.length());
	}

	public static boolean isAbbr(PerceptronInputData1 input,PerceptronOutputData1 output, int k) {
		if(output.segments.get(k).text.length()>1 && // it's a all upper token 
				(LexicalPattern.getUpCaseNum(output.segments.get(k).text) == output.segments.get(k).text.length() || // has a "(" and ")" around it
				((output.segments.get(k).start>0 && input.sentInfo.tokens.get(output.segments.get(k).start-1).word().equals("(") && output.segments.get(k).end<input.sentInfo.tokens.size()-1 && input.sentInfo.tokens.get(output.segments.get(k).end+1).word().equals(")"))))
				) {
			return true;
		} else
			return false;
	}
}
