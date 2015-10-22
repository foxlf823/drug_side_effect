package cdr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import cn.fox.math.Matrix;
import cn.fox.nlp.EnglishPos;
import cn.fox.nlp.Punctuation;
import cn.fox.nlp.WordVector;
import cn.fox.stanford.StanfordTree;
import cn.fox.utils.WordNetUtil;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.LexicalPattern;
import drug_side_effect_utils.RelationEntity;
import drug_side_effect_utils.Tool;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.Tree;
import gnu.trove.TObjectDoubleHashMap;



public class RelationFeatures extends PerceptronFeatureFunction {
	public RelationFeatures(Perceptron perceptron) {
		super(perceptron);
	}
	
	@Override
	public void compute(PerceptronInputData x, PerceptronStatus status,
			PerceptronOutputData y, Object other,
			TObjectDoubleHashMap<String> map, ArrayList<PerceptronInputData> preInputs, ArrayList<PerceptronOutputData> preOutputs) {
		PerceptronInputData input = x;
		PerceptronOutputData output = y;
		Tool tool = (Tool)other;
		
		int lastSegmentIndex = output.getLastSegmentIndex(status.tokenIndex);
		Entity latter = output.segments.get(lastSegmentIndex);
		
				
		for(RelationEntity re : output.relations) {
			Entity former = null;
			// only generate features according to the last segment
			if(re.entity1.equals(latter)) {
				former = re.entity2;
			} else if(re.entity2.equals(latter)) {
				former = re.entity1;
			}
			if(former ==null) 
				continue;
			// only consider the relation with both entities before the last segment
			if(former.sentIdx == input.sentIdx && former.end>status.tokenIndex)
				continue;
			
			// word
			addFeature(former.text.toLowerCase(), 1.0, status, y, map);
			addFeature(latter.text.toLowerCase(), 1.0, status, y, map);
			
			// pattern
			addFeature("#PTN_"+LexicalPattern.pipe(former.text), 1.0, status, y, map);
			addFeature("#PTN_"+LexicalPattern.pipe(latter.text), 1.0, status, y, map);
			
			// head word
			PerceptronInputData inputFormer = null;
			int idxInWindow = -1; // only used when former in previous sentence
			PerceptronOutputData outputFormer = null;
			if(former.sentIdx<input.sentIdx) { 
				idxInWindow = preInputs.size()-(input.sentIdx-former.sentIdx);
				inputFormer = preInputs.get(idxInWindow);
				outputFormer = preOutputs.get(idxInWindow);
			} else {
				inputFormer = input;
			}
			CoreLabel clHdFormer = getHeadWordOfSegment(former, inputFormer);
			CoreLabel clHdLatter = getHeadWordOfSegment(latter, input);
			String hdFormer = clHdFormer.lemma().toLowerCase();
			String hdLatter = clHdLatter.lemma().toLowerCase();
			
			addFeature(hdFormer, 1.0, status, y, map);
			addFeature(hdLatter, 1.0, status, y, map);
			
			addFeature("#PTN_"+LexicalPattern.pipe(clHdFormer.word()), 1.0, status, y, map);
			addFeature("#PTN_"+LexicalPattern.pipe(clHdLatter.word()), 1.0, status, y, map);
			
			addFeature("#HMPOS_"+clHdFormer.tag(), 1.0, status, y, map);
			addFeature("#HMPOS_"+clHdLatter.tag(), 1.0, status, y, map);
			
			int len1 = hdFormer.length()>4 ? 4:hdFormer.length();
			int len2 = hdLatter.length()>4 ? 4:hdLatter.length();
			String hd1Pref = hdFormer.substring(0, len1);
			String hd1Suf = hdFormer.substring(hdFormer.length()-len1, hdFormer.length());
			String hd2Pref = hdLatter.substring(0, len2);
			String hd2Suf = hdLatter.substring(hdLatter.length()-len2, hdLatter.length());
			
			addFeature("#PSF_"+hd1Pref,1.0, status, y, map);
			addFeature("#PSF_"+hd2Pref,1.0, status, y, map);
			addFeature("#PSF_"+hd1Suf,1.0, status, y, map);
			addFeature("#PSF_"+hd2Suf,1.0, status, y, map);
			
			// external
			String bcHdFormer = tool.entityBC.getPrefix(hdFormer);
			String bcHdLatter = tool.entityBC.getPrefix(hdLatter);
			addFeature("#HMBC_"+bcHdFormer, 1.0, status, y, map);
			addFeature("#HMBC_"+bcHdLatter, 1.0, status, y, map);
			
			addFeature("#WC_"+tool.wcr.getCluster(hdFormer), 1.0, status, y, map);
			addFeature("#WC_"+tool.wcr.getCluster(hdLatter), 1.0, status, y, map);
			
			

			POS[] poses = {POS.NOUN, POS.ADJECTIVE};
			for(POS pos:poses) {
				ISynset synset = WordNetUtil.getMostSynset(tool.dict, hdFormer, pos);
				if(synset!= null) {
					addFeature("#WN_"+synset.getID(),1.0, status, y, map);
				} 

				ISynset hypernym = WordNetUtil.getMostHypernym(tool.dict, hdFormer, pos);
				if(hypernym!= null) {
					addFeature("#WN_"+hypernym.getID(),1.0, status, y, map);
				}
				
			}
		
			for(POS pos:poses) {
				ISynset synset = WordNetUtil.getMostSynset(tool.dict, hdLatter, pos);
				if(synset!= null) {
					addFeature("#WN_"+synset.getID(),1.0, status, y, map);
				} 

				ISynset hypernym = WordNetUtil.getMostHypernym(tool.dict, hdLatter, pos);
				if(hypernym!= null) {
					addFeature("#WN_"+hypernym.getID(),1.0, status, y, map);
				}
				
			}	
			
			
			// context
			int countToken = 0;
			int countDisease = 0;
			int countChemical = 0;
			if(former.sentIdx<input.sentIdx) { // former and latter in different sentences
								
				// add the words from former to latter
				for(int i=former.end+1;i<inputFormer.tokens.size();i++) {
					CoreLabel token = inputFormer.sentInfo.tokens.get(i);
					String lemmaLow = token.lemma().toLowerCase();
					EnglishPos.Type posType = EnglishPos.getType(token.tag());
					if(posType==EnglishPos.Type.VERB || posType==EnglishPos.Type.ADJ)
						addFeature(lemmaLow,1.0, status, y, map); 
					
										
					countToken++;
				}
				for(int i=idxInWindow+1;i<preInputs.size();i++) {
					for(int j=0;j<preInputs.get(i).tokens.size();j++) {
						CoreLabel token = preInputs.get(i).sentInfo.tokens.get(j);
						String lemmaLow = token.lemma().toLowerCase();
						EnglishPos.Type posType = EnglishPos.getType(token.tag());
						if(posType==EnglishPos.Type.VERB || posType==EnglishPos.Type.ADJ)
							addFeature(lemmaLow,1.0, status, y, map); 
						
						
					}
					
					countToken++;
				}
				for(int i=0;i<latter.start;i++) {
					CoreLabel token = input.sentInfo.tokens.get(i);
					String lemmaLow = token.lemma().toLowerCase();
					EnglishPos.Type posType = EnglishPos.getType(token.tag());
					if(posType==EnglishPos.Type.VERB || posType==EnglishPos.Type.ADJ)
						addFeature(lemmaLow,1.0, status, y, map); 
					
					
					
					countToken++;
				}
				

				
			} else { // former and latter in the same sentences
				
				// add the words from former to latter
				for(int i=former.end+1;i<latter.start;i++) {
					CoreLabel token = input.sentInfo.tokens.get(i);
					String lemmaLow = token.lemma().toLowerCase();
					EnglishPos.Type posType = EnglishPos.getType(token.tag());
					if(posType==EnglishPos.Type.VERB || posType==EnglishPos.Type.ADJ)
						addFeature(lemmaLow,1.0, status, y, map); 
					
										
					countToken++;
				}
				
				
			}
			

			
			
				
			
			
			// global features
			if(preInputs.size()>0) {
				// conj
				for(CoreLabel token:input.sentInfo.tokens) {
					if(token.tag().equals("CC"))
						addFeature(token.lemma().toLowerCase(),1.0, status, y, map);
				}
				for(CoreLabel token:inputFormer.sentInfo.tokens) {
					if(token.tag().equals("CC"))
						addFeature(token.lemma().toLowerCase(),1.0, status, y, map);
				}
				
				
			}
			
			
		
		} // for(RelationEntity re : output.relations)
		
		
			
			
		
	}
	
