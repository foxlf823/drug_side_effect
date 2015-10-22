package cdr;

import gnu.trove.TObjectDoubleHashMap;

import java.io.Serializable;
import java.util.ArrayList;


public abstract class PerceptronFeatureFunction implements Serializable{

	private static final long serialVersionUID = 1869086926811075989L;
	public Perceptron perceptron;
	
	public PerceptronFeatureFunction(Perceptron perceptron) {
		this.perceptron = perceptron;
	}
	
	/*
	 * x: the current input data.
	 * status: the current status of perceptron.
	 * y: the current predicted answer, and it may be a partial answer. 
	 *    Moreover, it can be the gold answer as well.
	 * other: any information or tools which are used in the feature functions.
	 */
	public void compute(PerceptronInputData x, PerceptronStatus status, PerceptronOutputData y, Object other, 
			TObjectDoubleHashMap<String> map, ArrayList<PerceptronInputData> preInputs, ArrayList<PerceptronOutputData> preOutputs) {
		return ;
	}
	
	/*
	 * 	  value: Theoretically, it can be any real number. However, we should try to keep it between 0 and 1 for the convergence speed.
	 *	  And the return value of the gold answer can be any value because every weight updating just want the predicted answer which
	 *    is close to the gold answer, to win in the next scoring.
	 */
	public void addFeature(String name, double value, PerceptronStatus status, PerceptronOutputData y,
			TObjectDoubleHashMap<String> map) {
		/*if(perceptron.isAlphabetStop==false && !perceptron.featureAlphabet.containsKey(name)) {
			perceptron.featureAlphabet.put(name, perceptron.featureAlphabet.size());
		}*/

		if(status.step == 0 ) { // see buildFeatureAlphabet
			if(!perceptron.featureAlphabet.containsKey(name)) {

				perceptron.featureAlphabet.put(name, perceptron.featureAlphabet.size());
				
			}
			map.put(name, value);
						
			return ;
		}
		
		// if the feature name is not in featureAlphabet, it will be ignored.
		if(perceptron.featureAlphabet.containsKey(name)) {
			map.put(name, value);
		}
		

		return ;
	}
}
