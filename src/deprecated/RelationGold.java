package deprecated;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.MaxEnt;
import cc.mallet.classify.MaxEntTrainer;
import cc.mallet.classify.Trial;
import cc.mallet.fst.CRF;
import cc.mallet.pipe.Csv2FeatureVector;
import cc.mallet.pipe.Input2CharSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.pipe.iterator.FileIterator;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.Labeling;
import cc.mallet.types.Sequence;
import cn.fox.biomedical.Sider;
import cn.fox.mallet.FeatureVectorMaker;
import cn.fox.mallet.MalletClassifierInstance;
import cn.fox.mallet.MalletSequenceTaggerInstance;
import cn.fox.nlp.EnglishPos;
import cn.fox.nlp.Punctuation;
import cn.fox.nlp.Segment;
import cn.fox.nlp.Sentence;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.stanford.StanfordTree;
import cn.fox.stanford.Tokenizer;
import cn.fox.utils.Evaluater;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import cn.fox.utils.ObjectShuffle;
import cn.fox.utils.WordNetUtil;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.LexicalPattern;
import drug_side_effect_utils.Relation;
import drug_side_effect_utils.RelationEntity;
import drug_side_effect_utils.Tool;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import gnu.trove.TObjectDoubleHashMap;


public class RelationGold {


	public static void main(String[] args) throws Exception{
		
		FileInputStream fis = new FileInputStream(args[1]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String pos_tagger = properties.getProperty("pos_tagger");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String relation_instance_dir = properties.getProperty("relation_instance_dir");
		String bioc_documents = properties.getProperty("bioc_documents");
		String relation_classifier_ser = properties.getProperty("relation_classifier_ser");
		String parser = properties.getProperty("parser");
		String sider_dict = properties.getProperty("sider_dict");
		
		TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "ptb3Escaping=false");
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'}, false, common_english_abbr);
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.BOTH);
		Morphology morphology = new Morphology();
		LexicalizedParser lp = LexicalizedParser.loadModel(parser);
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
	    IDictionary dict = new Dictionary(new URL("file", null, wordnet_dict));
		dict.open();
		Sider sider = new Sider(sider_dict);
		Tokenizer tokenizer = new Tokenizer(true, ' ');

		Tool tool = new Tool();
		tool.tokenizerFactory = tokenizerFactory;
		tool.sentSplit = sentSplit;
		tool.tagger = tagger;
		//tool.xmlParser = xmlParser;
		tool.morphology = morphology;
		tool.lp = lp;
		tool.gsf = gsf;
		tool.dict = dict;
		tool.sider = sider;
		tool.tokenizer = tokenizer;
		
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile(bioc_documents);
		//ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D://biocreative//2015//cdr_sample//CDR_sample.xml");
		
		File fInstanceDir = new File(relation_instance_dir);
		IoUtils.clearDirectory(fInstanceDir);
		for(BiocDocument document:documents)
			preprocess(tool, document, relation_instance_dir, document.entities);
		
