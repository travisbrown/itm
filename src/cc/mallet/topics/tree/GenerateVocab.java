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


/**
 * This class generates the vocab file from mallet input.
 * Main entrance: genVocab()
 * Author: Yuening Hu
 */
public class GenerateVocab {
	
	/**
	 * After the preprocessing of mallet, a vocab is needed to generate
	 * the prior tree. So this function simply read in the alphabet
	 * of the training data and output as the vocab.
	 * Currently, the language_id is fixed.
	 */
	public static void genVocab(InstanceList data, String vocab) {
		try{
			File file = new File(vocab);
			PrintStream out = new PrintStream (file);
			
			// language_id is fixed now, but can be extended to 
			// multiple languages
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
