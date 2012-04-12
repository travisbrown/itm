package edu.umd.umiacs.itm.tree;

import gnu.trove.TIntHash;
import gnu.trove.TIntObjectHashMap;

/**
 * This class defines a two level hashmap, so a value will be indexed by two keys.
 * Author: Travis Brown
 */
public abstract class TwoIntHashMap<T extends TIntHash> {	
	protected TIntObjectHashMap<T> data;

	public TwoIntHashMap() {
		this.data = new TIntObjectHashMap<T>();
	}

	/**
	 * Return the HashMap indexed by the first key.
	 */
	public T get(int key1) {
		if (this.contains(key1)) {
			return this.data.get(key1);
		} 
		return null;
	}

	/**
	 * Return the first key set.
	 */
	public int[] getKey1Set() {
		return this.data.keys();
	}

	/**
	 * Check whether key1 is contained in the first key set or not.
	 */
	public boolean contains(int key1) {
		return this.data.contains(key1);
	}

	/**
	 * Check whether the key pair (key1, key2) is contained or not.
	 */
	public boolean contains(int key1, int key2) {
		if (this.data.contains(key1)) {
			return this.data.get(key1).contains(key2);
		} else {
			return false;
		}
	}
}

