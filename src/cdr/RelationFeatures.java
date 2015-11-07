package cdr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import cn.fox.math.Matrix;
import cn.fox.nlp.EnglishPos;
import cn.fox.nlp.Punctuation;
import cn.fox.nlp.Sentence;
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
			TObjectDoubleHashMap<String> map, ArrayList<PerceptronInputData> preInputs, ArrayList<PerceptronOutputData> preOutputs,
			PerceptronOutputData gold) {
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
			
			Entity chemical = re.getChemical();
			Entity disease = re.getDisease();
			
			addFeature("CM_"+chemical.text.toLowerCase(), 1, status, y, map);
			addFeature("DS_"+disease.text.toLowerCase(), 1, status, y, map);
			addFeature("MS1_"+chemical.mesh, 1, status, y, map);
			addFeature("MS2_"+disease.mesh, 1, status, y, map);
			

			if(former.sentIdx<input.sentIdx) { 
						
				for(int idxInWindow = preInputs.size()-(input.sentIdx-former.sentIdx);
						idxInWindow<preInputs.size();idxInWindow++) {
					PerceptronInputData preInput = preInputs.get(idxInWindow);
					PerceptronOutputData preOutput = preOutputs.get(idxInWindow);
					Sentence sentence = preInput.sentInfo;
					for(int i=0;i<sentence.tokens.size();i++) {
						CoreLabel token = sentence.tokens.get(i);
						tokenFeatureTemplate(token, former, latter, gold, status, y, map, this);
					}
				}
				
				Sentence sentence = input.sentInfo;
				for(int i=0;i<sentence.tokens.size();i++) {
					CoreLabel token = sentence.tokens.get(i);
					tokenFeatureTemplate(token, former, latter, gold, status, y, map, this);
				}
			} else {
				Sentence sentence = input.sentInfo;
				for(int i=0;i<sentence.tokens.size();i++) {
					CoreLabel token = sentence.tokens.get(i);
					tokenFeatureTemplate(token, former, latter, gold, status, y, map, this);
				}
			}
			
			if(tool.sider.contains(chemical.text, disease.text)) {
				addFeature("SD1_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1, status, y, map);
			} else {
				addFeature("SD2_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1, status, y, map);
			}
			
			if(tool.ctdParse.containRelation(chemical.text, disease.text)) {
				addFeature("CTD1_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1, status, y, map);
			} else {
				addFeature("CTD2_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1, status, y, map);
			}
			
			if(tool.medi.contains(chemical.text, disease.text)) {
				addFeature("MEDI1_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1, status, y, map);
			} else {
				addFeature("MEDI2_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1, status, y, map);
			}
			
						
						
			
		} // for(RelationEntity re : output.relations)
		
		
			
			
		
	}
	
	public static void tokenFeatureTemplate(CoreLabel token, Entity former, Entity latter, PerceptronOutputData gold
			, PerceptronStatus status,PerceptronOutputData y, TObjectDoubleHashMap<String> map, 
			RelationFeatures featureFunction) {
		
			
		
		if(isATokenInsideAnEntity(token, former) || isATokenInsideAnEntity(token, latter)) {
			// inside former or latter
		} else {
			Entity otherEntity = isInsideAGoldEntityAndReturnIt(gold, token.beginPosition(), token.endPosition()-1);
			if(otherEntity != null) {
				// inside an entity except former or latter
				featureFunction.addFeature(otherEntity.type, 1, status, y, map);
			} else {
				if(token.beginPosition()<former.offset) {
					// words before former
					featureFunction.addFeature("WB_"+token.lemma(), 1, status, y, map);
				} else if(token.beginPosition()>=former.offset+former.text.length() &&
						token.endPosition()<=latter.offset) {
					// words inbetween
					featureFunction.addFeature("WIB_"+token.lemma(), 1, status, y, map);
				} else {
					// words after latter
					featureFunction.addFeature("WA_"+token.lemma(), 1, status, y, map);
				}
			}
		}
	}
	
	public static Entity isInsideAGoldEntityAndReturnIt(PerceptronOutputData output, int start, int end) {
		for(Entity temp:output.segments) {
			if(temp.type.equals(Perceptron.EMPTY))
				continue;
			if(start >= temp.offset && end <= (temp.offset+temp.text.length()-1))
			{
				return temp;
			}
		}
		return null;
	}
	
	public static boolean isATokenInsideAnEntity(CoreLabel token, Entity entity) {
		if(token.beginPosition()>=entity.offset && token.endPosition()<=entity.offset+entity.text.length())
			return true;
		else
			return false;
	}
	
	public static HashSet<String> triggerWords = new HashSet<>(Arrays.asList("induce", "associate", "cause", "during",
			"follow", "relate", "develop", "produce", "after", "receive", "treat", "treatment"));
}
