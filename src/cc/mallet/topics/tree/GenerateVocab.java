package cc.mallet.topics.tree;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import cc.mallet.topics.tree.TreeTopicSampler.DocData;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public class GenerateVocab {
	
	public static void genVocab(InstanceList data, String vocab) {
		try{
			File file = new File(vocab);
			PrintStream out = new PrintStream (file);
			
			int language_id = 0;
			Alphabet alphabet = data.getAlphabet();
			for(int ii = 0; ii < alphabet.size(); ii++) {
				String word = alphabet.lookupObject(ii).toString();
				System.out.println(word);
				out.println(language_id + "\t" + word);
			}
			out.close();
		} catch (IOException e) {
			e.getMessage();
		}

	}
}
