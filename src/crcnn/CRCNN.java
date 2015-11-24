package crcnn;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import gnu.trove.TIntArrayList;

public class CRCNN implements Serializable{
	public Parameters para;
	public CRCNNmain owner;
	
	double [][] E;
	// convolution inside a sentence
	double [][] W1;
	double [] b1;
	// convolution between several sentences
	double [][] W2;
	double [] b2;
	
	double [][] Wclass;
	
	double[][] gradE;
	double[][] gradW1;
	double[]   gradb1;
	double[][] gradW2;
	double[]   gradb2;
	double[][] gradWclass;
	
	/*double[][] eg2E;
	double[][] eg2W1;
	double[]   eg2b1;
	double[][] eg2W2;
	double[]   eg2b2;
	double[][] eg2Wclass;*/
	
	public CRCNN(Parameters para, double[][] E, CRCNNmain owner) {
		this.para = para;
		this.owner = owner;
		this.E = E;
		Random random = new Random(System.currentTimeMillis());
		W1 = new double[para.convolutinalUnits][para.contextWindowSize*(para.embSize)];
		for(int i=0;i<W1.length;i++) {
			for(int j=0;j<W1[0].length;j++) {
				W1[i][j] = random.nextDouble() * 2 * para.initRange - para.initRange;
			}
		}
		
		b1 = new double[para.convolutinalUnits];
		for(int i=0;i<b1.length;i++) {
			b1[i] = random.nextDouble() * 2 * para.initRange - para.initRange;
		}
		
		W2 = new double[para.sentConvolUnites][para.sentWindowSize*para.convolutinalUnits];
		for(int i=0;i<W2.length;i++) {
			for(int j=0;j<W2[0].length;j++) {
				W2[i][j] = random.nextDouble() * 2 * para.initRange - para.initRange;
			}
		}
		
		b2 = new double[para.sentConvolUnites];
		for(int i=0;i<b2.length;i++) {
			b2[i] = random.nextDouble() * 2 * para.initRange - para.initRange;
		}
		
		Wclass = new double[para.classNumber][para.sentConvolUnites];
		for(int i=0;i<Wclass.length;i++) {
			for(int j=0;j<Wclass[0].length;j++) {
				Wclass[i][j] = random.nextDouble() * 2 * para.initRange - para.initRange;
			}
		}
		
		
	}
	
	public double[] computeScore(Example ex) {
		
		List<double[][]> convolMatrixes = new ArrayList<>();
		for(TIntArrayList sentFeatureID:ex.featureIDs) {
			// input -> convolution in one sentence
			double[][] convolMatrix = new double[sentFeatureID.size()][para.convolutinalUnits];
			
			for(int wordIdx=0;wordIdx<sentFeatureID.size();wordIdx++) {
				for(int convolIdx=0;convolIdx<para.convolutinalUnits;convolIdx++) {
					// W1*X
					int contextNumber = (para.contextWindowSize-1)/2;
					int offset = 0;
					for(int i=wordIdx-contextNumber;i<=wordIdx+contextNumber;i++) {
						int curWord = -1;
						if(i<0 || i>sentFeatureID.size()-1)
							curWord = owner.getWordID(Parameters.PADDING);
						else
							curWord = sentFeatureID.get(i);
						
						for(int j=0;j<para.embSize;j++) {
							convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+j]*E[curWord][j];
						}
						offset += para.embSize;	
					}
					
					// +b1
					convolMatrix[wordIdx][convolIdx] += b1[convolIdx];
					// tanh
					convolMatrix[wordIdx][convolIdx] = CRCNNmain.tanh(convolMatrix[wordIdx][convolIdx]);
				}
				
			}
			
			convolMatrixes.add(convolMatrix);
		}
		
		// max pooling
		List<double[]> sentRepresents = new ArrayList<>();
		for(double[][] convolMatrix: convolMatrixes) {
			double[] sentRepresent = new double[para.convolutinalUnits];
			for(int convolIdx=0;convolIdx<convolMatrix[0].length;convolIdx++) {
				double max = convolMatrix[0][convolIdx];
				for(int wordIdx=1;wordIdx<convolMatrix.length;wordIdx++) {
					if(max < convolMatrix[wordIdx][convolIdx])
						max = convolMatrix[wordIdx][convolIdx];
				}
				sentRepresent[convolIdx] = max;
			}
			sentRepresents.add(sentRepresent);
		}
		
