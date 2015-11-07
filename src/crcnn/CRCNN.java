package crcnn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import gnu.trove.TIntArrayList;

public class CRCNN {
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
					int preWord = wordIdx==0 ? owner.getWordID(Parameters.PADDING) : sentFeatureID.get(wordIdx-1);
					int curWord = sentFeatureID.get(wordIdx);
					int nextWord = wordIdx==sentFeatureID.size()-1 ? owner.getWordID(Parameters.PADDING) : sentFeatureID.get(wordIdx+1);
					
					int offset = 0;
					for(int i=0;i<para.embSize;i++) {
						convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+i]*E[preWord][i];
					}
					offset += para.embSize;
					for(int i=0;i<para.embSize;i++) {
						convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+i]*E[curWord][i];
					}
					offset += para.embSize;
					for(int i=0;i<para.embSize;i++) {
						convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+i]*E[nextWord][i];
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
	
	public void forwardbackward(Example ex) {
		List<double[][]> convolMatrixes = new ArrayList<>();
		for(TIntArrayList sentFeatureID:ex.featureIDs) {
			// input -> convolution in one sentence
			double[][] convolMatrix = new double[sentFeatureID.size()][para.convolutinalUnits];
			
			for(int wordIdx=0;wordIdx<sentFeatureID.size();wordIdx++) {
				for(int convolIdx=0;convolIdx<para.convolutinalUnits;convolIdx++) {
					// W1*X
					int preWord = wordIdx==0 ? owner.getWordID(Parameters.PADDING) : sentFeatureID.get(wordIdx-1);
					int curWord = sentFeatureID.get(wordIdx);
					int nextWord = wordIdx==sentFeatureID.size()-1 ? owner.getWordID(Parameters.PADDING) : sentFeatureID.get(wordIdx+1);
					
					int offset = 0;
					for(int i=0;i<para.embSize;i++) {
						convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+i]*E[preWord][i];
					}
					offset += para.embSize;
					for(int i=0;i<para.embSize;i++) {
						convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+i]*E[curWord][i];
					}
					offset += para.embSize;
					for(int i=0;i<para.embSize;i++) {
						convolMatrix[wordIdx][convolIdx] += W1[convolIdx][offset+i]*E[nextWord][i];
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
		
		// rank layer
		double[][] gradWclass = new double[Wclass.length][Wclass[0].length];
		double[] grad_r = new double[r.length];
		for(int i=0;i<Wclass.length;i++) {
			double delta = i==yPositive ? 
					-para.gamma*CRCNNmain.exp(para.gamma*(para.mPostive-s[yPositive]))/(1+CRCNNmain.exp(para.gamma*(para.mPostive-s[yPositive]))) :
					para.gamma*CRCNNmain.exp(para.gamma*(para.mNegative-s[cNegative]))/(1+CRCNNmain.exp(para.gamma*(para.mNegative-s[cNegative])));
			for(int j=0;j<Wclass[0].length;j++) {
				gradWclass[i][j] = delta*r[j];
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
		
		double[] grad_b2 = new double[b2.length];
		double[][] grad_W2 = new double[W2.length][W2[0].length];
		List<double[]> grad_sentences = new ArrayList<>();
		for(double[] sentence:sentRepresents) {
			double[] x = new double[sentence.length];
			grad_sentences.add(x);
		}
		for(int i=0;i<numSentConvol;i++) { 
			for(int j=0;j<W2.length;j++) {
				grad_b2[j] += N[i][j];
				
				int offset = 0;
				for(int sentWindowIdx=0;sentWindowIdx<para.sentWindowSize;sentWindowIdx++) {
					int sentIdx = i+sentWindowIdx;
					double[] sentence = sentRepresents.get(sentIdx);
					double[] grad_sentence = grad_sentences.get(sentIdx);
					for(int sentRepresentIdx=0;sentRepresentIdx<sentence.length;sentRepresentIdx++) {
						grad_W2[j][sentRepresentIdx+offset] += N[i][j] * sentence[sentRepresentIdx];
						grad_sentence[sentRepresentIdx] += N[i][j] * W2[j][sentRepresentIdx+offset];
					}

					offset += sentence.length;
				}
				
			}
		}
		
		// sentence
		/*List<double[]> grad_b1s = new ArrayList<>();
		List<double[][]> grad_W1s = new ArrayList<>();*/
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
			
			double[] grad_b1 = new double[b1.length];
			double[][] grad_W1 = new double[W1.length][W1[0].length];
			double[][] grad_E = new double[E.length][E[0].length];
			for(int i=0;i<sentFeatureID.size();i++) { 
				for(int j=0;j<W1.length;j++) {
					grad_b1[j] += B[i][j];
					
					int preWord = i==0 ? owner.getWordID(Parameters.PADDING) : sentFeatureID.get(i-1);
					int curWord = sentFeatureID.get(i);
					int nextWord = i==sentFeatureID.size()-1 ? owner.getWordID(Parameters.PADDING) : sentFeatureID.get(i+1);
					
					int offset = 0;
					for(int embIdx=0;embIdx<para.embSize;embIdx++) {
						grad_W1[j][embIdx+offset] += B[i][j]*E[preWord][embIdx];
						grad_E[preWord][embIdx] += B[i][j] * W1[j][embIdx+offset];
					}
					offset += para.embSize;
					for(int embIdx=0;embIdx<para.embSize;embIdx++) {
						grad_W1[j][embIdx+offset] += B[i][j]*E[curWord][embIdx];
						grad_E[curWord][embIdx] += B[i][j] * W1[j][embIdx+offset];
					}
					offset += para.embSize;
					for(int embIdx=0;embIdx<para.embSize;embIdx++) {
						grad_W1[j][embIdx+offset] += B[i][j]*E[nextWord][embIdx];
						grad_E[nextWord][embIdx] += B[i][j] * W1[j][embIdx+offset];
					}
				}
			}
			
			
		}
		
		
	}
}
