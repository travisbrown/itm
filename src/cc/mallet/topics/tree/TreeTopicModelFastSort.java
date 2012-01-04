package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

public class TreeTopicModelFastSort extends TreeTopicModelFast {
	
	int TOPIC_BITS = 16;
	
	public class ValueComparator implements Comparator {
		HashMap<Integer, Integer> map;
		Comparator comparator;
		public ValueComparator(HashMap<Integer, Integer> map) {
			this.map = map;
			comparator = Ordering.natural().reverse().onResultOf(Functions.forMap(map)).compound(Ordering.natural());
		}
		public int compare(Object a, Object b) {
			return comparator.compare(a, b);
		}
	}
	
	public TreeTopicModelFastSort(int numTopics, Random random) {
		super(numTopics, random);
	}
	
	private void updatePathMaskedCountSort(int path, int topic) {
		TopicTreeWalk tw = this.traversals.get(topic);
		int ww = this.getWordFromPath(path);
		TIntArrayList path_nodes = this.wordPaths.get(ww, path);
		int leaf_node = path_nodes.get(path_nodes.size() - 1);
		int original_count = tw.getNodeCount(leaf_node);
		
		int shift_count = this.INTBITS;
		int count = this.maxDepth - 1;
		int val = 0;
		boolean flag = false;
		
		// note root is not included here
		// if count of a node in the path is larger than 0, denote as "1"
		// else use "0"
		for(int nn = 1; nn < path_nodes.size(); nn++) {
			int node = path_nodes.get(nn);
			shift_count--;
			count--;
			if (tw.getNodeCount(node) > 0) {
				flag = true;
				val += 1 << shift_count;
			}
		}
		
		// if a path is shorter than tree depth, fill in "1"
		// should we fit in "0" ???
		while (flag && count > 0) {
			shift_count--;
			val += 1 << shift_count;
			count--;
		}
		
		// plus the original count
		val += original_count;
		if (val > 0) {
			int key = (topic << TOPIC_BITS) + path;
			TreeMap<Integer, Integer> treemap = this.nonZeroPathsSorted.get(ww);
			ValueComparator comparator = (ValueComparator)treemap.comparator();
			HashMap<Integer, Integer> map = comparator.map;
			
			map.put(key, val);
			treemap.put(key, val);
			
		} else if (val == 0) {
			int key = (topic << TOPIC_BITS) + path;
			TreeMap<Integer, Integer> treemap = this.nonZeroPathsSorted.get(ww);
			ValueComparator comparator = (ValueComparator)treemap.comparator();
			Map map = comparator.map;
			treemap.remove(key);
			map.remove(key);
		}

	}
	
	public void updateParams() {	
		super.updateParams();
		for(int ww : this.nonZeroPaths.keys()) {
			HashMap<Integer, Integer> tmpMap = new HashMap<Integer, Integer>();
			HIntIntIntHashMap nonzeros = this.nonZeroPaths.get(ww);
			for(int tt : nonzeros.getKey1Set()) {
				int[] paths = nonzeros.get(tt).keys();
				for (int pp = 0; pp < paths.length; pp++) {
					int path = paths[pp];
					int key = (tt << TOPIC_BITS) + path;
					int val = nonzeros.get(tt, path);
					tmpMap.put(key, val);
				}
			}
			ValueComparator comparator = new ValueComparator(tmpMap);
			TreeMap<Integer, Integer> sorted_map = new TreeMap<Integer, Integer>(comparator);
			sorted_map.putAll(tmpMap);
			this.nonZeroPathsSorted.put(ww, sorted_map);
			
//			System.out.println("word " + ww);
//			for(int key : this.nonZeroPathsSorted.get(ww).keySet()) {
//				System.out.println(key + " " + this.nonZeroPathsSorted.get(ww).get(key));
//			}
		}
	}
	
	public void changeCount(int topic, int word, int path_index, int delta) {
		TopicTreeWalk tw = this.traversals.get(topic);
		TIntArrayList path_nodes = this.wordPaths.get(word, path_index);
		
		// for affected paths, firstly remove the old values
		// do not consider the root
		for(int nn = 1; nn < path_nodes.size() - 1; nn++) {
			int node = path_nodes.get(nn);
			double tmp = this.betaSum.get(node) + tw.getNodeCount(node);
			tmp = 1 / tmp;
			TIntArrayList paths = this.nodeToPath.get(node);
			updateNormalizer(topic, paths, tmp);
		}
		
		// change the count for each edge per topic
		// return the node index whose count is changed from 0 or to 0
		int[] affected_nodes = tw.changeCount(path_nodes, delta);
		// change path count
		int key = (topic << TOPIC_BITS) + path_index;
		TreeMap<Integer, Integer> treemap = this.nonZeroPathsSorted.get(word);
		ValueComparator comparator = (ValueComparator)treemap.comparator();
		HashMap<Integer, Integer> map = comparator.map;
		if (map.containsKey(key)) {
			TreeTopicSampler.myAssert(treemap.containsKey(key), "Error!");
			//System.out.println(map.get(key) + " " + key);
			int value = treemap.get(key);
			
			treemap.remove(key);
			map.remove(key);
			
			value += delta;
			map.put(key, value);
			treemap.put(key, value);

		} else {
			map.put(key, delta);
			treemap.put(key, delta);
		}
		
		// if necessary, change the path mask of the affected nodes
		if (affected_nodes != null && affected_nodes.length > 0) {
			int[] affected_paths = this.findAffectedPaths(affected_nodes);
			for(int ii = 0; ii < affected_paths.length; ii++) {
				this.updatePathMaskedCountSort(affected_paths[ii], topic);
			}
		}
		
		// for affected paths, update the normalizer
		for(int nn = 1; nn < path_nodes.size() - 1; nn++) {
			int node = path_nodes.get(nn);
			double tmp = this.betaSum.get(node) + tw.getNodeCount(node);
			TIntArrayList paths = this.nodeToPath.get(node);
			updateNormalizer(topic, paths, tmp);
		}
		
		// update the root normalizer
		double val = this.betaSum.get(root) + tw.getNodeCount(root);
		this.rootNormalizer.put(topic, val);
	}
	
	/**
	 * This function computes the topic term bucket.
	 */
	public double computeTopicTerm(double[] alpha, TIntIntHashMap local_topic_counts, int word, ArrayList<double[]> dict) {
		double norm = 0.0;
		TreeMap<Integer, Integer> nonzeros = this.nonZeroPathsSorted.get(word);
		
		// Notice only the nonzero paths are considered
		for(int key : nonzeros.keySet()) {
			int tt = key >> TOPIC_BITS;
			int pp = key - (tt << TOPIC_BITS);

			double topic_alpha = alpha[tt];
			int topic_count = local_topic_counts.get(tt);

			double val = this.getObservation(tt, word, pp);
			val *= (topic_alpha + topic_count);
			val /= this.getNormalizer(tt, pp);
			//dict.put(tt, pp, val);
			double[] tmp = {tt, pp, val};
			dict.add(tmp);
			norm += val;
		}
		return norm;
	}
}