		nValidate(10, relation_instance_dir, relation_classifier_ser, documents, tool);
		
	}
	
	// Given a dir with document instances, predict the relation between entities.
	public static ArrayList<RelationEntity> predictRelationEntity(File documentDir, Classifier classifier, Tool tool) throws Exception{ 
		ArrayList<RelationEntity> preRelationEntitys = new ArrayList<RelationEntity>();
		for(String serRelationInstance:documentDir.list()) {
			RelationInstance ri = (RelationInstance)ObjectSerializer.readObjectFromFile(documentDir+"//"+serRelationInstance);
			FeatureVector vector = FeatureVectorMaker.make(classifier.getAlphabet(), ri.map);
			Instance instance = new Instance(vector, null, null, null);
			Label label = classifier.classify(instance).getLabeling().getBestLabel();
			if(label.getEntry().equals("1"))
				preRelationEntitys.add(new RelationEntity("CID", ri.former, ri.latter));
			else {
				// if the label is 0, we use some rules to improve the performance
				postprocessLocal( ri,tool, preRelationEntitys);
			}
		}
		return preRelationEntitys;
	}
	
	// for the relation extraction with gold entities, we only need to evaluate the p,r,f1 of label 1.
	public static void nValidate(int nFold, String instanceDir, String ser, ArrayList<BiocDocument> documents, Tool tool) throws Exception{
		double testPercent = 1.0/nFold;
		double trainPercent = 1-testPercent;
		File instanceFiles = new File(instanceDir);
		
		double sumPrecisionMesh = 0;
		double sumRecallMesh = 0;
		double sumF1Mesh = 0;
		Classifier best = null;
		double bestf1 = 0;
		for(int i=0;i<nFold;i++) { // for each fold
			List[] splitted = null;
			if(nFold!=1) {
				// split the data
				splitted = ObjectShuffle.split(Arrays.asList(instanceFiles.listFiles()), new double[] {trainPercent, testPercent});
			} else {
				splitted = new ArrayList[2];
				splitted[0] = new ArrayList<File>(Arrays.asList(instanceFiles.listFiles()));
				splitted[1] = new ArrayList<File>(Arrays.asList(instanceFiles.listFiles()));
			}
			
			// each splitted[j] denotes a directory, and the directory name denotes the document id
			RiSer2FvPipe pipe = new RiSer2FvPipe();
			InstanceList trainData = new InstanceList (pipe);
			for(int j=0;j<splitted[0].size();j++) {
				File documentDir = (File)splitted[0].get(j);
				trainData.addThruPipe(new FileIterator(documentDir));
			}
			
			// train
			Classifier classifier = train(trainData, null);
			// test
			int countPredictMesh = 0;
			int countTrueMesh = 0;
			int countCorrectMesh = 0;
			
			for(int j=0;j<splitted[1].size();j++) { // for each test file
				File documentDir = (File)splitted[1].get(j);
				BiocDocument document = DrugDiseaseDetect.getBiocDocumentByID(documentDir.getName(), documents);
				
				ArrayList<RelationEntity> preRelationEntitys = predictRelationEntity(documentDir, classifier, tool);
				
				// after the entired document has been done, some obvious errors can be fixed here to improve the precision
				postprocessGlobal(tool, preRelationEntitys);
				
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
			
			double precisionMesh = Evaluater.getPrecisionV2(countCorrectMesh, countPredictMesh);
			double recallMesh  = Evaluater.getRecallV2(countCorrectMesh, countTrueMesh);
			double f1Mesh = Evaluater.getFMeasure(precisionMesh, recallMesh, 1);
			sumPrecisionMesh += precisionMesh;
			sumRecallMesh += recallMesh;
			sumF1Mesh += f1Mesh;
			System.out.println("The loop "+i+" of Mesh p,r,f1 are "+precisionMesh+" "+recallMesh+" "+f1Mesh); 
			
			if(f1Mesh>bestf1) {
				bestf1 = f1Mesh;
				best = classifier;
			}
			
		}
		// save the best
		ObjectSerializer.writeObjectToFile(best, ser);
		System.out.println("The macro average of Mesh p,r,f1 are "+sumPrecisionMesh/nFold+" "+sumRecallMesh/nFold+" "+sumF1Mesh/nFold); 
	}
	
	public static void postprocessGlobal(Tool tool, ArrayList<RelationEntity> preRelationEntitys) {
		// if the type of entity1 and entity2 are the same, it must be wrong
		ArrayList<RelationEntity> toBeDeleted = new ArrayList<>();
		for(RelationEntity re:preRelationEntitys) {
			if(re.entity1.type.equals(re.entity2.type))
				toBeDeleted.add(re);
				
		}
		
		for(RelationEntity delete:toBeDeleted) {
			preRelationEntitys.remove(delete);
		}
	}
	
	public static void postprocessLocal(RelationInstance ri, Tool tool, ArrayList<RelationEntity> preRelationEntitys) {
		Entity drug = null;
		Entity disease = null;
		if(ri.former.type.equals("Chemical") && ri.latter.type.equals("Disease")) {
			drug = ri.former;
			disease = ri.latter;
		}
		else if(ri.former.type.equals("Disease") && ri.latter.type.equals("Chemical")) {
			drug = ri.latter;
			disease = ri.former;
		} else 
			return;
			
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
	
	public static String preprocess(Tool tool, BiocDocument document, String instanceOutPath, List<Entity> inputEntities) throws Exception{
		//Alphabet features = new Alphabet();
		//Integer name = 0;
		
			int idInDocument = 1;
			File documentDir = new File(instanceOutPath+"//"+document.id);
			if(!documentDir.exists())
				documentDir.mkdir();
			//ArrayList<MalletClassifierInstance> intances = new ArrayList<MalletClassifierInstance> ();

			// prepare all the information we will need
			String content = document.title+" "+document.abstractt;
			List<Sentence> mySentences = prepareAllTheInfo(tool, content, inputEntities);
			/*
			 *  iterate all the gold entities, select two and make a instance based on their context.
			 *  We only iterate the lower triangular matrix because:
			 *  not considering the same entity. 
			 *  not considering the directed relation with the same entities, eg. AB & BA(only select one)
			 *  
			 */
			for(int i=0;i<inputEntities.size();i++) {
				for(int j=0;j<i;j++) {
					Entity entity1 = inputEntities.get(i);
					Entity entity2 = inputEntities.get(j);
					// not considering the entities in different sentences
					Sentence sent = getTwoEntitesSentence(mySentences ,entity1, entity2);
					if(sent== null) continue;
					
					Entity former = entity1.offset>entity2.offset ? entity2:entity1;
					Entity latter = entity1.offset>entity2.offset ? entity1:entity2;
					int formerIndex = -1; // the index of tokens corresponding the former entity, note that the tokens are tokenized by segments
					int latterIndex = -1;  
					for(int kk=0;kk<sent.tokens.size();kk++) {
						if(former.offset==sent.tokens.get(kk).beginPosition() && former.offset+former.text.length()==sent.tokens.get(kk).endPosition())
							formerIndex = kk;
						else if(latter.offset==sent.tokens.get(kk).beginPosition() && latter.offset+latter.text.length()==sent.tokens.get(kk).endPosition())
							latterIndex = kk;
					}
					if(formerIndex==-1 || latterIndex==-1)
						continue; // there may be some overlapped annotation, like 
								// 3107448	481	503	drug-induced hepatitis	Disease	D056486	
								//	3107448	494	503	hepatitis	Disease	D056486	
					
					if(sent.tokens.get(formerIndex).lemma().equals(sent.tokens.get(latterIndex).lemma())) 
						continue; // not considering the entity with similar text
					
										
					// now we get the effective instance
					//MalletClassifierInstance instance = new MalletClassifierInstance();
					//Instance instance = new Instance(null, null, null, null);
					//instance.name = name.toString();
					// add features to the instance
					TObjectDoubleHashMap<String> map = new TObjectDoubleHashMap<String>();
					prepareFeatures(tool, map, former, latter, sent, inputEntities, formerIndex, latterIndex);
					if(map.isEmpty())
						continue;
					//FeatureVector vector = FeatureVectorMaker.make(features, map, false);
					//instance.setData(vector);
					// judge whether the two entities have relations, and set label
					String label = null;
					if(twoEntitiesHaveRelation(document,former, latter))
						label = "1";
					else
						label = "0";
					
					//intances.add(instance);
					RelationInstance ri = new RelationInstance(former, latter, map, label);
					ObjectSerializer.writeObjectToFile(ri, documentDir.getAbsolutePath()+"//"+idInDocument);
					idInDocument++;
					//name++;
				}
			}
			
			//MalletClassifierInstance.writeAllInstances2File(intances, instanceOutPath+"//"+document.id+".instance");
			return documentDir.getAbsolutePath();
		
		
		// output
		//MalletClassifierInstance.writeAllInstances2File(intances, instanceOutPath);
	}
	
	public static List<Sentence> prepareAllTheInfo(Tool tool, String content, List<Entity> inputEntities) {

		int offset = 0;
		List<Sentence> mySentences = new ArrayList<Sentence>();
		// sentence segmentation
		List<String> sentences = tool.sentSplit.splitWithFilters(content);
		for(String sentence:sentences) {
			int sentenceLength = sentence.length();
			
			Sentence mySentence = new Sentence();
			mySentence.text = sentence;
			mySentence.offset = offset;
			mySentence.length = sentenceLength;
			mySentences.add(mySentence);
			
			offset += sentenceLength;
		}
		
		for(Sentence sent:mySentences) {
			// Tokenize the sentence based on segments.
			List<Entity> entitiesInSent = getEntitiesOfSentence(sent, inputEntities);
			ArrayList<Segment> given = new ArrayList<Segment>();
			for(Entity entity:entitiesInSent) {
				given.add(new Segment(null, entity.offset, entity.offset+entity.text.length()));
			}
			ArrayList<Segment> segments = TokenizerWithSegment.tokenize(sent.offset, sent.text, given);
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
			Tree root = tool.lp.apply(tokens);
			root.indexLeaves();
			root.setSpans();
			List<Tree> leaves = root.getLeaves();
			// depend
			GrammaticalStructure gs = tool.gsf.newGrammaticalStructure(root);
			List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
		    SemanticGraph depGraph = new SemanticGraph(tdl);
			
			sent.tokens = tokens;
			sent.root = root;
			sent.leaves = leaves;
			sent.depGraph = depGraph;
		}
		
		return mySentences;
	}
	
	public static void prepareFeatures(Tool tool, TObjectDoubleHashMap<String> map, Entity former, Entity latter, Sentence sent, 
			List<Entity> entities, int formerIndex, int latterIndex) throws Exception{
		
		String hdFormer = RelationGold.getHeadWordOfEntity(former, tool).lemma().toLowerCase();
		String hdLatter = RelationGold.getHeadWordOfEntity(latter, tool).lemma().toLowerCase();
		
		map.put("#EN1_"+sent.tokens.get(formerIndex).lemma().toLowerCase(),1.0); 
		map.put("#HM1"+hdFormer, 1.0);
		map.put("#EN2_"+sent.tokens.get(latterIndex).lemma().toLowerCase(),1.0); 
		map.put("#HM2"+hdLatter, 1.0);
		map.put("#HM12_"+hdFormer+"_"+hdLatter, 1.0);
		
		for(int i=formerIndex+1;i<=latterIndex-1;i++) {
			if(Punctuation.isEnglishPunc(sent.tokens.get(i).word().charAt(0)))
			{
				map.put("#RPPU_", 1.0);
				break;
			}
		}
		
		if(formerIndex+1==latterIndex)
			map.put("#WBNULL", 1.0);
		else if(formerIndex+2==latterIndex)
			map.put("#WBFL"+sent.tokens.get(formerIndex+1).lemma().toLowerCase(), 1.0);
		else {
			for(int i=formerIndex+1, count=2;count>0 && i<sent.tokens.size(); count--,i++) {
				map.put("#WB_"+sent.tokens.get(i).lemma().toLowerCase(),1.0); 
			}
			for(int i=latterIndex-1, count=2;count>0 && i>=0; count--,i--) {
				map.put("#WB_"+sent.tokens.get(i).lemma().toLowerCase(),1.0); 
			}
			
		}
		
		for(int i=formerIndex-1, count=2;count>0 && i>=0; count--,i--) {
			map.put("#BM1_"+sent.tokens.get(i).lemma().toLowerCase(),1.0); 
		}
		for(int i=latterIndex+1, count=2;count>0 && i<sent.tokens.size(); count--,i++) {
			map.put("#AM2_"+sent.tokens.get(i).lemma().toLowerCase(),1.0); 
		}
		
		map.put("#ET12_"+former.type+"_"+latter.type, 1.0);
		
		map.put("#WDBETNUM_",(latterIndex-formerIndex)*1.0/10);
		
		int rangeBegin = former.offset+former.text.length();
		int rangeEnd = latter.offset-1;
		int countEntity = 0;
		for(Entity temp:entities) {
			if(temp.offset>=rangeBegin && temp.offset+temp.text.length()-1<=rangeEnd)
				countEntity++;
			if(temp.offset>rangeEnd)
				break;
		}
		map.put("#ENBETNUM_",countEntity*1.0/10);
		
		{
			int len = hdFormer.length()>4 ? 4:hdFormer.length();
			map.put("#HD1PREF_"+hdFormer.substring(0, len),1.0);
			map.put("#HD1SUF_"+hdFormer.substring(hdFormer.length()-len, hdFormer.length()),1.0);
			POS[] poses = {POS.NOUN, POS.ADJECTIVE};
			for(POS pos:poses) {
				ISynset synset = WordNetUtil.getMostSynset(tool.dict, hdFormer, pos);
				if(synset!= null) {
					map.put("#HD1SYNS_"+synset.getID(),1.0);
				} 

				ISynset hypernym = WordNetUtil.getMostHypernym(tool.dict, hdFormer, pos);
				if(hypernym!= null) {
					map.put("#HD1HYPER_"+hypernym.getID(),1.0);
				}
				
			}
		}
		
		{
			int len = hdLatter.length()>4 ? 4:hdLatter.length();
			map.put("#HD2PREF_"+hdLatter.substring(0, len),1.0);
			map.put("#HD2SUF_"+hdLatter.substring(hdLatter.length()-len, hdLatter.length()),1.0);
			POS[] poses = {POS.NOUN, POS.ADJECTIVE};
			for(POS pos:poses) {
				ISynset synset = WordNetUtil.getMostSynset(tool.dict, hdLatter, pos);
				if(synset!= null) {
					map.put("#HD2SYNS_"+synset.getID(),1.0);
				} 

				ISynset hypernym = WordNetUtil.getMostHypernym(tool.dict, hdLatter, pos);
				if(hypernym!= null) {
					map.put("#HD2HYPER_"+hypernym.getID(),1.0);
				}
				
			}
		}
		
		/*
		 * Feature: the parent type of two mentions in the syntactic tree
		 * Feature: the path and type combinations of two nodes
		 */
		if(sent.root!=null) {
			Tree nodeFormer = sent.leaves.get(sent.tokens.get(formerIndex).index()-1);
			Tree nodeLatter = sent.leaves.get(sent.tokens.get(latterIndex).index()-1);
			Tree common = StanfordTree.getCommonAncestor(sent.root, nodeFormer, nodeLatter);
			map.put("#PAR_"+common.value(), 1.0);
			List<Tree> path = sent.root.pathNodeToNode(nodeFormer, nodeLatter);
			//ArrayList<Tree> phrasePath = new ArrayList<Tree>();
			ArrayList<Tree> phrasePathDeleteOverlap = new ArrayList<Tree>();
			String lastNodeValue = "";
			String featurePath =  "#CPP_";
			for(int k=0;k<path.size();k++) {
				Tree node  = path.get(k);
				if(node.isPhrasal()) {
					//phrasePath.add(node);
					if(!node.value().equals(lastNodeValue)) {
						phrasePathDeleteOverlap.add(node);
						featurePath += node.value();
					}
					lastNodeValue = node.value();
				}
			}
			if(phrasePathDeleteOverlap.size()==0)
				map.put("#CPHBNULL", 1.0);
			else if(phrasePathDeleteOverlap.size()==1)
				map.put("#CPHBFL_"+lastNodeValue, 1.0);
			else {
				map.put("#CPHBF_"+phrasePathDeleteOverlap.get(0).value(), 1.0);
				map.put("#CPHBL_"+phrasePathDeleteOverlap.get(phrasePathDeleteOverlap.size()-1).value(), 1.0);
				/*for(int kk=1;kk<phrasePath.size()-1;kk++) {
					map.put("#CPHBO_"+phrasePath.get(kk).value(), 1.0);
				}*/
				map.put(featurePath, 1.0);
			}
			
		}
		
		/*
		 * Feature : the word and its pos that two entities depend on
		 * Feature: the combination of entity type and governor word
		 */
		if(sent.depGraph!=null) {
			// there may be someone who isn't in the semantic graph
			IndexedWord nodeFormer  = sent.depGraph.getNodeByIndexSafe(sent.tokens.get(formerIndex).index());
			if(nodeFormer != null) {
				List<SemanticGraphEdge> edges = sent.depGraph.incomingEdgeList(nodeFormer);
				for(int i=0;i<edges.size();i++) {
					SemanticGraphEdge edge = edges.get(i);
					String gw = edge.getGovernor().lemma().toLowerCase().replaceAll(" ", "_");
					map.put("#EN1GW_"+gw, 1.0);
					map.put("#EN1GWPOS_"+edge.getGovernor().tag(), 1.0);
					map.put("#EN1TP_GW_"+former.type+"_"+gw, 1.0);
					map.put("#HD1_GW_"+hdFormer+"_"+gw, 1.0);
					
					/*String gwHead = getHeadWordOfEntity(new Entity(null, null, 0, edge.getGovernor().lemma().toLowerCase(), null),  tool);
					map.put("#EN1GWH_"+gwHead,1.0);
					map.put("#EN1TP_GWH_"+former.type+"_"+gwHead, 1.0);
					map.put("#HD1_GWH_"+hdFormer+"_"+gwHead, 1.0);*/
					
					break; // only take one dependent
				}
			}
			IndexedWord nodeLatter  = sent.depGraph.getNodeByIndexSafe(sent.tokens.get(latterIndex).index());
			if(nodeLatter != null) {
				List<SemanticGraphEdge> edges = sent.depGraph.incomingEdgeList(nodeLatter);
				for(int i=0;i<edges.size();i++) {
					SemanticGraphEdge edge = edges.get(i);
					String gw = edge.getGovernor().lemma().toLowerCase().replaceAll(" ", "_");
					map.put("#EN2GW_"+gw, 1.0);
					map.put("#EN2GWPOS_"+edge.getGovernor().tag(), 1.0);
					map.put("#EN2TP_GW_"+latter.type+"_"+gw, 1.0);
					map.put("#HD2_GW_"+hdLatter+"_"+gw, 1.0);
					
					/*String gwHead = getHeadWordOfEntity(new Entity(null, null, 0, edge.getGovernor().lemma().toLowerCase(), null),  tool);
					map.put("#EN2GWH_"+gwHead,1.0);
					map.put("#EN2TP_GWH_"+latter.type+"_"+gwHead, 1.0);
					map.put("#HD2_GWH_"+hdLatter+"_"+gwHead, 1.0);*/
					
					break; // only take one dependent
				}
			}
			
		}
			
		/*
		 *  Feature: whether the trigger words emerge between entity1 and entity2
		 *  There must be no other entities between entity1 and entity2.
		 */	
		/*if(countEntity == 0) {
			// get the tokens between former and latter
			int tokenIndexBegin = -1;
			int tokenIndexEnd = -1;
			for(int i=0;i<sent.tokens.size();i++) {
				if(sent.tokens.get(i).beginPosition()>=rangeBegin) {
					tokenIndexBegin = i;
					break;
				}
				
			}
			for(int i=sent.tokens.size()-1;i>=0;i--) {
				if(sent.tokens.get(i).beginPosition()<=rangeEnd ) {
					tokenIndexEnd = i;
					break;
				}
			}
			if(tokenIndexBegin != -1 && tokenIndexEnd != -1) // stanford tokenize problem such as Co. to Co..
			{
				// check
				for(int i=tokenIndexBegin;i<=tokenIndexEnd;i++) {
					CoreLabel token= sent.tokens.get(i);
					if(i+1<=tokenIndexEnd) { // current token has next
						CoreLabel nextToken = sent.tokens.get(i+1);
						if(FFR_TWBET.triggerTwo.contains((token.word().toLowerCase()+" "+nextToken.word()).toLowerCase())) { 
							// match phrase
							map.put("#TWBET",1.0);
							break;
						} else {
							if(FFR_TWBET.triggerOne.contains(token.lemma().toLowerCase())) {
								// match one word trigger
								map.put("#TWBET",1.0);
								break;
							}
						}
					} else {
						if(FFR_TWBET.triggerOne.contains(token.lemma().toLowerCase())) {
							// match one word trigger
							map.put("#TWBET",1.0);
							break;
						}
					}
				}
			}
			
		}*/
		
	}
	
	// Get the sentence which two entities are both in, if not, return null.
	public static Sentence getTwoEntitesSentence(List<Sentence> mySentences, Entity entity1, Entity entity2) {
		for(int i=0;i<mySentences.size();i++) {
			Sentence sent = mySentences.get(i);
			if((entity1.offset>=sent.offset && entity1.offset+entity1.text.length()<=sent.offset+sent.length) &&
				(entity2.offset>=sent.offset && entity2.offset+entity2.text.length()<=sent.offset+sent.length)) {
				return sent;
			}
		}
		return null;
	}
	
	// Get all the entities that belong to the sentence
	public static List<Entity> getEntitiesOfSentence(Sentence sent, List<Entity> entities) {
		List<Entity> ret = new ArrayList<Entity>();
		for(Entity temp:entities) {
			if(temp.offset>=sent.offset && temp.offset+temp.text.length()<=sent.offset+sent.length)
				ret.add(temp);
		}
		return ret;
	}
	// entity must have mesh
	public static boolean twoEntitiesHaveRelation(BiocDocument document, Entity entity1, Entity entity2) {
		Relation r = new Relation(null, "CID", entity1.mesh, entity2.mesh);
		if(document.relations.contains(r))
			return true;
		else 
			return false;
		
		/*for(Relation relation:document.relations) {
			if( (relation.mesh1.equals(entity1.mesh) && relation.mesh2.equals(entity2.mesh)) || 
			(relation.mesh1.equals(entity2.mesh) && relation.mesh2.equals(entity1.mesh)) ){
				return true;
			}
		}
		return false;*/
		
	}
	
	// Given a entity, we return its lemma of head word. Note that entities are tokenized by segments, so the words in the entity mention
	// has not been handled.
	public static CoreLabel getHeadWordOfEntity(Entity entity, Tool tool) {
		List<CoreLabel> tokens = tool.tokenizer.tokenize(0, entity.text);
		tool.tagger.tagCoreLabels(tokens);
		for(int i=0;i<tokens.size();i++) {	
			tool.morphology.stem(tokens.get(i));
		}
		if(tokens.size()==1)
			return tokens.get(0);
		else {
			int i=0;
			for(;i<tokens.size();i++){ // in order to find a prep
				if(EnglishPos.getType(tokens.get(i).tag()) == EnglishPos.Type.PREP)
					break;
			}
			if(i==tokens.size() || i==0) { // not find a prep or the first token is prep
				return tokens.get(tokens.size()-1);
			} else { // use the word before prep as head
				return tokens.get(i-1);
			}
			
		}
	}
	
	public static Classifier train(InstanceList trainingData, String ser) {
		ClassifierTrainer<MaxEnt> trainer = new MaxEntTrainer();
		Classifier classifier = trainer.train(trainingData);
		if(ser!=null)
			ObjectSerializer.writeObjectToFile(classifier, ser);
		return classifier;
	}
	
	public static void test(InstanceList data, String ser) {
		Classifier classifier = (Classifier)ObjectSerializer.readObjectFromFile(ser);
		Trial trial = new Trial(classifier, data);
		double precision = trial.getPrecision("1");
		double recall = trial.getRecall("1");
		double f1 = trial.getF1("1");
		System.out.println("The of p,r,f1 are "+precision+" "+recall+" "+f1); 
	}

}
