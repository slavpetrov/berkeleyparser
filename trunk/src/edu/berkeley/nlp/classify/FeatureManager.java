package edu.berkeley.nlp.classify;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureManager implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 42;

	private transient Map<Feature, Feature> featureInterner = new HashMap<Feature, Feature>();
	private transient List<Feature> featureList = new ArrayList<Feature>();
	private boolean locked = false;

	public void lock() {
		this.locked = true;
	}

	public Feature getFeatrue(String pred, String val) {
		return getFeature(pred + "=" + val);
	}

	public void addFeature(String pred, String val) {
		assert !locked;
		addFeature(String.format("%s=%s", pred, val));
	}

	public void addFeature(String val) {
		assert !locked;
		getFeature(val);
	}

	public Feature getFeature(int index) {
		return featureList.get(index);
	}

	public Feature getFeature(String val) {
		Feature feat = new Feature(val, -1);
		Feature canonicalFeat = featureInterner.get(feat);
		if (canonicalFeat == null) {
			assert !locked : "Can't find feature " + val
					+ " in locked FeatureManager";
			feat = new Feature(feat.toString(), featureInterner.size());
			featureInterner.put(feat, feat);
			featureList.add(feat);
			canonicalFeat = feat;
		}
		return canonicalFeat;
	}

	public int getNumFeatures() {
		return featureInterner.size();
	}

	public boolean hasFeature(String val) {
		Feature feat = new Feature(val, -1);
		return featureInterner.containsKey(feat);
	}

	public boolean isLocked() {
		return locked;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		out.writeObject(featureList.size());
		for (Feature feat : featureList) {
			out.writeObject(feat.toString());
		}
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		in.defaultReadObject();
		featureList = new ArrayList<Feature>();
		featureInterner = new HashMap<Feature, Feature>();
		boolean oldLocked = this.locked;
		this.locked = false;
		int numFeats = (Integer) in.readObject();
		for (int i = 0; i < numFeats; ++i) {
			String f = (String) in.readObject();
			addFeature(f);
		}
		this.locked = oldLocked;
	}

}
