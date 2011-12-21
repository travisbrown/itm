package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectIntHashMap;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.Randoms;

public class TreeTopicSamplerNaive extends TreeTopicSampler {
		
	public TreeTopicSamplerNaive (int numberOfTopics, double alphaSum) {
		this (numberOfTopics, alphaSum, 0);
	}
		
	public TreeTopicSamplerNaive (int numberOfTopics, double alphaSum, int seed) {
		super (numberOfTopics, alphaSum, seed);
		this.topics = new TreeTopicModelNaive(this.numTopics, this.random);
	}

	public void sampleDoc(int doc_id){
		DocData doc = this.data.get(doc_id);
		//System.out.println("doc " + doc_id);
		
		for(int ii = 0; ii < doc.tokens.size(); ii++) {	
			//int word = doc.tokens.getIndexAtPosition(ii);
			int word = doc.tokens.get(ii);
			
			this.changeTopic(doc_id, ii, word, -1, -1);
			HIntIntDoubleHashMap topic_term_score = new HIntIntDoubleHashMap();
			double norm = this.topics.computeTopicTerm(this.alpha, doc.topicCounts, word, topic_term_score);
			//System.out.println(norm);
			
			int new_topic = -1;
			int new_path = -1;
			
			double sample = this.random.nextDouble();
			//double sample = 0.8;
			sample *= norm;
			
			int[] topic_set = topic_term_score.getKey1Set();
			for (int tt : topic_set) {
				int[] path_set = topic_term_score.get(tt).keys();
				for (int pp : path_set) {
					double val = topic_term_score.get(tt, pp);
					//System.out.println(tt + " " + pp + " " + val);
					sample -= val;
					if (sample <= 0.0) {
						new_topic = tt;
						new_path = pp;
						break;
					}
				}
				if (new_topic >= 0) {
					break;
				}
			}
			
			myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling!");
			
			this.changeTopic(doc_id, ii, word, new_topic, new_path);
		}
	}
	
}
