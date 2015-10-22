package cdr;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import cn.fox.math.Normalizer;
import cc.mallet.types.SparseVector;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.RelationEntity;

public class Perceptron implements Serializable{

	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7077106935494560143L;
	
	public static final String EMPTY = "#N#"; // empty type
	protected SparseVector w2; 
	public ArrayList<PerceptronFeatureFunction> featureFunctions2; 
	public ArrayList<String> alphabet2;
	
	public TObjectIntHashMap<String> featureAlphabet; // store the feature name and its index in the feature vector
	public boolean isAlphabetStop; // when training finished, set this true
	
	public float convergeThreshold;
	public double weightMax;
	
	public double learningRate = 1;
	
	public int window;
	
	public boolean average;

	public void setWindowSize(int window) {
		this.window = window;
	}
	
	public void buildFeatureAlphabet(ArrayList<PerceptronInputData> inputDatas, ArrayList<PerceptronOutputData> outputDatas, Object other) {
		try {
			w2 = new SparseVector();
			
			// use gold data and feature functions to build alphabet and gold feature vectors
			for(int i=0;i<outputDatas.size();i++)  {
				// prepare the window, we assume the data is ordered
				ArrayList<PerceptronInputData> preInputs = new ArrayList<PerceptronInputData>();
				ArrayList<PerceptronOutputData> preOutputs = new ArrayList<PerceptronOutputData>();
				for(int k=0;k<window;k++) {
					int index = i-window+k;
					if(index<0) continue;
					// if the previous sentence and current sentence are not in the same document, ignore it
					if(!outputDatas.get(index).id.equals(outputDatas.get(i).id))
						continue;
					
					preInputs.add(inputDatas.get(index));
					preOutputs.add(outputDatas.get(index));
				}
				for(int j=0;j<outputDatas.get(i).segments.size();j++) { 
					// beamsearch only returns at the end of entity and the last segment
					Entity segment = outputDatas.get(i).segments.get(j);
					if(segment.type.equals(EMPTY) && j!=outputDatas.get(i).segments.size()-1)
						continue;
					PerceptronStatus status = new PerceptronStatus(null, segment.end, 0); // we define this step is 0.
					FReturnType ret = f(inputDatas.get(i), status, outputDatas.get(i), other, preInputs, preOutputs);
				}
			}
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	// windowInput and windowOutput denote the window
	public PerceptronStatus beamSearch(PerceptronInputData x,
			PerceptronOutputData y, boolean isTrain, int beamSize, Object other,
			ArrayList<PerceptronInputData> preInputs, ArrayList<PerceptronOutputData> preOutputs)
			throws Exception {
		Random rnd = new Random(System.currentTimeMillis());
		// create just one beam
		ArrayList<PerceptronOutputData> beam = new ArrayList<PerceptronOutputData>();
		// create a prediction which has the same entities with y but has no relations
		PerceptronOutputData p1 = new PerceptronOutputData(false, x.id, y.sentIdx);
		for(int i=0;i<y.segments.size();i++)
			p1.segments.add(y.segments.get(i));
		
		ArrayList<PerceptronOutputData> buf = new ArrayList<PerceptronOutputData>();
		
		beam.add(p1);
		// for each entity in the current sentence
		for(int i=0;i<y.segments.size();i++) {
			Entity segmentCurrent = y.segments.get(i);
			if(segmentCurrent.type.equals(EMPTY))
				continue;
			
			PerceptronStatus statusKBest1 = new PerceptronStatus(null, segmentCurrent.end, 2);
			
			// predict the relations between entities in the current sentence
			// for avoiding the overlapped relation, e.g., AB vs BA, we consider the entity before the current
			for(int j=0;j<y.segments.size();j++) {
				if(j>=i) break;
				Entity segmentPre = y.segments.get(j);
				if(segmentPre.type.equals(EMPTY))
					continue;
				// if two entities have the same text, ignore
				if(segmentCurrent.text.toLowerCase().equals(segmentPre.text.toLowerCase()))
					continue;
				// if two entities type not match, ignore
				if((segmentPre.type.equals("Chemical") && segmentCurrent.type.equals("Disease")) ||
						(segmentPre.type.equals("Disease") && segmentCurrent.type.equals("Chemical")))
				{
					;
				} else {
					continue;
				}
				
				
				buf.clear();
				
				// try all the possible prediction
				for(int yy=0;yy<beam.size();yy++) {
					PerceptronOutputData yInBeam = beam.get(yy);
					buf.add(yInBeam);
					for(int r=0;r<alphabet2.size();r++) {
						PerceptronOutputData ret = new PerceptronOutputData(false, y.id, y.sentIdx);
						for(int m=0;m<yInBeam.segments.size();m++) {
							ret.segments.add(yInBeam.segments.get(m));
						}
						for(RelationEntity relation:yInBeam.relations) {
							ret.relations.add(relation);
						}
						RelationEntity relation = new RelationEntity(alphabet2.get(r), segmentPre, segmentCurrent);
						ret.relations.add(relation);
						buf.add(ret);
					}
				}
				
				
				kBest(x, statusKBest1, beam, buf, beamSize, other, preInputs, preOutputs);
				
			}

			// rule 1: if an entity occurred in an inner-sentence relation, it may not occur in the inter-sentence
			if(preOutputs.size()>0) {
		    
				/*int countNumber = 0;
				for(int yy=0;yy<beam.size();yy++) { // !!! this is only right when beam==1
					PerceptronOutputData yInBeam = beam.get(yy);
					for(RelationEntity re: yInBeam.relations) {
						if(segmentCurrent.equals(re.entity1) || segmentCurrent.equals(re.entity2)) {
							countNumber++;
						}
					}
				}*/
				// use sigmoid probability
				/*double prob = 1-Normalizer.doSigmoid(countNumber); 
				double randomNumber = rnd.nextDouble();
				if(randomNumber < prob) {
					
				} else { 
					continue;
				}*/
				
				// use fixed probability
				/*if(countNumber>0) 
					continue;*/
				
				// use 1/(1+x) probability
				/*double prob = 1.0/(countNumber+1); 
				double randomNumber = rnd.nextDouble();
				if(randomNumber < prob) {
					
				} else { 
					continue;
				}*/
				
				
			
			}
			

			// predict the relation between entities of the current sentence and previous sentences
			for(int w=preOutputs.size()-1;w>=0;w--) {
				
				PerceptronOutputData preOutput = preOutputs.get(w);
				
				for(int j=preOutput.segments.size()-1;j>=0;j--) {
					Entity segmentPre = preOutput.segments.get(j);
					if(segmentPre.type.equals(EMPTY))
						continue;
					// if two entities have the same text, ignore
					if(segmentCurrent.text.toLowerCase().equals(segmentPre.text.toLowerCase()))
						continue;
					// if two entities type not match, ignore
					if((segmentPre.type.equals("Chemical") && segmentCurrent.type.equals("Disease")) ||
							(segmentPre.type.equals("Disease") && segmentCurrent.type.equals("Chemical")))
					{
						;
					} else {
						continue;
					}
					

					
					buf.clear();
					
					// try all the possible prediction
					for(int yy=0;yy<beam.size();yy++) {
						PerceptronOutputData yInBeam = beam.get(yy);
						buf.add(yInBeam);
						for(int r=0;r<alphabet2.size();r++) {
							PerceptronOutputData ret = new PerceptronOutputData(false, y.id, y.sentIdx);
							for(int m=0;m<yInBeam.segments.size();m++) {
								ret.segments.add(yInBeam.segments.get(m));
							}
							for(RelationEntity relation:yInBeam.relations) {
								ret.relations.add(relation);
							}
							RelationEntity relation = new RelationEntity(alphabet2.get(r), segmentPre, segmentCurrent);
							ret.relations.add(relation);
							buf.add(ret);
						}
					}
					
					
					kBest(x, statusKBest1, beam, buf, beamSize, other, preInputs, preOutputs);
					
				}
				
				
			}
			
			
			

			// After we generate all the predict about segmentCurrent, early update
			if(isTrain) {
				int m=0;
				for(;m<beam.size();m++) {
					if(beam.get(m).isIdenticalWith(x, y, statusKBest1)) {
						break;
					}
				}
				if(m==beam.size()) {
					PerceptronStatus returnType = new PerceptronStatus(beam.get(0), segmentCurrent.end, 2);
					return returnType;
				}
			}
			
		}
		
		PerceptronStatus returnType = new PerceptronStatus(beam.get(0), x.tokens.size()-1, 3);
		return returnType;
		
	}
	
		
	public Perceptron(ArrayList<String> alphabet2, float convergeThreshold, double weightMax) {
		
		if(alphabet2!=null) {
			this.alphabet2 = new ArrayList<String>();
			for(int i=0;i<alphabet2.size();i++) {
				this.alphabet2.add(alphabet2.get(i));
			}
		}
		
		this.featureAlphabet = new TObjectIntHashMap<>();
		
		this.convergeThreshold = convergeThreshold;
		this.weightMax = weightMax;
		
	}
	
	
	public void setFeatureFunction(ArrayList<PerceptronFeatureFunction> featureFunctions1, ArrayList<PerceptronFeatureFunction> featureFunctions2) {
		this.featureFunctions2 = featureFunctions2;
	}
	
	public SparseVector getW2() {
		return w2;
	}
	
	// if average is true, we use averaged parameter instead of normalization
	public void normalizeWeight(int T, int n) {
		if(average) {
			double regularization = T*n;
			for(int j=0;j<w2.getIndices().length;j++) {
				w2.setValueAtLocation(j, w2.valueAtLocation(j)/regularization);
			}
		} else {
			double norm2 = w2.twoNorm();
			for(int j=0;j<w2.getIndices().length;j++) {
				w2.setValueAtLocation(j, w2.valueAtLocation(j)/norm2);
			}
		}
	}
	
	public void trainOnce(int beamSize, ArrayList<PerceptronInputData> input, ArrayList<PerceptronOutputData> output, Object other) {
		try {		

			long startTime = System.currentTimeMillis();
			for(int j=0;j<input.size();j++) {
				PerceptronInputData x = input.get(j);
				PerceptronOutputData y = output.get(j);
				// prepare the window, we assume the data is ordered
				ArrayList<PerceptronInputData> preInputs = new ArrayList<PerceptronInputData>();
				ArrayList<PerceptronOutputData> preOutputs = new ArrayList<PerceptronOutputData>();
				for(int k=0;k<window;k++) {
					int index = j-window+k;
					if(index<0) continue;
					// if the previous sentence and current sentence are not in the same document, ignore it
					if(!input.get(index).id.equals(x.id))
						continue;
					
					preInputs.add(input.get(index));
					preOutputs.add(output.get(index));
				}
				
				
				// get the best predicted answer
				PerceptronStatus status = beamSearch(x, y, true, beamSize, other, preInputs, preOutputs);
				
				if(status.step==2) {
					// it's return by early-update
					FReturnType rtFxy = f(x, status, y, other, preInputs, preOutputs);
					FReturnType rtFxz = f(x, status, status.z, other, preInputs, preOutputs);
					
												
					SparseVector fxy2  = rtFxy.sv2;
					SparseVector fxz2 = rtFxz.sv2;
					SparseVector temp = fxy2.vectorAdd(fxz2, -1);
					w2 = w2.vectorAdd(temp, learningRate);
					
				} else if(status.step==3 && !status.z.isIdenticalWith(x, y, status)) {
					// if the predicted answer are not identical to the gold answer, update the model.
					FReturnType rtFxy = f(x, status, y, other, preInputs, preOutputs);
					FReturnType rtFxz = f(x, status, status.z, other, preInputs, preOutputs);
					
												
					SparseVector fxy2  = rtFxy.sv2;
					SparseVector fxz2 = rtFxz.sv2;
					SparseVector temp = fxy2.vectorAdd(fxz2, -1);
					w2 = w2.vectorAdd(temp, learningRate);
				} 
			}
			
			
			long endTime = System.currentTimeMillis();
			// System.out.println("train finished "+(endTime-startTime)+" ms");

		} catch(Exception e) {
			e.printStackTrace();
		}
		

		return;
	}

	
	public void trainPerceptron(int T, int beamSize, ArrayList<PerceptronInputData> input, ArrayList<PerceptronOutputData> output, Object other) {
		try {		
			for(int i=0;i<T;i++) {
				
				long startTime = System.currentTimeMillis();
				
				for(int j=0;j<input.size();j++) {
					PerceptronInputData x = input.get(j);
					PerceptronOutputData y = output.get(j);
					// prepare the window, we assume the data is ordered
					ArrayList<PerceptronInputData> preInputs = new ArrayList<PerceptronInputData>();
					ArrayList<PerceptronOutputData> preOutputs = new ArrayList<PerceptronOutputData>();
					for(int k=0;k<window;k++) {
						int index = j-window+k;
						if(index<0) continue;
						// if the previous sentence and current sentence are not in the same document, ignore it
						if(!input.get(index).id.equals(x.id))
							continue;
						
						preInputs.add(input.get(index));
						preOutputs.add(output.get(index));
					}
					// get the best predicted answer
					PerceptronStatus status = beamSearch(x, y, true, beamSize, other,preInputs, preOutputs);
					
					if(status.step==2) {
						// it's return by early-update
						FReturnType rtFxy = f(x, status, y, other, preInputs, preOutputs);
						FReturnType rtFxz = f(x, status, status.z, other, preInputs, preOutputs);
						
													
						SparseVector fxy2  = rtFxy.sv2;
						SparseVector fxz2 = rtFxz.sv2;
						SparseVector temp = fxy2.vectorAdd(fxz2, -1);
						w2 = w2.vectorAdd(temp, learningRate);
						
					} else if(status.step==3 && !status.z.isIdenticalWith(x, y, status)) {
						// if the predicted answer are not identical to the gold answer, update the model.
						FReturnType rtFxy = f(x, status, y, other, preInputs, preOutputs);
						FReturnType rtFxz = f(x, status, status.z, other, preInputs, preOutputs);
						
													
						SparseVector fxy2  = rtFxy.sv2;
						SparseVector fxz2 = rtFxz.sv2;
						SparseVector temp = fxy2.vectorAdd(fxz2, -1);
						w2 = w2.vectorAdd(temp, learningRate);
					} 
		
					
				}
				
				
				
				long endTime = System.currentTimeMillis();
				System.out.println((i+1)+" train finished with "+(endTime-startTime)+" ms");
				

			}
			System.out.println("achieve max training times, quit");
		} catch(Exception e) {
			e.printStackTrace();
		}
		// norm
		normalizeWeight(T, input.size());

		return;
	}
	
	
	
	public void kBest(PerceptronInputData x, PerceptronStatus status, ArrayList<PerceptronOutputData> beam, ArrayList<PerceptronOutputData> buf, int beamSize, Object other,
			ArrayList<PerceptronInputData> preInputs, ArrayList<PerceptronOutputData> preOutputs)throws Exception {
		// compute all the scores in the buf
		TDoubleArrayList scores = new TDoubleArrayList();
		for(PerceptronOutputData y:buf) {
			FReturnType ret = f(x,status,y, other, preInputs, preOutputs);
			if(status.step==2) {
				scores.add(w2.dotProduct(ret.sv2));
			} else 
				throw new Exception();
			
		}
		
		// assign k best to the beam, and note that buf may be more or less than beamSize.
		int K = buf.size()>beamSize ? beamSize:buf.size();
		PerceptronOutputData[] temp = new PerceptronOutputData[K];
		Double[] tempScore = new Double[K];
		for(int i=0;i<buf.size();i++) {
			for(int j=0;j<K;j++) {
				if(temp[j]==null || scores.get(i)>tempScore[j]) {
					if(temp[j] != null) {
						for(int m=K-2;m>=j;m--) {
							temp[m+1] = temp[m];
							tempScore[m+1] = tempScore[m];
						}
					}
					
					temp[j] = buf.get(i);
					tempScore[j] = scores.get(i);
					break;
				}
			}
		}
		
		beam.clear();
		for(int i=0;i<K;i++) {
			beam.add(temp[i]);
		}
		
		return;
	}
	
	public class FReturnType {
		public SparseVector sv1;
		public SparseVector sv2;
		public FReturnType(SparseVector sv1, SparseVector sv2) {
			super();
			this.sv1 = sv1;
			this.sv2 = sv2;
		}
	}
	
	protected FReturnType f(PerceptronInputData x, PerceptronStatus status, PerceptronOutputData y, Object other,
			ArrayList<PerceptronInputData> preInputs, ArrayList<PerceptronOutputData> preOutputs) throws Exception {	
		
		if(y.isGold) {
			if(status.step==0) { // initialize the feature vectors of gold output
								
				TObjectDoubleHashMap<String> map2 = new TObjectDoubleHashMap<>();
				for(int j=0;j<featureFunctions2.size();j++) {
					PerceptronFeatureFunction featureFunction = featureFunctions2.get(j);
					featureFunction.compute(x, status, y, other, map2, preInputs, preOutputs);
				}
				y.featureVectors2.put(status.tokenIndex,hashMapToSparseVector(map2));
				return new FReturnType(null, y.featureVectors2.get(status.tokenIndex));
			} else if(status.step==2) {
				return new FReturnType(null, y.featureVectors2.get(status.tokenIndex));
			} else if(status.step==3) {
				return new FReturnType(null, y.featureVectors2.get(status.tokenIndex));
			} else
				throw new Exception();
			
		} else {
			if(status.step==2) {
								
				TObjectDoubleHashMap<String> map = new TObjectDoubleHashMap<>();
				for(int j=0;j<featureFunctions2.size();j++) {
					PerceptronFeatureFunction featureFunction = featureFunctions2.get(j);
					featureFunction.compute(x, status, y, other, map, preInputs, preOutputs);
				}
				y.featureVector2 = hashMapToSparseVector(map);
				return new FReturnType(null, y.featureVector2);
			} else if(status.step==3) {
								
				TObjectDoubleHashMap<String> map2 = new TObjectDoubleHashMap<>();
				for(int j=0;j<featureFunctions2.size();j++) {
					PerceptronFeatureFunction featureFunction = featureFunctions2.get(j);
					featureFunction.compute(x, status, y, other, map2, preInputs, preOutputs);
				}
				y.featureVector2 = hashMapToSparseVector(map2);
				return new FReturnType(null, y.featureVector2);
			} else
				throw new Exception();
			
		}

	}
	
	
	public SparseVector hashMapToSparseVector(TObjectDoubleHashMap<String> map) {
		TIntArrayList featureIndices = new TIntArrayList();
		TDoubleArrayList featureValues = new TDoubleArrayList();
		String[] keys = map.keys( new String[ map.size() ] );
		for(String featureName:keys) {
			featureIndices.add(this.featureAlphabet.get(featureName));
    		featureValues.add(map.get(featureName));
		}
		
        int[] featureIndicesArr = new int[featureIndices.size()];
        double[] featureValuesArr = new double[featureValues.size()];
        for (int index = 0; index < featureIndices.size(); index++) {
        	featureIndicesArr[index] = featureIndices.get(index);
        	featureValuesArr[index] = featureValues.get(index);
        }
		
        SparseVector fxy = new SparseVector(featureIndicesArr, featureValuesArr, false);
        return fxy;
	}
	

}
