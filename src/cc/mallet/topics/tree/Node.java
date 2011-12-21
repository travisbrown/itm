package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;


public class Node {
	int offset;
	double rawCount;
	double hypoCount;
	String hyperparamName;
	
	TIntArrayList words;
	TDoubleArrayList wordsCount;
	TIntArrayList childOffsets;
	
	int numChildren;
	int numPaths;
	int numWords;
	
	double transitionScalor;
	TDoubleArrayList transitionPrior;
	
	public Node() {
		this.words = new TIntArrayList ();
		this.wordsCount = new TDoubleArrayList ();
		this.childOffsets = new TIntArrayList ();
		this.transitionPrior = new TDoubleArrayList ();
		this.numChildren = 0;
		this.numWords = 0;
		this.numPaths = 0;
	}
	
	public void initializePrior(int size) {
		for (int ii = 0; ii < size; ii++ ) {
			this.transitionPrior.add(0.0);
		}
	}
	
	public void setOffset(int val) {
		this.offset = val;
	}
	
	public void setRawCount(double count) {
		this.rawCount = count;
	}
	
	public void setHypoCount(double count) {
		this.hypoCount = count;
	}
	
	public void setHyperparamName(String name) {
		this.hyperparamName = name;
	}
	
	public void setTransitionScalor(double val) {
		this.transitionScalor = val;
	}
	
	public void setPrior(int index, double value) {
		this.transitionPrior.set(index, value);
	}
	
	public void addChildrenOffset(int childOffset) {
		this.childOffsets.add(childOffset);
		this.numChildren += 1;
	}
	
	public void addWord(int wordIndex, double wordCount) {
		this.words.add(wordIndex);
		this.wordsCount.add(wordCount);
		this.numWords += 1;
	}
	
	public void addPaths(int inc) {
		this.numPaths += inc;
	}
	
	public int getOffset() {
		return this.offset;
	}
	
	public int getNumChildren() {
		return this.numChildren;
	}
	
	public int getNumWords() {
		return this.numWords;
	}
	
	public int getChild(int child_index) {
		return this.childOffsets.get(child_index);
	}
	
	public int getWord(int word_index) {
		return this.words.get(word_index);
	}
	
	public double getWordCount(int word_index) {
		return this.wordsCount.get(word_index);
	}
	
	public double getHypoCount() {
		return this.hypoCount;
	}
	
	public double getTransitionScalor() {
		return this.transitionScalor;
	}
	
	public TDoubleArrayList getTransitionPrior() {
		return this.transitionPrior;
	}
	
	public void normalizePrior() {
		double norm = 0;
		for (int ii = 0; ii < this.transitionPrior.size(); ii++) {
			norm += this.transitionPrior.get(ii);
		}
		for (int ii = 0; ii < this.transitionPrior.size(); ii++) {
			double tmp = this.transitionPrior.get(ii) / norm;
			tmp *= this.transitionScalor;
			this.transitionPrior.set(ii, tmp);
		}
	}
}
