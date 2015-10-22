package utils;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;

/**
 * Sieve 1-Exact Match, Sieve 2-Abbreviation Expansion,Sieve 3-Subject/object conversion
 * Sieve 4-Numbers Replacement,Sieve 5-DiseaseSynomymsReplacement
 * Sieve 6-Affixation,Sieve 7-Stemming
 * Sieve 8-Partial Match
 * @author Administrator
 *
 */
public class MultiSieve {
	//Sieve 1 - Exact Match
	public Map<String,List<String>> idToNames = new HashMap<String,List<String>>();//疾病Id对应的名称
	public String preId;
	
	public MultiSieve(Map<String,List<String>> idToNames){
		this.idToNames = idToNames;		
	}
	public String isExactMap(String entityText){		
		
		//String minDistanceToMeshId = null;
		preId = null;
		String meshId = null;
	    Iterator iter = idToNames.entrySet().iterator();
	    boolean isComma = false;
	    if(entityText.contains(","))
	    	isComma = true;
	   
	    while(iter.hasNext()){
	    	Map.Entry entry = (Map.Entry) iter.next();
	    	meshId = (String) entry.getKey();
	    	
	    	List<String> names = (List<String>)entry.getValue();	
	    	//找精确匹配
	    	
	    	for(String name : names){
	    		if(name.contains(",") && !isComma)
	    			name = name.replace(",", "");//若entityText没有逗号，name中有逗号，则把name中的逗号去掉。
	    		if(entityText.compareToIgnoreCase(name.trim()) == 0)
	    		    preId = meshId.substring(meshId.indexOf(':')+1);				
	    	}
	    }    	
		
	    return preId;
	
}
	//sieve-2 singular ---plural switch
	public String singularToPlural(String entityText,List<String>entityTextList){
		if(!isContain(entityText,entityTextList))
			entityTextList.add(entityText);
		String tempReturn = null;
		String[] entityTokens = entityText.split(" ");
		String tempEntityText = "";		
		for(int i = 0; i < entityTokens.length; i++){
			String tempStr = entityTokens[i];
			if(tempStr.length() > 2){//单数变复数，复数变单数
				if(tempStr.charAt(tempStr.length() -1)=='s'){
					//如果是"'s"结尾的，去掉"'s"				
				    tempStr = tempStr.substring(0,tempStr.length()-1);//drop s				  
				}
				else
				   tempStr = tempStr.concat("s");			   
				}
				tempEntityText += tempStr +" ";
			}
		tempReturn = isExactMap(tempEntityText.trim());
		if(tempReturn == null){
			if(!isContain(tempEntityText,entityTextList))
		       entityTextList.add(tempEntityText.trim()); 
		}
	return tempReturn;		
	}
	//sieve-3 
	
    private static final Map<String, String> suffixMap;//后缀
	    static {
	        Map<String, String> aMap = new HashMap<String,String>();
	        aMap.put("sion","sive");
	        aMap.put("sions", "sive");
	        aMap.put("sive","sion");
	        aMap.put("'s","");
	        suffixMap = Collections.unmodifiableMap(aMap);
	    }	

