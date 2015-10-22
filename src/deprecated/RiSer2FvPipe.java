package deprecated;

import java.io.File;
import java.util.ArrayList;

import cn.fox.mallet.FeatureVectorMaker;
import cn.fox.utils.ObjectSerializer;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;

public class RiSer2FvPipe extends Pipe {

	private static final long serialVersionUID = -420825604172267001L;
	public RiSer2FvPipe() {
		super (new Alphabet(), new LabelAlphabet());
	}
	
	@Override
	public Instance pipe (Instance carrier)
    {
		File inputFile = (File)carrier.getData();  
		RelationInstance ri = (RelationInstance)ObjectSerializer.readObjectFromFile(inputFile.getAbsolutePath());
		Alphabet features = getDataAlphabet(); 
		LabelAlphabet labels = (LabelAlphabet)getTargetAlphabet();
      
        FeatureVector vector = FeatureVectorMaker.make(features, ri.map);
        carrier.setData(vector);
        
        Label label = labels.lookupLabel(ri.label);
        carrier.setTarget(label);
   
        return carrier;
    }
}
