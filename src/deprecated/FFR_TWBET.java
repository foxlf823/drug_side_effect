package deprecated;

import java.util.Arrays;
import java.util.HashSet;

import cn.fox.machine_learning.Perceptron;
import cn.fox.machine_learning.PerceptronFeatureFunction;
import cn.fox.machine_learning.PerceptronInputData;
import cn.fox.machine_learning.PerceptronOutputData;
import cn.fox.machine_learning.PerceptronStatus;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.RelationEntity;
import edu.stanford.nlp.ling.CoreLabel;
import gnu.trove.TObjectDoubleHashMap;

public /*
 * Feature: whether the trigger words emerge between entity1 and entity2
 * There must be no other entities between entity1 and entity2.
 */
class FFR_TWBET extends PerceptronFeatureFunction {
	public static HashSet<String> triggerOne = new HashSet<String>(Arrays.asList("induce","associate","after","during", "follow", "related", 
			"cause", "develop"));
	public static HashSet<String> triggerTwo = new HashSet<String>(Arrays.asList("associated with","induced by","due to","caused by",
			"produced by")); 
	public FFR_TWBET(Perceptron perceptron) {
		super(perceptron);
	}
	@Override
	public void compute(PerceptronInputData x, PerceptronStatus status,
			PerceptronOutputData y, Object other,
			TObjectDoubleHashMap<String> map) {
		PerceptronInputData1 input = (PerceptronInputData1)x;
		PerceptronOutputData1 output = (PerceptronOutputData1)y;
		
		int lastSegmentIndex = output.getLastSegmentIndex(status.tokenIndex);
		Entity latter = output.segments.get(lastSegmentIndex);
		if(latter.type.equals(Perceptron.EMPTY))
			return;
		for(int index=0;index<lastSegmentIndex;index++) {
			Entity former = output.segments.get(index);
			if(former.type.equals(Perceptron.EMPTY))
				continue;
			String type = Perceptron.EMPTY;
			if(output.relations.contains(new RelationEntity("CID", former, latter)))
				type = "CID";
			
			int rangeBegin = former.offset+former.text.length();
			int rangeEnd = latter.offset-1;
			// if there are other entities between entity1 and entity2, we do not trigger this feature.
			boolean has = false;
			for(int i=0;i<output.segments.size();i++) {
				Entity temp = output.segments.get(i);
				if(temp.offset>=rangeBegin && temp.offset<=rangeEnd) {
					has = true;
					break;
				}
			}
			if(has) continue; 
			// get the tokens between former and latter
			int tokenIndexBegin = -1;
			int tokenIndexEnd = -1;
			for(int i=0;i<input.sentInfo.tokens.size();i++) {
				if(input.sentInfo.tokens.get(i).beginPosition()>=rangeBegin) {
					tokenIndexBegin = i;
					break;
				}
				
			}
			for(int i=input.sentInfo.tokens.size()-1;i>=0;i--) {
				if(input.sentInfo.tokens.get(i).beginPosition()<=rangeEnd ) {
					tokenIndexEnd = i;
					break;
				}
			}
			if(tokenIndexBegin == -1 || tokenIndexEnd == -1) // stanford tokenize problem such as Co. to Co..
				continue;
			// check
			for(int i=tokenIndexBegin;i<=tokenIndexEnd;i++) {
				CoreLabel token= input.sentInfo.tokens.get(i);
				if(i+1<=tokenIndexEnd) { // current token has next
					CoreLabel nextToken = input.sentInfo.tokens.get(i+1);
					if(triggerTwo.contains((token.word().toLowerCase()+" "+nextToken.word()).toLowerCase())) { 
						// match phrase
						addFeature("#WDBET_TRIGGER"+"_"+type, 1.0, status, y, map);
						break;
						//i++; // move one more step
					} else {
						if(triggerOne.contains(token.lemma().toLowerCase())) {
							// match one word trigger
							addFeature("#WDBET_TRIGGER"+"_"+type, 1.0, status, y, map);
							break;
						}
					}
				} else {
					if(triggerOne.contains(token.lemma().toLowerCase())) {
						// match one word trigger
						addFeature("#WDBET_TRIGGER"+"_"+type, 1.0, status, y, map);
						break;
					}
				}
			}
		}
		
			
	}
	
}
