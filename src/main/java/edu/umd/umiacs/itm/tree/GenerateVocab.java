package edu.umd.umiacs.itm.tree;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TObjectIntHashMap;
import edu.umd.umiacs.itm.tree.TreeTopicSamplerHashD.DocData;
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
	public static void genVocab_old(InstanceList data, String vocab) {
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
	
	public static void genVocab(InstanceList data, String vocab) {
		
		class WordCount implements Comparable {
			String word;
			int count;
			public WordCount (String word, int count) { this.word = word; this.count = count; }
			public final int compareTo (Object o2) {
				if (count > ((WordCount)o2).count)
					return -1;
				else if (count == ((WordCount)o2).count)
					return 0;
				else return 1;
			}
		}
		
		try{
			TObjectIntHashMap<String> freq = new TObjectIntHashMap<String> ();
			Alphabet alphabet = data.getAlphabet();
			for(int ii = 0; ii < alphabet.size(); ii++) {
				String word = alphabet.lookupObject(ii).toString();
				freq.put(word, 0);
			}

			for (Instance instance : data) {
				FeatureSequence original_tokens = (FeatureSequence) instance.getData();
				for (int jj = 0; jj < original_tokens.getLength(); jj++) {
					String word = (String) original_tokens.getObjectAtPosition(jj);
					freq.adjustValue(word, 1);
				}
			}
			
			WordCount[] array = new WordCount[freq.keys().length];
			int index = -1;
			for(Object o : freq.keys()) {
				String word = (String)o;
				int count = freq.get(word);
				index++;
				array[index] = new WordCount(word, count);
			}
			
			Arrays.sort(array);
			
			
			File file = new File(vocab);
			PrintStream out = new PrintStream (file);
			
			// language_id is fixed now, but can be extended to 
			// multiple languages
			int language_id = 0;
			for(int ii = 0; ii < array.length; ii++) {
				out.println(language_id + "\t" + array[ii].word + "\t" + array[ii].count);
			}
			out.close();
			
		} catch (IOException e) {
			e.getMessage();
		}
	}
}
