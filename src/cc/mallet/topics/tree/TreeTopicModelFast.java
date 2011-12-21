package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectIterator;

import java.util.Random;

public class TreeTopicModelFast extends TreeTopicModel {
	
	int INTBITS = 31;

	public TreeTopicModelFast(int numTopics, Random random) {
		super(numTopics, random);
		this.normalizer = new HIntIntDoubleHashMap ();
		this.rootNormalizer = new TIntDoubleHashMap ();
	}
	
	private void updatePathMaskedCount(int path, int topic) {
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
		for(int nn = 1; nn < path_nodes.size(); nn++) {
			int node = path_nodes.get(nn);
			shift_count--;
			count--;
			if (tw.getNodeCount(node) > 0) {
				flag = true;
				val += 1 << shift_count;
			}
		}
		
		while (flag && count > 0) {
			shift_count--;
			val += 1 << shift_count;
			count--;
		}
		
		val += original_count;
		if (val > 0) {
			this.nonZeroPaths.get(ww).put(topic, path, val);
		} else if (val == 0) {
			if (this.nonZeroPaths.get(ww).get(topic) != null) {
				this.nonZeroPaths.get(ww).removeKey2(topic, path);
				if (this.nonZeroPaths.get(ww).get(topic).size() == 0) {
					this.nonZeroPaths.get(ww).removeKey1(topic);
				}				
			}
		}

		//System.out.println(original_count + " " + this.nonZeroPaths.get(ww).get(topic, path));
	}
		
	private void computeNormalizer(int topic) {
		TopicTreeWalk tw = this.traversals.get(topic);
		double val = this.betaSum.get(root) + tw.getNodeCount(root);
		this.rootNormalizer.put(topic, val);
		//System.out.println("Topic " + topic + " root normalizer " + this.rootNormalizer.get(topic));
		
		for(int pp = 0; pp < this.getPathNum(); pp++) {
			int ww = this.getWordFromPath(pp);
			val = this.computeNormalizerPath(topic, ww, pp);
			this.normalizer.put(topic, pp, val);
			//System.out.println("Topic " + topic + " Path " + pp  + " normalizer " + this.normalizer.get(topic, pp));
		}
	}
	
	private double computeNormalizerPath(int topic, int word, int path) {
		TopicTreeWalk tw = this.traversals.get(topic);
		TIntArrayList path_nodes = this.wordPaths.get(word, path);
		
		double norm = 1.0;
		// do not consider the root
		for (int nn = 1; nn < path_nodes.size() - 1; nn++) {
			int node = path_nodes.get(nn);
			norm *= this.betaSum.get(node) + tw.getNodeCount(node);
		}
		return norm;
	}
	
	private int[] findAffectedPaths(int[] nodes) {
		TIntHashSet affected = new TIntHashSet();
		for(int ii = 0; ii < nodes.length; ii++) {
			int node = nodes[ii];
			TIntArrayList paths = this.nodeToPath.get(node);
			for (int jj = 0; jj < paths.size(); jj++) {
				int pp = paths.get(jj);
				affected.add(pp);
			}
		}
		return affected.toArray();
	}
	
	private void updateNormalizer(int topic, TIntArrayList paths, double delta) {
		for (int ii = 0; ii < paths.size(); ii++) {
			int pp = paths.get(ii);
			double val = this.normalizer.get(topic, pp);
			val *= delta;
			this.normalizer.put(topic, pp, val);
		}
	}
	
	private double getObservation(int topic, int word, int path_index) {
		TIntArrayList path_nodes = this.wordPaths.get(word, path_index);
		TopicTreeWalk tw = this.traversals.get(topic);
		double val = 1.0;
		for(int ii = 0; ii < path_nodes.size()-1; ii++) {
			int parent = path_nodes.get(ii);
			int child = path_nodes.get(ii+1);
			val *= this.beta.get(parent, child) + tw.getCount(parent, child);
		}
		val -= this.priorPath.get(word, path_index);
		return val;
	}
	
	public void updateParams() {		
		for(int tt = 0; tt < this.numTopics; tt++) {
			for(int pp = 0; pp < this.getPathNum(); pp++) {
				this.updatePathMaskedCount(pp, tt);				
			}
			this.computeNormalizer(tt);
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
		if (delta > 0) {
			this.nonZeroPaths.get(word).adjustOrPutValue(topic, path_index, delta, delta);
		} else {
			this.nonZeroPaths.get(word).adjustValue(topic, path_index, delta);
		}
		
		// if necessary, change the path mask of the affected nodes
		if (affected_nodes != null && affected_nodes.length > 0) {
			int[] affected_paths = this.findAffectedPaths(affected_nodes);
			for(int ii = 0; ii < affected_paths.length; ii++) {
				this.updatePathMaskedCount(affected_paths[ii], topic);
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
	
	public double getNormalizer(int topic, int path) {
		return this.normalizer.get(topic, path) * this.rootNormalizer.get(topic);
	}
	
	public double computeTermSmoothing(double[] alpha, int word) {
		double smoothing = 0.0;
		int[] paths = this.getWordPathIndexSet(word);
		
		for(int tt = 0; tt < this.numTopics; tt++) {
			for(int pp : paths) {
				double val = alpha[tt] * this.getPathPrior(word, pp);
				val /= this.getNormalizer(tt, pp);
				smoothing += val;
			}
		}
		//myAssert(smoothing > 0, "something wrong with smoothing!");
		return smoothing;
	}
	
	public double computeTermTopicBeta(TIntIntHashMap topic_counts, int word) {
		double topic_beta = 0.0;
		int[] paths = this.getWordPathIndexSet(word);
		for(int tt : topic_counts.keys()) {
			if (topic_counts.get(tt) > 0 ) {
				for (int pp : paths) {
					double val = topic_counts.get(tt) * this.getPathPrior(word, pp);
					val /= this.getNormalizer(tt, pp);
					topic_beta += val;
				}
			}
		}
		//myAssert(topic_beta > 0, "something wrong with topic_beta!");
		return topic_beta;
	}
	
	public double computeTopicTerm(double[] alpha, TIntIntHashMap local_topic_counts, int word, HIntIntDoubleHashMap dict) {
		double norm = 0.0;
		HIntIntIntHashMap nonzeros = this.nonZeroPaths.get(word);
		
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
				dict.put(tt, path, val);
				norm += val;
			}
		}
		return norm;
	}


	//////////////////////////////////////////////////////////
	
	
}
