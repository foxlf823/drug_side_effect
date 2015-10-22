package utils;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.Entity;

public class BiocTxtParse {	
	String filePath;
	ArrayList<BiocDocument> documents;
	public BiocTxtParse(String filePath){
		super();
		this.filePath = filePath;
		documents = new ArrayList<BiocDocument> ();
	}
	public ArrayList<BiocDocument> parseBiocTxtFile(){		
		try{
			BufferedReader inFile = new BufferedReader(new InputStreamReader(new FileInputStream(filePath),"utf-8"));
			String currentLine = null;				
			String title = null;
			String text = null;
			String id = null;			
			while ((currentLine = inFile.readLine())!= null) {				
				currentLine = currentLine.trim();
				String[] splits = currentLine.split("\\|");				
				if(splits.length != 2 && splits.length != 3){//表示一个document已读完
					continue;
				}
				if(title == null){
					id = splits[0];					
					if(splits[1].equals("t"))
						title = splits[2];	
				}
				if(splits[1].equals("a")){
					text = splits[2];
							
				}
				if((title != null)&& (text!=null)){
					BiocDocument biocDocument = new BiocDocument(id,title,text);					
					documents.add(biocDocument);
					title = null;
					text = null;
				}
			}		
		inFile.close();			
		}catch(Exception e){
			e.printStackTrace();
		}		
		return documents;		
	}

	public void  setEntities(){
		ArrayList<BiocDocument> biocDocuments = new ArrayList<BiocDocument>();
		try{
		String line = null;
		BufferedReader dataFile = new BufferedReader(new InputStreamReader(new FileInputStream(filePath),"UTF-8"));
		String id = null;
		BiocDocument document =null ;
		
	
		while((line = dataFile.readLine())!= null){	
			String[] titleAndAbstract = line.trim().split("\\|");				
			if(titleAndAbstract.length == 3 && titleAndAbstract[1].equals("a")){//表示一个document已读完
				id = titleAndAbstract[0];
				if(document != null)
				   biocDocuments.add(document);
				document = getDocumentById(id);
				continue;
			}			
		    String[] splits = line.trim().split("\\t");		    
			if(splits.length < 6)
				continue;	
			
			int start = Integer.parseInt(splits[1]); // 字符型转换为整型
			int end = Integer.parseInt(splits[2]);
			String text = splits[3];
			/*if (!splits[3].equals(removePunctuation(text)))
				throw new IllegalArgumentException("2");
			if (!text.equals(text.trim()))
				throw new IllegalArgumentException("3");*/
			String type = splits[4];
			if(!type.equals("Disease"))//读NCBI语料时，不需要这句
				continue;
			String mesh = splits[5].trim().replaceAll("\\*", "");
			Entity entity = new Entity(id,type,start,text,mesh);
			if(document != null)
				document.entities.add(entity);
			else
				System.out.println("wrong document");
		 }
		 biocDocuments.add(document);//最后一次document加上
		}catch(Exception e){
			e.printStackTrace();
		}
		
		this.documents = biocDocuments;
				
	}

	public  String removePunctuation(String text) {
		String remove = "\"";
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (remove.indexOf(c) == -1) {
				sb = sb.append(c);
			} else {
				sb.append(" ");
			}
		}
		return sb.toString();
	}
	public void writerNCBIToPlainSample(String plainPath)throws IOException{
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(plainPath),"UTF-8"));
		
		for(BiocDocument document:documents){
			
			String text = document.id +"\t"+document.title+" "+document.abstractt;
			//writer text to NCBI_Sample.txt file,each line represent a document
				bw.write(text);
				bw.write("\n");
				bw.flush();			
		}
		bw.close();
	}
	public BiocDocument getDocumentById(String documentId){
		
		for(BiocDocument document :documents){
	        if(documentId.equals(document.id)){
			  return document;			  
             }
		}
		return null;
	}
}

