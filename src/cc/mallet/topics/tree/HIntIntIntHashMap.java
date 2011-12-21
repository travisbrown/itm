package cc.mallet.topics.tree;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

public class HIntIntIntHashMap {
	
	TIntObjectHashMap<TIntIntHashMap> data;
	
	public HIntIntIntHashMap() {
		this.data = new TIntObjectHashMap<TIntIntHashMap> ();
	}
	
	public void put(int key1, int key2, int value) {
		if(! this.data.contains(key1)) {
			this.data.put(key1, new TIntIntHashMap());
		}
		TIntIntHashMap tmp = this.data.get(key1);
		tmp.put(key2, value);
	}
	
	public TIntIntHashMap get(int key1) {
		if(this.contains(key1)) {
			return this.data.get(key1);
		} 
		return null;
	}
	
	public int[] getKey1Set() {
		return this.data.keys();
	}
	
	public int get(int key1, int key2) {
		if (this.contains(key1, key2)) {
			return this.data.get(key1).get(key2);
		} else {
			System.out.println("HIntIntIntHashMap: key does not exist!");
			return 0;
		}
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
	
	public void adjustValue(int key1, int key2, int increment) {
		int old = this.get(key1, key2);
		this.put(key1, key2, old+increment);
	}
	
	public void adjustOrPutValue(int key1, int key2, int increment, int newvalue) {
		if (this.contains(key1, key2)) {
			int old = this.get(key1, key2);
			this.put(key1, key2, old+increment);
		} else {
			this.put(key1, key2, newvalue);
		}
	}
	
	public void removeKey1(int key1) {
		this.data.remove(key1);
	}
	
	public void removeKey2(int key1, int key2) {
		if (this.data.contains(key1)) {
			this.data.get(key1).remove(key2);
		}
	}
	
}
