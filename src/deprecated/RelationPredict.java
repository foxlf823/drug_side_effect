package deprecated;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.mallet.classify.Classifier;
import cc.mallet.fst.CRF;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.FileIterator;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cn.fox.biomedical.Dictionary;
import cn.fox.biomedical.Sider;
import cn.fox.mallet.MalletClassifierInstance;
import cn.fox.nlp.Segment;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.utils.CharCode;
import cn.fox.utils.Evaluater;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import cn.fox.utils.ObjectShuffle;
import deprecated.DrugDiseaseDetect.Label;
import cn.fox.mallet.MyCRFPipe;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
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

public class RelationPredict {

	public static void main(String[] args) throws Exception {
		FileInputStream fis = new FileInputStream(args[1]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String pos_tagger = properties.getProperty("pos_tagger");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String bioc_documents = properties.getProperty("bioc_documents");
		String relation_classifier_ser = properties.getProperty("relation_classifier_ser");
		String parser = properties.getProperty("parser");
		String sider_dict = properties.getProperty("sider_dict");
		String entity_recognizer_ser = properties.getProperty("entity_recognizer_ser");
		String instance_dir = properties.getProperty("instance_dir");
		//String mesh_dict = properties.getProperty("mesh_dict");
		String jochem_dict = properties.getProperty("jochem_dict");
		String ctdchem_dict = properties.getProperty("ctdchem_dict");
		String ctdmedic_dict = properties.getProperty("ctdmedic_dict");
		String chemical_element_abbr = properties.getProperty("chemical_element_abbr");
		String drug_dict = properties.getProperty("drug_dict");
		String disease_dict = properties.getProperty("disease_dict");
		String max_train_times = properties.getProperty("max_train_times");
		
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
		Dictionary jochem = new Dictionary(jochem_dict, 1);
		Dictionary ctdchem = new Dictionary(ctdchem_dict, 1);
		Dictionary ctdmedic = new Dictionary(ctdmedic_dict, 1);
		Dictionary chemElem = new Dictionary(chemical_element_abbr, 1);
		Dictionary drugbank = new Dictionary(drug_dict, 1);
		Dictionary humando = new Dictionary(disease_dict, 1);

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
		
	
		//ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D://biocreative//2015//cdr_sample//CDR_sample.xml");
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile(bioc_documents);
		
		// If the crf and classifier have been trained, we don't need to preprocess again. 
		// Just copy the instances to the "instance_dir".
		/*File fInstanceDir = new File(instance_dir);
		IoUtils.clearDirectory(fInstanceDir);
		for(int j=0;j<documents.size();j++) {
			BiocDocument document = documents.get(j);
			DrugDiseaseDetect.preprocess(tool, instance_dir, document);
		}
		for(int j=0;j<documents.size();j++) {
			BiocDocument document = documents.get(j);
			RelationGold.preprocess(tool, document, instance_dir, document.entities);
		}*/
		
		
		nValidate(10, documents, tool, instance_dir, Integer.parseInt(max_train_times), entity_recognizer_ser, relation_classifier_ser);

	}
	
	public static void nValidate(int nFold, ArrayList<BiocDocument> documents, Tool tool, String instance_dir
			, int max_train_times, String entity_recognizer_ser, String relation_classifier_ser) throws Exception {
		double testPercent = 1.0/nFold;
		double trainPercent = 1-testPercent;	
		
		double sumPrecisionMesh = 0;
		double sumRecallMesh = 0;
		double sumF1Mesh = 0;

		for(int i=0;i<nFold;i++) { // for each fold
			// split the data
			List[] splitted = null;
			if(nFold!=1)
				splitted = ObjectShuffle.split(documents, new double[] {trainPercent, testPercent});
			else {
				splitted = new ArrayList[2];
				splitted[0] = new ArrayList<>(documents);
				splitted[1] = new ArrayList<>(documents);
			}
			
			CRF crf = null;
			Classifier classifier = null;
			// train	
			boolean train = true;
			if(train) {
				Pipe crfPipe = new MyCRFPipe();
				InstanceList crfTrainData = new InstanceList(crfPipe);
				for(int j=0;j<splitted[0].size();j++) {
					BiocDocument document = (BiocDocument)splitted[0].get(j);
					crfTrainData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instance_dir+"/"+document.id+".instance"), "UTF-8"),Pattern.compile("^\\s*$"), true));
				}
				
