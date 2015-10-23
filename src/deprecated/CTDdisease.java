package deprecated;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class CTDdisease {
	String diseaseName;
	String diseaseId;
	List<String> altDiseaseIds;
	List<String> parentIds;
	List<String> synonyms;
public 	Map<String, List<String>>idToSynonyms;

	public CTDdisease(){
		diseaseName = null;
		diseaseId = null;
		altDiseaseIds = new ArrayList<String>();
		parentIds = new ArrayList<String>();
		synonyms = new ArrayList<String>();
		idToSynonyms = new HashMap<String, List<String>>();
		
	}
	public CTDdisease(String name,String id){
		diseaseName = name;
		diseaseId = id;
		altDiseaseIds = new ArrayList<String>();
		parentIds = new ArrayList<String>();
		synonyms = new ArrayList<String>();	
		idToSynonyms = new HashMap<String, List<String>>();
		
	}
	public void setDiseaseName(String name){
		diseaseName = name;
	}
	public void setId(String id){
		diseaseId = id;
	}
	public void setAltDiseaseIds(List<String> ids){
		for(String temp:ids){
			altDiseaseIds.add(temp);
		}
	}
	public void setParentIds(List<String>ids){
		for(String temp:ids){
			parentIds.add(temp);
		}
	}
	public void setSynonyms(List<String> synonymss){
		for(String temp:synonymss){
			synonyms.add(temp);
		}
	}
	public void setSynonymsMap(){
		List<String> name = new ArrayList<String>();
		if(diseaseName != null)
		   name.add(diseaseName);
		if(!synonyms.isEmpty()){
			name.addAll(synonyms);		
		}
		idToSynonyms.put(diseaseId, synonyms);		
	}
	
	public String getDiseaseName(){
		return diseaseName;
	}
	public String getDiseaseId(){
		return diseaseId;
	}
	public List<String> getAltDiseaseIds(){
		return altDiseaseIds;
	}
	public List<String> getParentIds(){
		return parentIds;
	}
	public List<String> getSynonyms(){
		return synonyms;
	}

}
