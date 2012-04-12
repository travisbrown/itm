package edu.umd.umiacs.itm.tree;

import gnu.trove.TIntDoubleHashMap;

/**
 * This class defines a two level hashmap, so a value will be indexed by two keys.
 * The value is double, and two keys are both int.
 * Author: Yuening Hu
 */
public class HIntIntDoubleHashMap extends TwoIntHashMap<TIntDoubleHashMap> {
	/**
	 * If keys do not exist, insert value.
	 * Else update with the new value.
	 */
	public void put(int key1, int key2, double value) {
		if(! this.data.contains(key1)) {
			this.data.put(key1, new TIntDoubleHashMap());
		}
		TIntDoubleHashMap tmp = this.data.get(key1);
		tmp.put(key2, value);
	}

	/**
	 * Return the value indexed by key1 and key2.
	 */
	public double get(int key1, int key2) {
		if (this.data.contains(key1)) {
			TIntDoubleHashMap tmp1 = this.data.get(key1);
			if (tmp1.contains(key2)) {
				return tmp1.get(key2);
			}
		}
		System.out.println("HIntIntDoubleHashMap: key does not exist!");
		return -1;
	}

	/**
	 * Remove the second key 
	 */
	public void removeKey2(int key1, int key2) {
		if (this.data.contains(key1)) {
			this.data.get(key1).remove(key2);
		}
	}
}

