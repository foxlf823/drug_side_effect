package corpus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class PostProcess {

	public static void main(String[] args) throws Exception {
		/*
		 * Since the data is asked by "xml" format and I forget to transform the following characters
		 * 	&(逻辑与)  &amp;        
			<(小于)    &lt;        
			>(大于)    &gt;        
			"(双引号)  &quot;      
			'(单引号)  &apos;
		 */
		
		String input = "F:/biomedical resource/cdr_ext_corpus/data";
		File fInputDir = new File(input);
		String output = "F:/biomedical resource/cdr_ext_corpus/data1";
		
		for(File file:fInputDir.listFiles()) {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(output+"/"+file.getName()), "utf-8");
			
			String all = "";
			String line = null;
			while((line=br.readLine()) != null) {
				all += line+"\n"; 
			}
			br.close();
			
			all = all.replaceAll("&amp;", "&");
			all = all.replaceAll("&lt;", "<");
			all = all.replaceAll("&gt;", ">");           
			all = all.replaceAll("&quot;", "\"");        
			all = all.replaceAll("&apos;", "'");  
			
			osw.write(all);
			
			osw.close();
			
		}

	}

}
