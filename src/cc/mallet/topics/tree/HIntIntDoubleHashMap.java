package cc.mallet.topics.tree;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;


public class HIntIntDoubleHashMap {
	TIntObjectHashMap<TIntDoubleHashMap> data;
	public HIntIntDoubleHashMap() {
		this.data = new TIntObjectHashMap<TIntDoubleHashMap> ();
	}
	
	public void put(int key1, int key2, double value) {
		if(! this.data.contains(key1)) {
			this.data.put(key1, new TIntDoubleHashMap());
		}
		TIntDoubleHashMap tmp = this.data.get(key1);
		tmp.put(key2, value);
	}
	
	public TIntDoubleHashMap get(int key1) {
		return this.data.get(key1);
	}
	
	public double get(int key1, int key2) {
		
		if (this.data.contains(key1)) {
			TIntDoubleHashMap tmp1 = (TIntDoubleHashMap) this.data.get(key1);
			if (tmp1.contains(key2)) {
				return tmp1.get(key2);
			}
		}
		
		System.out.println("HIntIntDoubleHashMap: key does not exist!");
		return -1;
	}
	
	public int[] getKey1Set() {
		return this.data.keys();
	}
	
	public boolean contains(int key1) {
		return this.data.contains(key1);
	}
	
	public boolean contains(int key1, int key2) {
		if (this.data.contains(key1)) {
			return this.data.get(key1).contains(key2);
		} else {
			return false;
		}
	}

}

