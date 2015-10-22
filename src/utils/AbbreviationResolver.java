package utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Iterator;

public class AbbreviationResolver {
     /**因为abbreviations的第一个String表示是documentId号，可能同一个ID号中有多个缩写，所以若abbreviations类型为
	*Map<String, Map<String, String>>,同一个id号，只改变Map<String, String>里面数组的值。
	*所以对abbreviations赋值时，只能用createAbbreviation(String pmid, String shortForm, String longForm)方法。
	*或者把abbreviations类型直接改为Map<String, List<Map<String, String>>>,一个documentId可以对应多个map对。
	*/
	private Map<String, Map<String, String>> abbreviations;

	public AbbreviationResolver() {
		abbreviations = new HashMap<String,Map<String, String>>();
	}
//load Abbreviations ,the format of file is documentId,short-form and long-Form. Every piece is separated by "\t".
	public void loadAbbreviations(String filename) {
		//abbreviations = new HashMap<String,Map<String, String>>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] split = line.split("\\t");
					createAbbreviation(split[0], split[1], split[2]);
				}
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}
	// load Abbreviations from a fileSample
	public void loadAbbreFromFile(String fileName){
		//abbreviations = new HashMap<String,Map<String,String>>();
		try{
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName),"UTF-8"));
			Map<String, String> abbreviation = new HashMap<String, String>();
			String documentId = null;
			String line = reader.readLine();
			while (line != null) {				
				if (line.length() > 0) {
					if (!line.startsWith("  ")) {
						/**注意：两个hashMap()变量直接用"="赋值，一个变量改变会影响另一个，因为是地址引用。
						 * 不要把mp2 的值赋给 mp1，java 的 = 号是句柄赋值，你把 mp1 = mp2;后，两个都指向同一个值了，mp1 改变 mp2 也会改变。
						 * 所以你要两个互不干扰，不能用 = 赋值，只能把里面的东西拿出来，再放到另一个里面。 
						 */
											
						if (!abbreviation.isEmpty()){
							Iterator it = abbreviation.entrySet().iterator();
					    	while(it.hasNext()){
					    		Map.Entry entry = (Map.Entry) it.next();
					    		String sf = (String)entry.getKey();
					    		String lf = (String)entry.getValue();			
					    		createAbbreviation(documentId,sf,lf);
							}
							abbreviation.clear();
						}
						String[] split = line.split("\\t");
						if (split.length == 2) {
							documentId = split[0];							
						}						
						else{
							System.out.println("the text format is wrong");
						}							
					}else{
						String[] split = line.split("\\|");
						if (split.length == 3){
							abbreviation.put(split[0], split[1]);						
						} else{
							System.out.println("the SF-LF format is wrong");
						}						
					}
				}
				line = reader.readLine();
			}
			
			
		}catch(IOException e){
			throw new RuntimeException(e);
		}
	}

	private void createAbbreviation(String pmid, String shortForm, String longForm) {
		Map<String, String> abbreviation = abbreviations.get(pmid);
		if (abbreviation == null) {
			abbreviation = new HashMap<String, String>();
			abbreviations.put(pmid, abbreviation);
		}
		if (abbreviation.containsKey(shortForm) && !abbreviation.get(shortForm).equals(longForm))
			throw new IllegalArgumentException();
		abbreviation.put(shortForm, longForm);//abbreviation的值改变，同时abbreviations值也变，hashmap是地址引用。
	}

	public String expandAbbreviations(String documentId, String lookupText) {
		return expandAbbreviations(lookupText, abbreviations.get(documentId));
	}
	public static String expandAbbreviations(String lookupText, Map<String, String> abbreviationMap) {
		if (abbreviationMap == null)
			return lookupText;
		for (String abbreviation : abbreviationMap.keySet()) {
			if (lookupText.contains(abbreviation)) {
				String replacement = abbreviationMap.get(abbreviation);
				String updated = null;
				//当使用String.replaceAll(String regex, String replacement) 方法时，
				//若replacement中包含'/'或'$'字符时，该方法会抛出illgalArgumentException异常。
				if (lookupText.contains(replacement)) {
					// Handles mentions like "von Hippel-Lindau (VHL) disease"
					updated = lookupText.replaceAll("\\(?\\b" + Pattern.quote(abbreviation) + "\\b\\)?", "");
				} else {
					//若在replacement中确实需要包含'/'或'$'字符，	则可用语句 Matcher.quoteReplacement(replacement)  进行转义后再使用。
					updated = lookupText.replaceAll("\\(?\\b" + Pattern.quote(abbreviation) + "\\b\\)?", Matcher.quoteReplacement(replacement));
				}
				if (!updated.equals(lookupText)) {
					 System.out.println("Before:\t" + lookupText);
					 System.out.println("After :\t" + updated);
					 System.out.println();
					lookupText = updated;
				}
			}
		}
		return lookupText;
	}
	public Map<String, Map<String, String>> getAbbreviations(){
		if(abbreviations.isEmpty())
			return null;
		return abbreviations;
	}
	public Map<String,String> getAbbreviation(String documentId){
		Map<String,String> abbreviationMap = abbreviations.get(documentId);
		if(abbreviationMap == null)
			return null;
		return abbreviationMap;
	}
	public void writeAbberToFile(String writeAbbreviationsFile){
		try{
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(writeAbbreviationsFile),"UTF-8"));
			 if(getAbbreviations()!= null){
		    	 Iterator iter = getAbbreviations().entrySet().iterator();
		    	 Map<String,String> sfAndLf = new HashMap<String,String>();
		    	 while (iter.hasNext()) { 
		    	   Map.Entry entry = (Map.Entry) iter.next(); 
		    	   String key =(String) entry.getKey(); 
		    	   sfAndLf = (Map)entry.getValue(); 
		    	   Iterator it = sfAndLf.entrySet().iterator();
		    	   while(it.hasNext()){
		    		   Map.Entry ent = (Map.Entry) it.next();
		    		   Object sf = ent.getKey();
		    		   Object lf = ent.getValue();
		    		   bw.write(key +"\t"+ (String)sf +"\t"+(String)lf);
		    		   bw.flush();
		    		   bw.write("\n");
		    	   }		    	   
		    	 } 
		     }
			bw.close();
			
		}catch(IOException e){
			e.printStackTrace();
		}
	}
}
