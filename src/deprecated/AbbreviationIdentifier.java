package deprecated;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AbbreviationIdentifier {	
	public AbbreviationIdentifier(){
		
	}
	public Map<String, String> getAbbreviations(String file,String id, String text) throws IOException {
		//String filePath = "E:/biomedicine/BioTrack-3/NCBI Disease Corpus/DNorm-0.0/DNorm-0.0.6/data/abbreviations.tsv";
		// Return abbreviations found
		
		Map<String, String> abbreviations = new HashMap<String, String>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
		boolean isDocument = false;
		String line = reader.readLine();
	    while (line != null) {				
	      if (line.length() > 0) {
		    if (!line.startsWith("  ")) {
		    	isDocument = false;
				String[] split = line.trim().split("\\t");
				if (split.length == 2) {
					if (id.equals(split[0].trim())){
						isDocument = true;
					}
					else if (abbreviations.size()!= 0){
						break;
					}
				}
		    }else if(isDocument){
		    	String[] split = line.trim().split("\\|");
				if (split.length == 3){
					abbreviations.put(split[0], split[1]);						
				} else{
					System.out.println("the SF-LF format is wrong");
				}
		    }
		  }
	      line = reader.readLine();	
		}												
		reader.close();

		return abbreviations;
	}
}