	// find the main verb
	public static Label getMainVerb(Tree tree) {
		LinkedList<Tree> queue = new LinkedList<Tree>();
		queue.addLast(tree);
		Tree vp = null;
		// find the leftmost and uppermost vp
OUT:	while(!queue.isEmpty())  {
			Tree temp = queue.removeFirst();
			Tree[] tc = temp.children();
			for(Tree child:tc) {
				if(child.isPhrasal()) {
					if(child.value().equals("VP")) {
						vp = child;
						break OUT;
					} else
						queue.addLast(child);
				} 
			}
		}
		// find the leftmost verb which belongs to that vp
		if(vp!=null) {
			queue.clear();
			queue.addLast(vp);
			while(!queue.isEmpty()) {
				Tree temp = queue.removeFirst();
				Tree[] tc = temp.children();
				for(Tree child:tc) {
					if(child.isPreTerminal() && EnglishPos.getType(child.value()) == EnglishPos.Type.VERB)
						return child.children()[0].label();
					else if(child.isPhrasal())
						queue.addLast(child);
				}
			}
			
		}
		
		return null;
	}
	
	public static CoreLabel getHeadWordOfSegment(Entity segment, PerceptronInputData input) {
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
	
	public static Tree getSegmentTreeNode(Entity segment, PerceptronInputData input) {
		
		List<Tree> nodes = input.sentInfo.root.getLeaves();
		if(segment.start==segment.end)  // return the leaf node
			return nodes.get(input.sentInfo.tokens.get(segment.start).index()-1);
		else { // return the phrasal node
			Tree nodeFormer = nodes.get(input.sentInfo.tokens.get(segment.start).index()-1);
			Tree nodeLatter = nodes.get(input.sentInfo.tokens.get(segment.end).index()-1);
			Tree common = StanfordTree.getCommonAncestor(input.sentInfo.root, nodeFormer, nodeLatter);
			return common; 
		}
			
	}
	
	public static HashSet<String> triggerWords = new HashSet<>(Arrays.asList("induce", "associate", "cause", "during",
			"follow", "relate", "develop", "produce", "after", "receive", "treat", "treatment"));
}
