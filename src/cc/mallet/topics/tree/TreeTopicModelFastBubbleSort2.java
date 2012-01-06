package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TreeTopicModelFastBubbleSort2  extends TreeTopicModelFast {
	
	public TreeTopicModelFastBubbleSort2(int numTopics, Random random) {
		super(numTopics, random);
	}
		
	/**
	 * This function computes the topic term bucket.
	 */
	public double computeTopicTerm(double[] alpha, TIntIntHashMap local_topic_counts, int word, ArrayList<double[]> dict) {
		double norm = 0.0;
		HIntIntIntHashMap nonzeros = this.nonZeroPaths.get(word);
		
		// Notice only the nonzero paths are considered
		//for(int tt = 0; tt < this.numTopics; tt++) {
		for(int tt : nonzeros.getKey1Set()) {
			double topic_alpha = alpha[tt];
			int topic_count = local_topic_counts.get(tt);
			int[] paths = nonzeros.get(tt).keys();
			for (int pp = 0; pp < paths.length; pp++) {
				int path = paths[pp];
				double val = this.getObservation(tt, word, path);
				val *= (topic_alpha + topic_count);
				val /= this.getNormalizer(tt, path);
				norm += val;
				
				double[] result = {tt, path, val};
				//dict.add(result);
				
				int index = dict.size();
				for(int jj = 0; jj < dict.size(); jj++) {
					double[] find = dict.get(jj);
					//System.out.println(find[2] + " " + val);
					if(val >= find[2]) {
						index = jj;
						break;
					}
				}
				dict.add(index, result);
			}
		}
		return norm;
	}
}
