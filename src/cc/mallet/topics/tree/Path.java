package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;

public class Path {
	
	TIntArrayList nodes;
	//TIntArrayList children;
	int finalWord;
	
	public Path () {
		this.nodes = new TIntArrayList();
		this.finalWord = -1;
	}
	
	public void addNodes (TIntArrayList innodes) {
		for (int ii = 0; ii < innodes.size(); ii++) {
			int node_index = innodes.get(ii);
			this.nodes.add(node_index);
		}
	}
	
	public void addFinalWord(int word) {
		this.finalWord = word;
	}
	
	public TIntArrayList getNodes() {
		return this.nodes;
	}
	
	public int getFinalWord() {
		return this.finalWord;
	}
}