		// sentence convolution
		int numSentConvol = sentRepresents.size()-para.sentWindowSize+1;
		double[][] sentConvolMatrix = new double[numSentConvol][para.sentConvolUnites];
		for(int countConvol=0; countConvol<numSentConvol;countConvol++) {
			for(int convolIdx=0; convolIdx<para.sentConvolUnites; convolIdx++) {
				// W2*sentRepresent
				int offset = 0;
				for(int i=0;i<para.sentWindowSize;i++) {
					int sentRepresentsIdx = countConvol + i;
					for(int j=0;j<para.convolutinalUnits;j++)
						sentConvolMatrix[countConvol][convolIdx] += W2[convolIdx][offset+j]*sentRepresents.get(sentRepresentsIdx)[j];
					
					offset += para.convolutinalUnits;
				}
				// +b2
				sentConvolMatrix[countConvol][convolIdx] += b2[convolIdx];
				// tanh
				sentConvolMatrix[countConvol][convolIdx] = CRCNNmain.tanh(sentConvolMatrix[countConvol][convolIdx]);
			}
		}
		
		// max pooling
		double[] r = new double[para.sentConvolUnites];
		for(int convolIdx=0;convolIdx<sentConvolMatrix[0].length;convolIdx++) {
			double max = sentConvolMatrix[0][convolIdx];
			for(int countConvol=1;countConvol<sentConvolMatrix.length;countConvol++) {
				if(max < sentConvolMatrix[countConvol][convolIdx])
					max = sentConvolMatrix[countConvol][convolIdx];
			}
			r[convolIdx] = max;
		}
		
		// rank
		double[] s = new double[para.classNumber];
		for(int classIdx=0;classIdx<para.classNumber;classIdx++) {
			for(int convolIdx=0;convolIdx<para.sentConvolUnites;convolIdx++) {
				s[classIdx] += Wclass[classIdx][convolIdx]*r[convolIdx];
			}
		}
		
