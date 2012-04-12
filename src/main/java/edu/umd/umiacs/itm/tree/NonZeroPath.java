package edu.umd.umiacs.itm.tree;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

public class NonZeroPath {
	
	HIntIntIntHashMap data;
	
	public NonZeroPath () {
		this.data = new HIntIntIntHashMap();
	}
	
	public void put(int key1, int key2, int value) {
		this.data.put(key1, key2, value);
	}
	
	public void get(int key1, int key2) {
		this.data.get(key1, key2);
	}

}