	public String diseaseAffixationReplace(String entityText,List<String> entityTextList) {
		
		String affixationReplaceReturn = null;
		List<String> replaceList = new ArrayList<String>();
		
		// compare to suffixMap;//后缀 entityTextList中包含单数和复数entityText，所以不用在对entityText本身判断。
		for (int i = 0; i < entityTextList.size(); i++) {
			String tempEntity = entityTextList.get(i);
			Iterator iter = suffixMap.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				String suffixKey = (String) entry.getKey();
				String suffixValue = (String) entry.getValue();
				String tempReplaced = null;
				tempReplaced = isSuffixAndReplace(tempEntity, suffixKey,
						suffixValue);
				if ((tempReplaced != null) && !tempReplaced.equals(tempEntity)) {
					if (!isContain(tempReplaced, entityTextList)) {
						affixationReplaceReturn = isExactMap(tempReplaced);
						if (affixationReplaceReturn != null)
							return affixationReplaceReturn;
						else{
							if (!isContain(tempReplaced, replaceList))
								replaceList.add(tempReplaced);
						}
					}
				}
			}

		}
		if (replaceList.size() > 0) {
			for (String tempReplace : replaceList) {
				entityTextList.add(tempReplace);
			}
		}
		return affixationReplaceReturn;
	}

	public boolean isContain(String target, List<String> entityTextList) {
		for (String entity : entityTextList) {
			if (target.equals(entity))
				return true;
		}
		return false;
	}	
	//Sieve 3-DiseaseSynomyms

	List<String> diseaseSynonymsSingularList = new ArrayList<String>(){{
		add("disease");
		add("disorder");
		add("deficiency");
		add("condition");
		add("syndrome");
		add("symptom");
		add("abnormality");
		add("issue");
		add("drug");
		add("event");
		add("impairment");
		add("diagnosis");
		add("dysfunction");
		add("injury");
	}};	
	List<String> disorderSynonymsPluralList = new ArrayList<String>() {{
		add("diseases");
		add("disorders");
		add("deficiencies");
		add("conditions");
		add("syndromes");
		add("symptoms");
		add("abnormalities");
		add("issues");
		add("drugs");
		add("events");
		add("impairments");
		add("dysfunctions");
		add("injuries");
	}};
	List<String> tumorSynonymsList = new ArrayList<String>(){{		
		add("tumour");
		add("cancer");	
		add("neoplasm");
		add("tumours");
		add("cancers");	
		add("neoplasms");
		add("carcinomas");
		add("complications");
		
	}};	
	
	/*sieve-4
	 * 先判断entityText词中是否有疾病的同义词，若含有疾病同义词，则判断去掉同义词，是否能找到合适的Id号；
	 *若entityText中没有疾病同义词，先追加，然后判断能否找到合适的id
	 **/
	public String dropDiseaseOrAppendSynonyms(String entityText,List<String> entityTextList){
		String tempReturn = null;		
		String tempEntityText = "";
		if(!isContain(entityText,entityTextList))
			entityTextList.add(entityText);
		
		String[] entityTokens = entityText.split(" ");
		boolean isDisease = false;
		for(String entityToken: entityTokens){
			if( isContain(entityToken,diseaseSynonymsSingularList) || isContain(entityText,disorderSynonymsPluralList) )
				isDisease = true;
		}
		
		if(isDisease){
			
		//若含有疾病同义词，则判断去掉同义词，是否能找到合适的Id号。	
		List<String> dropEntityList = new ArrayList<String>();
		for(int i= 0; i< entityTextList.size(); i++){
			String tempEntity = entityTextList.get(i);
			String[] tempEntityTokens = tempEntity.split(" ");
			String tempToken = "";
			for(String tempEntityToken : tempEntityTokens){
				//tempEntityToken 是否为疾病的词，若是删除
				if(isContain(tempEntityToken,diseaseSynonymsSingularList))
					tempEntityToken = "";
				else if(isContain(tempEntityToken,disorderSynonymsPluralList))
					tempEntityToken = "";
				tempToken += tempEntityToken +" ";
			}
			if(tempToken.trim().length() > 0 && !tempToken.trim().equals(tempEntity)){
				tempReturn = isExactMap(tempToken.trim());
				if(tempReturn != null)
					return tempReturn;
				else{
				    if(!isContain(tempToken.trim(),dropEntityList))
				    	dropEntityList.add(i, tempToken.trim());
				}
			}
		}
			if(dropEntityList.size()>0){
			  for(String dropEntity:dropEntityList){
				  if(!isContain(dropEntity,entityTextList))
				      entityTextList.add(dropEntity);
			 } 
			}
			
		  
		} else{ //没有疾病同义词，则追加
			List<String> appendEntityList =  new ArrayList<String>();
			for(int i= 0; i< entityTextList.size(); i++){
				String tempEntity = entityTextList.get(i);
				for(String diseaseSynonymsSingular : diseaseSynonymsSingularList){
					tempReturn = isExactMap(tempEntity +" "+diseaseSynonymsSingular);
					if(tempReturn == null)
						appendEntityList.add(tempEntity +" "+ diseaseSynonymsSingular);
					else
						return tempReturn;
				}
				for(String diseaseSynonymsPlural : disorderSynonymsPluralList){
					tempReturn = isExactMap(tempEntity +" "+ diseaseSynonymsPlural);
					if(tempReturn == null)
						appendEntityList.add(tempEntity +" "+ diseaseSynonymsPlural);
					else
						return tempReturn;
				}			
					
				}
			if(appendEntityList.size() > 0){
				for(String tempAppend: appendEntityList){
					if(!isContain(tempAppend,entityTextList)){
						entityTextList.add(tempAppend);
					}
				}
			}
			
		}
		return tempReturn;
	}
	// Sieve 5-Subject/object conversion  	
	private  final List<String> prepositionsList = new ArrayList<String>() {{
		add("in");
		add("with");
		add("on");
		add("of");
	}};
	//(2)dropping the preposition from the name and swapping the substrings surrounding it (e.g.,
	//		changes on ekg converted to ekg changes
	public String dropPrepositionAndSwapping(String entityText,List<String>newEntityTextList,TokenizerFactory<CoreLabel> tokenizerFactory){
		//first judge entityText
		Tokenizer<CoreLabel> tempTokenize = tokenizerFactory.getTokenizer(new StringReader(entityText));
   		List<CoreLabel> entityTokens = tempTokenize.tokenize();
		List<String> entityTokenToStrs = new ArrayList<String>();
		for(int i =0; i< entityTokens.size();i++ ){
			String subToken = entityTokens.get(i).toString();
			entityTokenToStrs.add(subToken);
		}
		List<String> dropAndSwap = new ArrayList<String>();
			
		//注意找介词时，把介词后面的内容和介词前面的内容互换位置。
		String afterPrepositionWord = "";
		String frontPrepositionWord = "";
		String newText = "";	
		int index = entityTokens.size();//介词的位置	
		
		for(int i =  index -1; i >= 0; i--){
			String subToken = entityTokenToStrs.get(i);
			if(getPrepIndex(subToken)!=-1){ //说明subToken 是介词
				
			    if(index != i);
			    for(int j = i +1; j < index  ;j++){
			        afterPrepositionWord += entityTokenToStrs.get(j)+" ";
			    }
			    index = i;
//			    entityTokenToStrs.remove(i);
			   
			}			
		}
		if(index > 0){
			for(int j = 0; j < index; j++){
				frontPrepositionWord += entityTokenToStrs.get(j) + " ";
			}
						    	
			newText = (afterPrepositionWord + frontPrepositionWord).trim();
		    afterPrepositionWord = null;
			frontPrepositionWord = null;
		}
		
		 
		if(index == entityTokens.size()){
			return null; //表示originalEntity没有介词，直接退出，不需删除。
		}			
		String tempReturn =isExactMap(newText);
		if(tempReturn==null){
			newEntityTextList.add(newText);
		}
	//	System.out.println("the newEntityTextList length is : " +newEntityTextList.size());
		return tempReturn;
	}
 
	//subEntityText在介词列表中的位置。
	public int  getPrepIndex(String subEntityText){
		for(int i = 0; i< prepositionsList.size();i++ ){
			if(subEntityText.equals(prepositionsList.get(i))){
				return i;				
			}
		}	
		return -1;
	}
	//sieve- 6
	public String replaceDiseaseSynonyms(String entityText, List<String>entityTextList){
		//for name containing a disorder term,replaced with its synomyms
		if(!isContain(entityText,entityTextList))
			entityTextList.add(entityText);
		List<String> replace = new ArrayList<String>();
		String tempReturn = null;
		//first change the form of entityText		
		if(entityTextList.size() == 0)
		    tempReturn = replaceDisease(entityText,replace);		
		else{
		  for(int i = 0; i < entityTextList.size(); i++){
			  //
			  String tempEntity = entityTextList.get(i);
			  tempReturn = replaceDisease(tempEntity,replace);
			  if(tempReturn != null)
				  return tempReturn;
		  }
		}
		 for(String tempReplace : replace ){
			 if(!isContain(tempReplace,entityTextList))
			      entityTextList.add(tempReplace);
		  }
		
		return tempReturn;	
	}

	public String replaceDisease(String entityText,List<String>replace){
		String diseaseSynonymsReturn = null;
		boolean isContain = false;
		String tempEntity = null;
		
		for(int i = 0; i < diseaseSynonymsSingularList.size(); i++){
			String tempSynonymsSing = diseaseSynonymsSingularList.get(i);
			if(entityText.contains(tempSynonymsSing)){
				isContain = true;
				if(i==0){
					for(int j = i+1; j <diseaseSynonymsSingularList.size();j++){
						String replaceSynonymsSing = diseaseSynonymsSingularList.get(j);
						tempEntity = entityText.replace(tempSynonymsSing, replaceSynonymsSing);
					
					    diseaseSynonymsReturn = isExactMap(entityText);
					    if( diseaseSynonymsReturn == null)
					    	if(!isContain(tempEntity,replace))
						   replace.add(tempEntity);
					    else
						   return  diseaseSynonymsReturn;				
				}
				//i>1
			}else{
				
				for(int j = 0; j< i; j++){
					String replaceSynonymsSing = diseaseSynonymsSingularList.get(j);
					tempEntity = entityText.replace(tempSynonymsSing, replaceSynonymsSing);
				
				    diseaseSynonymsReturn = isExactMap(tempEntity);
				    if( diseaseSynonymsReturn == null)
				    	if(!isContain(tempEntity,replace))
					       replace.add(tempEntity);
				    else
					   return  diseaseSynonymsReturn;
				}
				for(int j = i+1; j < diseaseSynonymsSingularList.size();j++){
					String replaceSynonymsSing = diseaseSynonymsSingularList.get(j);
					tempEntity = entityText.replace(tempSynonymsSing, replaceSynonymsSing);
				
				    diseaseSynonymsReturn = isExactMap(tempEntity);
				    if( diseaseSynonymsReturn == null)
				    	if(!isContain(tempEntity,replace))
					       replace.add(tempEntity);
				    else
					   return  diseaseSynonymsReturn;
				}
			}
			
		 }
		}
		//isContain = false, 说明diseaseText没有diseaseSynonymsSingularList中元素，
		//再看是否有disorderSynonymsPluralList中的元素。
		if(!isContain){			
			for(int i = 0; i < disorderSynonymsPluralList.size(); i++){
				String tempSynonymsPlural = disorderSynonymsPluralList.get(i);
				if(entityText.contains(tempSynonymsPlural)){
					isContain = true;				
					if(i==0){
						for(int j = i+1; j < disorderSynonymsPluralList.size();j++){
							String replaceSynonymsPlural = disorderSynonymsPluralList.get(j);
							tempEntity = entityText.replace(tempSynonymsPlural, replaceSynonymsPlural);						
						    diseaseSynonymsReturn = isExactMap(tempEntity);
						    if( diseaseSynonymsReturn == null)
						    	if(!isContain(tempEntity,replace))
							       replace.add(tempEntity);
						    else
							   return  diseaseSynonymsReturn;				
					}
				  }else{
					  for(int j = 0; j< i; j++){
							String replaceSynonymsPlural = disorderSynonymsPluralList.get(j);
							tempEntity = entityText.replace(tempSynonymsPlural, replaceSynonymsPlural);
						
						    diseaseSynonymsReturn = isExactMap(tempEntity);
						    if( diseaseSynonymsReturn == null)
						    	if(!isContain(tempEntity,replace))
							       replace.add(tempEntity);
						    else
							   return  diseaseSynonymsReturn;
						}
						for(int j = i+1; j < disorderSynonymsPluralList.size();j++){
							String replaceSynonymsPlural = disorderSynonymsPluralList.get(j);
							tempEntity = entityText.replace(tempSynonymsPlural, replaceSynonymsPlural);
						
						    diseaseSynonymsReturn = isExactMap(tempEntity);
						    if( diseaseSynonymsReturn == null)
						    	if(!isContain(tempEntity,replace))
							       replace.add(tempEntity);
						    else
							   return  diseaseSynonymsReturn;
						}
					  
				  }
					
				
				}				
			}			
		}
		
		
		return diseaseSynonymsReturn;
	}
	//sieve-7
	//tumorSynonyms
	public String replaceTumorSynonyms(String entityText, List<String> entityTextList){
	    String tempReturn = null;
		boolean isSynonym = false;		
		String tempEntity = "";
		List<String> replaceSynonymsList= new ArrayList<String>();
		if(!isContain(entityText,entityTextList))
			entityTextList.add(entityText);		
		for(int i = 0; i < tumorSynonymsList.size(); i++){
			String tempTumorSynonym = tumorSynonymsList.get(i);
			if(entityText.contains(tempTumorSynonym)){
				isSynonym = true;				
			if(i==0){
				for(int j = i+1; j < tumorSynonymsList.size();j++){
					String replaceSynonym = tumorSynonymsList.get(j);
					tempEntity = entityText.replace(tempTumorSynonym, replaceSynonym);						
					tempReturn = isExactMap(tempEntity);
					if( tempReturn == null)
					   if(!isContain(tempEntity,replaceSynonymsList))
						   replaceSynonymsList.add(tempEntity);
					   else
						   return  tempReturn;	
				}
			}
			else{
				for(int j = 0; j < i; j++){
					String replaceSynonym = tumorSynonymsList.get(j);
					tempEntity = entityText.replace(tempTumorSynonym, replaceSynonym);						
					tempReturn = isExactMap(tempEntity);
					if( tempReturn == null)
					   if(!isContain(tempEntity,replaceSynonymsList))
						   replaceSynonymsList.add(tempEntity);
					   else
						   return  tempReturn;	
				}
				for(int j = i+1; j < tumorSynonymsList.size(); j++){
					String replaceSynonym = tumorSynonymsList.get(j);
					tempEntity = entityText.replace(tempTumorSynonym, replaceSynonym);						
					tempReturn = isExactMap(tempEntity);
					if( tempReturn == null)
					   if(!isContain(tempEntity,replaceSynonymsList))
						   replaceSynonymsList.add(tempEntity);
					   else
						   return  tempReturn;	
					}
				}
					
				}
			}
			
		    for(String tempReplace : replaceSynonymsList ){
			   if(!isContain(tempReplace,entityTextList))
				  entityTextList.add(tempReplace);
			 }	
	    return tempReturn;		
			
	}
	
	   public boolean isPrefixAndReplace(String target,String prefixKey,String prefixValue){
		   boolean isPrefix = false;
		   int prefixLength = prefixKey.length();
		   String[] splits = target.split(" ");
		   String tempTarget = "";
		   for(int i = 0; i < splits.length; i++){
			   String split = splits[i];
			   if(split.length() < prefixLength)
				   continue;
			   String tempSub = split.substring(0,prefixLength);
			   if(tempSub.equals(prefixKey)){
				   isPrefix = true;
				   splits[i] = prefixValue + split.substring(prefixLength);
			   }		   
			   
		   }
		   if(isPrefix){
		      for(int i= 0; i< splits.length; i++)
			     tempTarget += splits[i]+" ";
		      target = tempTarget.trim();
		   }
		  return isPrefix;		    
	   }
	   public String isSuffixAndReplace(String target,String suffixKey,String suffixValue){
		   boolean isSuffix = false;
		   String replacedStr = null;
		   int suffixLength = suffixKey.length();
		   String[] splits = target.split(" ");
		   String tempTarget = "";
		   for(int i = 0; i < splits.length; i++){
			   String split = splits[i];
			   if(split.length() < suffixLength)
				   continue;
			   int sl = splits[i].length();
			   int suffixIndex = splits[i].length() - suffixLength;
			   String tempSub = split.substring(suffixIndex);
			   if(tempSub.equals(suffixKey)){
				   isSuffix = true;
				   splits[i] = split.substring(0,suffixIndex) + suffixValue;
			   }			   
		   }
		   if(isSuffix){
		       for(int i = 0; i < splits.length; i++)
			     tempTarget += splits[i] +" ";
		       replacedStr = tempTarget.trim();
		   }
		   return replacedStr;
	   }
	//sieve-8 compareStemmer(entityTextList,idToName)
	public String compareStemmer(List<String> entityTextList, Map<String,String> idToName){
		String tempReturn = null;
		List<String> entityStemmerList = new ArrayList<String>();
		Iterator iter = idToName.entrySet().iterator();
		for(String entityText:entityTextList){
			String entityStemmer = new String();

	   		String[] entityTokens = entityText.split(" ");
	   		//stemmer each token of entity
	   		Stemmer tempStemmer = new Stemmer();
	   		for(int i = 0; i < entityTokens.length; i++){
	   			String entityToken = entityTokens[i];
	   			entityToken = entityToken.toLowerCase();
	   			
	   			tempStemmer.add(entityToken.toCharArray(), entityToken.toCharArray().length);
	   			tempStemmer.stem();
	   			entityStemmer += tempStemmer.toString() + " ";
	   		}
	   		entityStemmer = entityStemmer.trim();
	   		while(iter.hasNext()){
	   			Map.Entry entry = (Map.Entry)iter.next();
	   			String id = (String)entry.getKey();
	   			String name = (String)entry.getValue();
	   			String nameStemmer = new String();
	   			String[] nameTokens = name.split(" ");
	   			for(int j = 0; j< nameTokens.length; j++){
	   				String nameToken = nameTokens[j];
	   				nameToken = nameToken.toLowerCase();
	   				tempStemmer.add(nameToken.toCharArray(),nameToken.toCharArray().length);
	   				tempStemmer.stem();
	   				nameStemmer += tempStemmer + " ";	   				
	   			}
	   			nameStemmer = nameStemmer.trim();
	   			if(entityStemmer.equals(nameStemmer)){
	   				tempReturn = id.substring(id.indexOf(':')+1);
	   			}
	   			if(tempReturn == null)
	   				if(!isContain(entityStemmer,entityStemmerList))
	   				    entityStemmerList.add(entityStemmer);
	   		}	   		
			
		}
		for(String temp : entityStemmerList){
			if(!isContain(temp,entityTextList))
				entityTextList.add(temp);
		}
		return tempReturn;
		
	}
	
	public String partialMatch(String entityText, List<String> entityTextList,TokenizerFactory<CoreLabel> tokenizerFactory) {
		if (!isContain(entityText, entityTextList)) {
			entityTextList.add(entityText);
		}

		int minmin = Integer.MAX_VALUE;
		String minDistanceToMeshId = null;
		preId = null;
		String meshId = null;
		for (String tempEntity : entityTextList) {

			// tokenize entityText
			Tokenizer<CoreLabel> tempTokenize = tokenizerFactory
					.getTokenizer(new StringReader(tempEntity));
			List<CoreLabel> entityTokens = tempTokenize.tokenize();
			int[] minEntityToNameToken = new int[entityTokens.size()];
			Iterator iter = idToNames.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				meshId = (String) entry.getKey();
				
				List<String> names = (List<String>) entry.getValue(); // 找部分匹配
				int allMinEntityToken = 0;
				for (int i = 0; i < entityTokens.size(); i++) {
					String tokenStr = entityTokens.get(i).toString();
					minEntityToNameToken[i] = Integer.MAX_VALUE;
					for (int j = 0; j< names.size(); j++) {
						String name = names.get(j);
						if(name.contains(","))
							name = name.replace(",","");
						if(j == 25)
							System.out.println();
						// tokenize name
						if (name.equals("5 alpha Fluorouracil toxicity"))
							System.out.println();
						if (name.equals("Drug-Related Side Effects and Adverse Reactions"))
							System.out.println();
						int nameToEntityToken = 0;
						nameToEntityToken = EditDistance(name.toLowerCase(),tokenStr.toLowerCase()); //EditDistance(nametoken.toLowerCase(),tokenStr.toLowerCase());//不对name分词
						/*Tokenizer<CoreLabel> tokenizeName = tokenizerFactory
								.getTokenizer(new StringReader(name));
						List<CoreLabel> nameTokens = tokenizeName.tokenize();
						int[] entityToNameToken = new int[nameTokens.size()];
						int sumEntityToNameToken = 0;
						for (int k = 0; k < nameTokens.size(); k++) {
							// compute similarity between tokenStr and nametoken
							String nametoken = nameTokens.get(k).toString();
							entityToNameToken[k] = EditDistance(nametoken.toLowerCase(),tokenStr.toLowerCase());
							sumEntityToNameToken += entityToNameToken[k];
						}*/
						if (nameToEntityToken < minEntityToNameToken[i])
							   minEntityToNameToken[i] = nameToEntityToken;
						    
					}

					// 求出entity所有的token的最小距离和
					allMinEntityToken += minEntityToNameToken[i];
				}
				if (allMinEntityToken < minmin) {
					minmin = allMinEntityToken;
					preId = meshId.substring(meshId.indexOf(':') + 1);
					minDistanceToMeshId = preId;
				}

			}

		}
		return minDistanceToMeshId;
	}
	//计算两个字符串的相似性
	 private static int EditDistance(String source, String target)  
	    {  
	        char[] s=source.toCharArray();  
	        char[] t=target.toCharArray();  
	        int slen=source.length();  
	        int tlen=target.length();  
	        int d[][]=new int[slen+1][tlen+1];  
	        for(int i=0;i<=slen;i++)  
	        {  
	            d[i][0]=i;  
	        }  
	        for(int i=0;i<=tlen;i++)  
	        {  
	            d[0][i]=i;  
	        }  
	        for(int i=1;i<=slen;i++)  
	        {  
	            for(int j=1;j<=tlen;j++)  
	            {  
	                if(s[i-1]==t[j-1])  
	                {  
	                    d[i][j]=d[i-1][j-1];  
	                }else{  
	                    int insert=d[i][j-1]+1;  
	                    int del=d[i-1][j]+1;  
	                    int update=d[i-1][j-1]+1;  
	                    d[i][j]=Math.min(insert, del)>Math.min(del, update)?Math.min(del, update):Math.min(insert, del);  
	                }  
	            }  
	        }  
	        return d[slen][tlen];  
	    }  
	
	 
		
	
}
