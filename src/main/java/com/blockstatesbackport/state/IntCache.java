package com.blockstatesbackport.state;

/**
 * Small open-addressing int -> Object map, so state lookups in the render loop do not box keys.
 */
public final class IntCache {

	private int[] keys = new int[16];
	private Object[] values = new Object[16];
	private int size;

	public Object get(int key) {
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
			Object v = values[i];
			if (v == null) {
				return null;
			}
			if (keys[i] == key) {
				return v;
			}
		}
	}

	public void put(int key, Object value) {
		if ((size + 1) * 2 > keys.length) {
			grow();
		}
		int mask = keys.length - 1;
		for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
			if (values[i] == null) {
				keys[i] = key;
				values[i] = value;
				size++;
				return;
			}
			if (keys[i] == key) {
				values[i] = value;
				return;
			}
		}
	}

	private void grow() {
		int[] k = keys;
		Object[] v = values;
		keys = new int[k.length * 2];
		values = new Object[k.length * 2];
		size = 0;
		for (int i = 0; i < k.length; i++) {
			if (v[i] != null) {
				put(k[i], v[i]);
			}
		}
	}

	private static int mix(int h) {
		h *= 0x9E3779B9;
		return h ^ (h >>> 16);
	}
}
