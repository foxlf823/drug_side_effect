package utils;  
import java.util.ArrayList;  
import java.util.List;  
  
import javax.xml.parsers.SAXParser;  
import javax.xml.parsers.SAXParserFactory;  
  
import org.xml.sax.Attributes;   
import org.xml.sax.SAXException;  
import org.xml.sax.XMLReader;  
import org.xml.sax.helpers.DefaultHandler;
import utils.CTDdisease;

public class CTDSaxParse {
	public List<CTDdisease> diseaseslist;
	public List<CtdEntry> chemList;
	public String type;
	public CTDSaxParse(String type){
		this.type = type;
		if(type.equals("Disease"))
			diseaseslist = new ArrayList<CTDdisease>();
		else
			chemList = new ArrayList<>();
	}
	public void startCTDSaxParse(String xmlPath)throws Exception{		
		 //1.创建解析工厂  
        SAXParserFactory factory=SAXParserFactory.newInstance();  
        //2.得到解析器  
        SAXParser sp=factory.newSAXParser();  
        //3.得到读取器  
        XMLReader reader=sp.getXMLReader();  
        //4.设置内容处理器  
        if(type.equals("Disease")) {
        	CTDdiseaseHandler handle=new CTDdiseaseHandler();  
            reader.setContentHandler(handle);  
            reader.parse(xmlPath);
    		diseaseslist=handle.getList();  
    	    diseaseslist.iterator();  
        }
		else {
			CtdChemHandler handle = new CtdChemHandler();
			reader.setContentHandler(handle);
			reader.parse(xmlPath);
			chemList = handle.ctdChemicals;
			chemList.iterator();
		}
        
        
	}
}
//把每一对<Row>...</Row>封装为一个CTDdisease对象
class CTDdiseaseHandler extends DefaultHandler{
	 private List<CTDdisease> ctdDiseases;  
	 public List<CTDdisease> getList() {  
	    return ctdDiseases;  
	 }	  
	 private String currentTag;  
	 private CTDdisease ctdDisease;
	 private static final String TAG = "CTDdiseaseHandler";
	 public void startDocument() throws SAXException {
		 ctdDiseases=new ArrayList<CTDdisease>();
//		 System.out.println(TAG +"文档解析开始");

	 }	
	
	 /**
	 * 开始处理元素时触发该方法
     * Param     :    uri          命名空间
　        *                localName    不带命名空间前缀的标签名
　　   *                qName        带命名空间前缀的标签名
　　   *                attributes   属性集合

     * 执行回调方法characters(char[] ch, int start, int length)，这是一个循环的过程 
     */ 
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		currentTag = qName;
		 if(currentTag.equals("Row")) {
			ctdDisease = new CTDdisease();
		 }
//		 System.out.println("正在解析-->" + qName);
	}
	//这里是将之间的值加入到currentValue
	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		if((currentTag != null) && (ctdDisease != null)) {
		    String currentValues = new String(ch, start, length);   //获取当前标签里的内容
		    if("DiseaseName".equals(currentTag)){          	
        	   ctdDisease.setDiseaseName(currentValues);
            } 
		    if("DiseaseID".equals(currentTag)){
	        	ctdDisease.setId(currentValues);       	
	        }
		    if("AltDiseaseIDs".equals(currentTag)){
	        	
	        	List<String> altDiseaseIds = new ArrayList<String>();   
	        	String[] alts = currentValues.split("\\|");
	        	if(alts.length > 1){
	        	for(int i = 0; i< alts.length; i++){
	        		altDiseaseIds.add(alts[i]);        			
	        		}
	        	}
	        	else{
	        		altDiseaseIds.add(currentValues);
	        	}
	        	ctdDisease.setAltDiseaseIds(altDiseaseIds);
	        } 
		    if("ParentIDs".equals(currentTag)){
	        	
	        	List<String> parentIds = new ArrayList<String>();        
	        	String[] ids = currentValues.split("\\|");
	        	if(ids.length >1){
	        		for(int i = 0; i< ids.length ; i++){
	        			parentIds.add(ids[i]);        			
	        		}
	        	}
	        	else{
	        		parentIds.add(currentValues);
	        	}
	        	ctdDisease.setParentIds(parentIds);
	        } 
	        if("Synonyms".equals(currentTag)){
	        	
	        	List<String> synonyms = new ArrayList<String>();        	
	        	String[] ids = currentValues.split("\\|");
	        	if(ids.length > 1){
	        		for(int i = 0; i< ids.length; i++){
	        			/*if(ids[i].indexOf(",") != -1)
	        				ids[i] = ids[i].replace(",", "");*/
	        			synonyms.add(ids[i]);        			
	        		}
	        	}
	        	else{
	        		synonyms.add(currentValues);
	        	}
	        	
	        	ctdDisease.setSynonyms(synonyms);
	        } 
		}
		currentTag = null;
//		System.out.println("解析内容-->"+new String(ch, start, length));       
        
        
	}	
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		 
	   if(qName.equals("Row") && ctdDisease != null) {
			ctdDiseases.add(ctdDisease);
			ctdDisease = null;		
		}	
	   currentTag = null;
//	   System.out.println("解析完毕-->" + qName);

	}	
	@Override
	public void endDocument() throws SAXException {
		System.out.println("CDTdisease's Num: "+ctdDiseases.size());
	}
	
	

}

class CtdChemHandler extends DefaultHandler{
	public	 List<CtdEntry> ctdChemicals;  
	 	  
	 private String currentTag;  
	 private CtdEntry ctdChemical;
	 private static final String TAG = "CtdChemHandler";
	 
	 public void startDocument() throws SAXException {
		 ctdChemicals=new ArrayList<CtdEntry>();

	 }
	 
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		currentTag = qName;
		 if(currentTag.equals("Row")) {
			 ctdChemical = new CtdEntry();
			 ctdChemical.type = "Chemical";
		 }

	}
	
	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		if((currentTag != null) && (ctdChemical != null)) {
		    String currentValues = new String(ch, start, length);   //获取当前标签里的内容
		    if("ChemicalName".equals(currentTag)){          	
		    	ctdChemical.name = currentValues;
            } 
		    if("ChemicalID".equals(currentTag)){
		    	ctdChemical.id = currentValues;       	
	        }
		    
		    if("ParentIDs".equals(currentTag)){
	        	
	        	List<String> parentIds = new ArrayList<String>();        
	        	String[] ids = currentValues.split("\\|");
	        	if(ids.length >1){
	        		for(int i = 0; i< ids.length ; i++){
	        			parentIds.add(ids[i]);        			
	        		}
	        	}
	        	else{
	        		parentIds.add(currentValues);
	        	}
	        	ctdChemical.parentIds = parentIds;
	        } 
	        if("Synonyms".equals(currentTag)){
	        	
	        	List<String> synonyms = new ArrayList<String>();        	
	        	String[] ids = currentValues.split("\\|");
	        	if(ids.length > 1){
	        		for(int i = 0; i< ids.length; i++){
	        			synonyms.add(ids[i]);        			
	        		}
	        	}
	        	else{
	        		synonyms.add(currentValues);
	        	}
	        	
	        	ctdChemical.parentIds = synonyms;
	        } 
		}
		currentTag = null;
   
	}	
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		 
	   if(qName.equals("Row") && ctdChemical != null) {
			ctdChemicals.add(ctdChemical);
			ctdChemical = null;		
		}	
	   currentTag = null;
	}	
	@Override
	public void endDocument() throws SAXException {
		System.out.println("CTD_chemicals number: "+ctdChemicals.size());
	}
}
