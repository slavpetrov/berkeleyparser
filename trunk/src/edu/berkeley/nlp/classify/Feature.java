package edu.berkeley.nlp.classify;

public class Feature {

	private Object obj;
	private int index;

	public Feature(Object obj, int index) {
		this.obj = obj;
		this.index = index;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((obj == null) ? 0 : obj.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Feature other = (Feature) obj;
		if (this.obj == null) {
			if (other.obj != null)
				return false;
		} else if (!this.obj.equals(other.obj))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return obj.toString();
	}

	public int getIndex() {
		return index;
	}

}