				crf = DrugDiseaseDetect.train(crfTrainData, null, max_train_times);
				
				RiSer2FvPipe mePipe = new RiSer2FvPipe();
				InstanceList meTrainData = new InstanceList (mePipe);
				for(int j=0;j<splitted[0].size();j++) {
					BiocDocument document = (BiocDocument)splitted[0].get(j);
					meTrainData.addThruPipe(new FileIterator(instance_dir+"/"+document.id));
				}
				classifier = RelationGold.train(meTrainData, null);
				
			} else {
				crf = (CRF)ObjectSerializer.readObjectFromFile(entity_recognizer_ser);
				classifier = (Classifier)ObjectSerializer.readObjectFromFile(relation_classifier_ser);
			}
			
			int countPredictMesh = 0;
			int countTrueMesh = 0;
			int countCorrectMesh = 0;
			// Because the training instances are made using the gold entities.
			// After training, we need to rebuild instances using predicted entities to this temp directory.
			File tempDir = new File(instance_dir+"/temp");
			if(!tempDir.exists())
				tempDir.mkdir();
			else {
				IoUtils.clearDirectory(tempDir);
			}
			// test
			for(int j=0;j<splitted[1].size();j++) { // for each test file
				BiocDocument document = (BiocDocument)splitted[1].get(j);
				// read instance
				InstanceList testData = new InstanceList(crf.getInputPipe());
				testData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instance_dir+"/"+document.id+".instance"), "UTF-8"),Pattern.compile("^\\s*$"), true));
				
				ArrayList<Entity> preEntities = DrugDiseaseDetect.getEntities(document, testData, crf, tool);

				DrugDiseaseDetect.postprocessGlobal(tool, preEntities);
				
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
				for(Entity delete:toBeDeleted) {
					preEntities.remove(delete);
				}
				
				// prepare instance
				String relationInstanceDir = RelationGold.preprocess(tool, document, tempDir.getAbsolutePath(), preEntities);
				File fRelationInstanceDir = new File(relationInstanceDir);
				ArrayList<RelationEntity> preRelationEntitys = RelationGold.predictRelationEntity(fRelationInstanceDir, classifier, tool);
				
				RelationGold.postprocessGlobal(tool, preRelationEntitys);
				
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
			
			
		}
		System.out.println("The macro average of Mesh p,r,f1 are "+sumPrecisionMesh/nFold+" "+sumRecallMesh/nFold+" "+sumF1Mesh/nFold); 
	}
	
	
	// generate the inner-sentence RelationEntity with gold relations and entities
	/*public static List<RelationEntity> generateRelationEntity(BiocDocument document, Tool tool, List<Entity> inputEntities) {
		List<RelationEntity> ret = new ArrayList<RelationEntity>();
		// prepare sentence information
		String content = document.title+" "+document.abstractt;
		//content = content.replaceAll("-", " ");
		int offset = 0;
		List<Sentence> mySentences = new ArrayList<Sentence>();
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
			// for each sentence, get all the entities in it.
			List<Entity> entitiesInSent = RelationGold.getEntitiesOfSentence(sent, inputEntities);
			for(int i=0;i<entitiesInSent.size();i++) {
				for(int j=0;j<i;j++) {
					Entity entity1 = entitiesInSent.get(i);
					Entity entity2 = entitiesInSent.get(j);
					if(RelationGold.twoEntitiesHaveRelation(document, entity1, entity2)) {
						// build a RelationEntity
						RelationEntity re = new RelationEntity("CID", entity1, entity2);
						ret.add(re);
					}
				}
			}
			
		}
		
		return ret;
	}*/
	
}