		return s;
	}
	
		
	public double forwardbackward(Example ex, int batchSize) {
		List<double[][]> convolMatrixes = new ArrayList<>();
		for(TIntArrayList sentFeatureID:ex.featureIDs) {
			// input -> convolution in one sentence
			double[][] convolMatrix = new double[sentFeatureID.size()][para.convolutinalUnits];
			
			for(int wordIdx=0;wordIdx<sentFeatureID.size();wordIdx++) {
				for(int convolIdx=0;convolIdx<para.convolutinalUnits;convolIdx++) {
					// W1*X
					int contextNumber = (para.contextWindowSize-1)/2;
					int offset = 0;
					for(int i=wordIdx-contextNumber;i<=wordIdx+contextNumber;i++) {
						int curWord = -1;
						if(i<0 || i>sentFeatureID.size()-1)
							curWord = owner.getWordID(Parameters.PADDING);
						else
							curWord = sentFeatureID.get(i);
						
						for(int j=0;j<para.embSize;j++) {
							convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+j]*E[curWord][j];
						}
						offset += para.embSize;	
					}
					// +b1
					convolMatrix[wordIdx][convolIdx] += b1[convolIdx];
					// tanh
					convolMatrix[wordIdx][convolIdx] = CRCNNmain.tanh(convolMatrix[wordIdx][convolIdx]);
				}
				
			}
			
			convolMatrixes.add(convolMatrix);
		}
		
		// max pooling
		List<double[]> sentRepresents = new ArrayList<>();
		List<int[]> Kes = new ArrayList<>();
		for(double[][] convolMatrix: convolMatrixes) {
			double[] sentRepresent = new double[para.convolutinalUnits];
			int[] K = new int[sentRepresent.length];
			for(int convolIdx=0;convolIdx<convolMatrix[0].length;convolIdx++) {
				double max = convolMatrix[0][convolIdx];
				K[convolIdx] = 0;
				for(int wordIdx=1;wordIdx<convolMatrix.length;wordIdx++) {
					if(max < convolMatrix[wordIdx][convolIdx]) {
						max = convolMatrix[wordIdx][convolIdx];
						K[convolIdx] = wordIdx;
					}
				}
				sentRepresent[convolIdx] = max;
			}
			sentRepresents.add(sentRepresent);
			Kes.add(K);
		}
		
		// sentence convolution
		int numSentConvol = sentRepresents.size()-para.sentWindowSize+1;
		double[][] sentConvolMatrix = new double[numSentConvol][para.sentConvolUnites];
		for(int countConvol=0; countConvol<numSentConvol;countConvol++) {
			for(int convolIdx=0; convolIdx<para.sentConvolUnites; convolIdx++) {
				// W2*sentRepresent
				int offset = 0;
				for(int i=0;i<para.sentWindowSize;i++) {
					int sentRepresentsIdx = countConvol + i;
					for(int j=0;j<para.convolutinalUnits;j++)
						sentConvolMatrix[countConvol][convolIdx] += W2[convolIdx][offset+j]*sentRepresents.get(sentRepresentsIdx)[j];
					
					offset += para.convolutinalUnits;
				}
				// +b2
				sentConvolMatrix[countConvol][convolIdx] += b2[convolIdx];
				// tanh
				sentConvolMatrix[countConvol][convolIdx] = CRCNNmain.tanh(sentConvolMatrix[countConvol][convolIdx]);
			}
		}
		
		// max pooling
		double[] r = new double[para.sentConvolUnites];
		double[] sentK = new double[para.sentConvolUnites];
		for(int convolIdx=0;convolIdx<sentConvolMatrix[0].length;convolIdx++) {
			double max = sentConvolMatrix[0][convolIdx];
			sentK[convolIdx] = 0;
			for(int countConvol=1;countConvol<sentConvolMatrix.length;countConvol++) {
				if(max < sentConvolMatrix[countConvol][convolIdx]) {
					max = sentConvolMatrix[countConvol][convolIdx];
					sentK[convolIdx] = countConvol;
				}
			}
			r[convolIdx] = max;
		}
		
		// rank
		double[] s = new double[para.classNumber];
		for(int classIdx=0;classIdx<para.classNumber;classIdx++) {
			for(int convolIdx=0;convolIdx<para.sentConvolUnites;convolIdx++) {
				s[classIdx] += Wclass[classIdx][convolIdx]*r[convolIdx];
			}
		}
		
		// loss
		int yPositive = -1;
		int cNegative = -1;
		if(ex.label==0) {
			yPositive = 0;
			cNegative = 1;
		}
		else {
			yPositive = 1;
			cNegative = 0;
		}
		double loss = Math.log(1+CRCNNmain.exp(para.gamma*(para.mPostive-s[yPositive]))) +
					  Math.log(1+CRCNNmain.exp(para.gamma*(para.mNegative+s[cNegative])));
		
		
		// rank layer
		double[] grad_r = new double[r.length];
		for(int i=0;i<Wclass.length;i++) {
			double delta = i==yPositive ? 
					-para.gamma*CRCNNmain.exp(para.gamma*(para.mPostive-s[yPositive]))/(/*batchSize**/(1+CRCNNmain.exp(para.gamma*(para.mPostive-s[yPositive])))) :
					para.gamma*CRCNNmain.exp(para.gamma*(para.mNegative+s[cNegative]))/(/*batchSize**/(1+CRCNNmain.exp(para.gamma*(para.mNegative+s[cNegative]))));
			for(int j=0;j<Wclass[0].length;j++) {
				gradWclass[i][j] += delta*r[j];
				grad_r[j] += delta*Wclass[i][j];
			}
		}
		
		// discourse
		double[][] N = new double[sentConvolMatrix.length][sentConvolMatrix[0].length]; // N[i][j] is delta[i][j]
		for(int i=0;i<N.length;i++) {
			for(int j=0;j<N[0].length;j++) {
				if(i!=sentK[j])
					N[i][j] = 0;
				else
					N[i][j] = grad_r[j]*(1-sentConvolMatrix[i][j]*sentConvolMatrix[i][j]);
			}
		}
		
		List<double[]> grad_sentences = new ArrayList<>();
		for(double[] sentence:sentRepresents) {
			double[] x = new double[sentence.length];
			grad_sentences.add(x);
		}
		for(int i=0;i<numSentConvol;i++) { 
			for(int j=0;j<W2.length;j++) {
				gradb2[j] += N[i][j];
				
				int offset = 0;
				for(int sentWindowIdx=0;sentWindowIdx<para.sentWindowSize;sentWindowIdx++) {
					int sentIdx = i+sentWindowIdx;
					double[] sentence = sentRepresents.get(sentIdx);
					double[] grad_sentence = grad_sentences.get(sentIdx);
					for(int sentRepresentIdx=0;sentRepresentIdx<sentence.length;sentRepresentIdx++) {
						gradW2[j][sentRepresentIdx+offset] += N[i][j] * sentence[sentRepresentIdx];
						grad_sentence[sentRepresentIdx] += N[i][j] * W2[j][sentRepresentIdx+offset];
					}

					offset += sentence.length;
				}
				
			}
		}
		
		// sentence
		for(int sentIdx = 0;sentIdx<sentRepresents.size();sentIdx++) {
			double[] sentence = sentRepresents.get(sentIdx);
			double[] grad_sentence = grad_sentences.get(sentIdx);
			double[][] convolMatrix = convolMatrixes.get(sentIdx);
			int[] K = Kes.get(sentIdx);
			TIntArrayList sentFeatureID = ex.featureIDs.get(sentIdx);
			double[][] B = new double[convolMatrix.length][convolMatrix[0].length];
			
			for(int i=0;i<B.length;i++) {
				for(int j=0;j<B[0].length;j++) {
					if(i!=K[j])
						B[i][j] = 0;
					else
						B[i][j] = grad_sentence[j]*(1-convolMatrix[i][j]*convolMatrix[i][j]);
				}
			}
			
			for(int wordIdx=0;wordIdx<sentFeatureID.size();wordIdx++) { 
				for(int convolIdx=0;convolIdx<W1.length;convolIdx++) {
					gradb1[convolIdx] += B[wordIdx][convolIdx];
					
					int contextNumber = (para.contextWindowSize-1)/2;
					int offset = 0;
					for(int i=wordIdx-contextNumber;i<=wordIdx+contextNumber;i++) {
						int curWord = -1;
						if(i<0 || i>sentFeatureID.size()-1)
							curWord = owner.getWordID(Parameters.PADDING);
						else
							curWord = sentFeatureID.get(i);
						
						for(int j=0;j<para.embSize;j++) {
							gradW1[convolIdx][j+offset] += B[wordIdx][convolIdx]*E[curWord][j];
							gradE[curWord][j] += B[wordIdx][convolIdx] * W1[convolIdx][j+offset];
						}
						offset += para.embSize;	
					}
					
				}
			}

		}
		

		return loss;
	}
	
	public void initial() {
		/*eg2E = new double[E.length][E[0].length];
		eg2W1 = new double[W1.length][W1[0].length];
		eg2b1 = new double[b1.length];
		eg2W2 = new double[W2.length][W2[0].length];
		eg2b2 = new double[b2.length];
		eg2Wclass = new double[Wclass.length][Wclass[0].length];*/
	}
	
