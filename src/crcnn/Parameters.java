package crcnn;


import java.io.Serializable;
import java.util.Properties;
import edu.stanford.nlp.util.PropertiesUtils;


public class Parameters implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -8092716117880301475L;
	/**
	  *   Out-of-vocabulary token string.
	  */
	  public static final String UNKNOWN = "-UNKN-";
	  // Out of window token string
	  public static final String PADDING = "-PAD-";
	  public static final String SEPARATOR = "###################";
	  public static final String CHEMICAL = "-Chem-";
	  public static final String DISEASE = "-Dise-";
	  public static final String RELATION = "CID";
	

	  public double initRange = 0.01;

	  public int maxIter = 20;
	  public int evalPerIter = 2;

	  public double regParameter = 0.001;

	  public int embSize = 400;
	
	  public int convolutinalUnits = 1000;
	  public int contextWindowSize = 3;
	  
	  public int sentConvolUnites = 500;
	  public int sentWindowSize = 2;
	  
	  public int classNumber = 2;
	  
	  public double initialLearningRate = 0.025;
	  
	  public double gamma = 2;
	  public double mPostive = 2.5;
	  public double mNegative = 0.5;

	  public Parameters(Properties properties) {
	    setProperties(properties);
	  }
	
	  private void setProperties(Properties props) {
		initRange = PropertiesUtils.getDouble(props, "initRange", initRange);
		maxIter = PropertiesUtils.getInt(props, "maxIter", maxIter);
		regParameter = PropertiesUtils.getDouble(props, "regParameter", regParameter);
		evalPerIter = PropertiesUtils.getInt(props, "evalPerIter", evalPerIter);
		embSize = PropertiesUtils.getInt(props, "embSize", embSize);
		convolutinalUnits = PropertiesUtils.getInt(props, "convolutinalUnits", convolutinalUnits);
		contextWindowSize = PropertiesUtils.getInt(props, "contextWindowSize", contextWindowSize);
		sentConvolUnites = PropertiesUtils.getInt(props, "sentConvolUnites", sentConvolUnites);
		sentWindowSize = PropertiesUtils.getInt(props, "sentWindowSize", sentWindowSize);
		classNumber = PropertiesUtils.getInt(props, "classNumber", classNumber);
		initialLearningRate = PropertiesUtils.getDouble(props, "initialLearningRate", initialLearningRate); 
		gamma = PropertiesUtils.getDouble(props, "gamma", gamma);  
		mPostive = PropertiesUtils.getDouble(props, "mPostive", mPostive);  
		mNegative = PropertiesUtils.getDouble(props, "mNegative", mNegative);  
		

	  }
	
	 	
	  public void printParameters() {
		
		System.out.printf("initRange = %.2g%n", initRange);
		System.out.printf("maxIter = %d%n", maxIter);
		System.out.printf("regParameter = %.2g%n", regParameter);
		System.out.printf("evalPerIter = %d%n", evalPerIter);
		System.out.printf("embSize = %d%n", embSize);
		System.out.printf("convolutinalUnits = %d%n", convolutinalUnits);
		System.out.printf("contextWindowSize = %d%n", contextWindowSize);
		System.out.printf("sentConvolUnites = %d%n", sentConvolUnites);
		System.out.printf("sentWindowSize = %d%n", sentWindowSize);
		System.out.printf("classNumber = %d%n", classNumber);
		System.out.printf("initialLearningRate = %.2g%n", initialLearningRate);
		System.out.printf("gamma = %.2g%n", gamma);
		System.out.printf("mPostive = %.2g%n", mPostive);
		System.out.printf("mNegative = %.2g%n", mNegative);
		
		System.out.println(SEPARATOR);
	  }
}