/*	public double minibatch(List<Example> examples) {
		gradE = new double[E.length][E[0].length];
		gradW1 = new double[W1.length][W1[0].length];
		gradb1 = new double[b1.length];
		gradW2 = new double[W2.length][W2[0].length];
		gradb2 = new double[b2.length];
		gradWclass = new double[Wclass.length][Wclass[0].length];
		double loss = 0;
		for(Example ex:examples) {
			loss += forwardbackward(ex, examples.size());
		}
		loss = addL2NormalandAdaGrad(loss);
		return loss;
	}*/
	
	public void sgd(List<Example> examples, boolean debug) {
		double loss = 0;
		for(Example ex:examples) {
			gradE = new double[E.length][E[0].length];
			gradW1 = new double[W1.length][W1[0].length];
			gradb1 = new double[b1.length];
			gradW2 = new double[W2.length][W2[0].length];
			gradb2 = new double[b2.length];
			gradWclass = new double[Wclass.length][Wclass[0].length];
			double temploss = 0;
			temploss = forwardbackward(ex, examples.size());
			temploss = addL2NormalandUpdate(temploss);
			loss += temploss;
		}
		if(debug)
			System.out.println("loss "+loss);
	}
	
	public double addL2NormalandUpdate(double loss) {
		// l2 normalization
		for(int i=0;i<gradWclass.length;i++) {
			for(int j=0;j<gradWclass[0].length;j++) {
				loss += para.regParameter * Wclass[i][j] * Wclass[i][j] / 2.0;
				gradWclass[i][j] += para.regParameter * Wclass[i][j];
				Wclass[i][j] -= para.initialLearningRate * gradWclass[i][j];
				/*eg2Wclass[i][j] += gradWclass[i][j]*gradWclass[i][j];
				Wclass[i][j] -= para.initialLearningRate * gradWclass[i][j] / Math.sqrt(eg2Wclass[i][j]+para.adaEps) ;*/
			}
		}
				
		for(int i=0;i<gradW2.length;i++) {
			for(int j=0;j<gradW2[0].length;j++) {
				loss += para.regParameter * W2[i][j] * W2[i][j] / 2.0;
				gradW2[i][j] += para.regParameter * W2[i][j];
				W2[i][j] -= para.initialLearningRate * gradW2[i][j];
				/*eg2W2[i][j] += gradW2[i][j]*gradW2[i][j];
				W2[i][j] -= para.initialLearningRate * gradW2[i][j] / Math.sqrt(eg2W2[i][j]+para.adaEps) ;*/
			}
		}
				
		for(int i=0;i<gradb2.length;i++) {
			loss += para.regParameter * b2[i] * b2[i] / 2.0;
			gradb2[i] += para.regParameter * b2[i];
			b2[i] -= para.initialLearningRate * gradb2[i];
			/*eg2b2[i] += gradb2[i]*gradb2[i];
			b2[i] -= para.initialLearningRate * gradb2[i] / Math.sqrt(eg2b2[i]+para.adaEps) ;*/
		}
		
		for(int i=0;i<gradb1.length;i++) {
			loss += para.regParameter * b1[i] * b1[i] / 2.0;
			gradb1[i] += para.regParameter * b1[i];
			b1[i] -= para.initialLearningRate * gradb1[i];
			/*eg2b1[i] += gradb1[i]*gradb1[i];
			b1[i] -= para.initialLearningRate * gradb1[i] / Math.sqrt(eg2b1[i]+para.adaEps) ;*/
		}

		for(int i=0;i<gradW1.length;i++) {
			for(int j=0;j<gradW1[0].length;j++) {
				loss += para.regParameter * W1[i][j] * W1[i][j] / 2.0;
				gradW1[i][j] += para.regParameter * W1[i][j];
				W1[i][j] -= para.initialLearningRate * gradW1[i][j];
				/*eg2W1[i][j] += gradW1[i][j]*gradW1[i][j];
				W1[i][j] -= para.initialLearningRate * gradW1[i][j] / Math.sqrt(eg2W1[i][j]+para.adaEps) ;*/
			}
		}

		for(int i=0;i<gradE.length;i++) {
			for(int j=0;j<gradE[0].length;j++) {
				loss += para.regParameter * E[i][j] * E[i][j] / 2.0;
				gradE[i][j] += para.regParameter * E[i][j];
				E[i][j] -= para.initialLearningRate * gradE[i][j];
				/*eg2E[i][j] += gradE[i][j]*gradE[i][j];
				E[i][j] -= para.initialLearningRate * gradE[i][j] / Math.sqrt(eg2E[i][j]+para.adaEps) ;*/
			}
		}
		return loss;
	}

	
}
