package edu.berkeley.nlp.io;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import edu.berkeley.nlp.util.IntPair;
import edu.berkeley.nlp.util.MapFactory;

/**
 * An abstract class for Label objects which store attributes in a Map. Also
 * implements HasWord, HasTag, HasCategory, and HasContext by storing the words,
 * tags, etc. under standardized keys in the Map.
 * <p/>
 * For convenience, this class also contains standardized keys for storing many
 * other possible label attributes, such as head words, ner tags, etc.
 * 
 * @author grenager
 */
public abstract class AbstractMapLabel implements Label, Serializable {

	static final int initCapacity = 5;

	// THE STANDARD KEYS

	/**
	 * The standard key for storing a value in the map, as a String.
	 */
	public static final String VALUE_KEY = "value";

	/**
	 * The standard key for storing a tag in the map.
	 */
	public static final String TAG_KEY = "tag";

	/**
	 * The standard key for storing a word in the map, as a String.
	 */
	public static final String WORD_KEY = "word";

	/**
	 * The standard key for storing a lemma in the map.
	 */
	public static final String LEMMA_KEY = "lemma";

	/**
	 * The standard key for storing a category in the map, as a String.
	 */
	public static final String CATEGORY_KEY = "cat";

	/**
	 * The standard key for storing a projected category in the map, as a
	 * String. For any word (leaf node), the projected category is the syntactic
	 * category of the maximal constituent headed by the word. Used in
	 * SemanticGraph.
	 */
	public static final String PROJ_CAT_KEY = "pcat";

	/**
	 * The standard key for storing a head word in the map as a pointer to
	 * another node.
	 */
	public static final String HEAD_WORD_KEY = "hw";

	/**
	 * The standard key for storing a head tag in the map as a pointer to
	 * another node.
	 */
	public static final String HEAD_TAG_KEY = "ht";

	/**
	 * The standard key for storing an integer index in the map.
	 */
	public static final String INDEX_KEY = "idx";

	/**
	 * The standard key for a propbank label which is of type Argument
	 */
	public static final String ARG_KEY = "arg";

	/**
	 * Another key used for propbank - to signify core arg nodes or predicate
	 * nodes
	 */
	public static final String MARKING_KEY = "mark";

	/**
	 * The standard key for Semantic Head Word which is a String
	 */
	public static final String SEMANTIC_HEAD_WORD_KEY = "shw";

	/**
	 * The standard key for Semantic Head Word POS which is a String
	 */
	public static final String SEMANTIC_HEAD_POS_KEY = "shp";

	/**
	 * Probank key for the Verb sense given in the Propbank Annotation, should
	 * only be in the verbnode
	 */
	public static final String VERB_SENSE_KEY = "vs";

	/**
	 * The standard key for storing category with functional tags.
	 */
	public static final String CATEGORY_FUNCTIONAL_TAG_KEY = "cft";

	/**
	 * the standard key for the NER label.
	 */
	public static final String NER_KEY = "ner";

	/**
	 * the standard key for the coref label.
	 */
	public static final String COREF_KEY = "coref";

	/**
	 * The standard key for the "shape" of a word: a String representing the
	 * type of characters in a word, such as "Xx" for a capitalized word. See
	 * {@link edu.stanford.nlp.process.WordShapeClassifier} for functions for
	 * making shape strings.
	 */
	public static final String SHAPE_KEY = "shape";

	/**
	 * The Standard key for storing the left terminal number relative to the
	 * root of the tree of the leftmost terminal dominated by the current node
	 */
	public static final String LEFT_TERM_KEY = "LEFT_TERM";

	/**
	 * The standard key for the parent which is a String
	 */
	public static final String PARENT_KEY = "PARENT";

	/**
	 * The standard key for span which is a String
	 */
	public static final String SPAN_KEY = "SPAN";

	/**
	 * the standard key for the String that comes before this word (from the
	 * InvertiblePTBTokenizer)
	 */
	public static final String BEFORE_KEY = "before";

	/**
	 * the standard key for the String that comes after this word (from the
	 * InvertiblePTBTokenizer)
	 */
	public static final String AFTER_KEY = "after";

	/**
	 * the standard key for the actual, unmangled, pre-PTB'd word (from the
	 * InvertiblePTBTokenizer)
	 */
	public static final String CURRENT_KEY = "current";

	/**
	 * The standard key for the answer which is a String
	 */
	public static final String ANSWER_KEY = "answer";

	/**
	 * The standard key for gold answer which is a String
	 */
	public static final String GOLDANSWER_KEY = "goldAnswer";

	/**
	 * The standard key for the features which is a Collection
	 */
	public static final String FEATURES_KEY = "features";

	/**
	 * The standard key for the semantic interpretation
	 */
	public static final String INTERPRETATION_KEY = "interpretation";

	/**
	 * The standard key for the semantic role label
	 */
	public static final String ROLE_KEY = "srl";

	/**
	 * The standard key for the gazetteer information
	 */
	public static final String GAZETTEER_KEY = "gazetteer";

	public static final String STEM_KEY = "stem";

	public static final String POLARITY_KEY = "polarity";

	/**
	 * for Chinese: character level information, segmentation
	 */
	public static final String CH_CHAR_KEY = "char";
	public static final String CH_ORIG_SEG_KEY = "orig_seg"; // the segmentation
																// info existing
																// in the
																// original text
	public static final String CH_SEG_KEY = "seg"; // the segmentation
													// information from the
													// segmenter

	/** This key is at present only used in CraigslistDemo. */
	public static final String BEGIN_POSITION_KEY = "BEGIN_POS";

	/** The character offset of last character of token in source. */
	public static final String END_POSITION_KEY = "END_POS";

	/**
	 * The Map which stores all of the attributes for this label, and the label
	 * value itself.
	 */
	protected Map map;

	/**
	 * The MapFactory which will be used to make new Maps in this
	 * AbstractMapLabel.
	 */
	protected MapFactory mapFactory;

	protected AbstractMapLabel() {
		this(null);
	}

	protected AbstractMapLabel(MapFactory mapFactory) {
		if (mapFactory == null) {
			this.mapFactory = new MapFactory.HashMapFactory();
		} else {
			this.mapFactory = mapFactory;
		}
		this.map = this.mapFactory.buildMap();
	}

	// DIRECT MAP FUNCTIONALITY

	/**
	 * Return the <code>Map</code> contained in this label.
	 * 
	 * @return the <code>Map</code> contained in this AbstractMapLabel
	 */
	public Map map() {
		return map;
	}

	// yikes! [commented out by dramage 4/5/06]
	//
	// /**
	// * Set the <code>Map</code> contained in this AbstractMapLabel to the
	// * supplied <code>Map</code>.
	// *
	// * @param map the new <code>Map</code> for this label
	// */
	// public void setMap(Map map) {
	// this.map = map;
	// }

	/**
	 * Returns the value to which the map contained in this label maps the
	 * specified key. Returns <code>null</code> if the map contains no mapping
	 * for this key. (Analogous to {@link Map#get
	 * <code>Map.get(Object key)</code>}.)
	 * 
	 * @param key
	 *            key whose associated value is to be returned.
	 * @return the value to which the map contained in this label maps the
	 *         specified key, or <code>null</code> if the map contains no
	 *         mapping for this key.
	 */
	public Object get(Object key) {
		Object v = map.get(key);
		if (v == null) {
			return "";
		}
		return v;
	}

	/**
	 * Associates the specified value with the specified key in the map
	 * contained in this label. (Analogous to {@link Map#put
	 * <code>Map.put(Object key, Object value)</code>}.) If the map previously
	 * contained a mapping for this key, the old value is replaced by the
	 * specified value.
	 * 
	 * @param key
	 *            key with which the specified value is to be associated.
	 * @param value
	 *            value to be associated with the specified key.
	 * @return previous value associated with specified key, or
	 *         <code>null</code> if there was no mapping for key.
	 */
	public Object put(Object key, Object value) {
		return map.put(key, value);
	}

	// LABEL METHODS

	/**
	 * @return the value for the label
	 */
	public String value() {
		return (String) map.get(VALUE_KEY);
	}

	/**
	 * Set the value for the label.
	 * 
	 * @param value
	 *            the value for the label
	 */
	public void setValue(final String value) {
		map.put(VALUE_KEY, value);
	}

	/**
	 * Set value for the label from a String.
	 * 
	 * @param str
	 *            the string value for the label
	 */
	public void setFromString(final String str) {
		setValue(str);
	}

	// HASCATEGORY METHODS

	/**
	 * Return the category of the label (or <code>null</code> if none), which is
	 * stored in the map under the key {@link AbstractMapLabel#CATEGORY_KEY
	 * <code>CATEGORY_KEY</code>}.
	 * 
	 * @return the category for the label
	 */
	public String category() {
		Object cat = map.get(CATEGORY_KEY);
		if (cat != null && cat instanceof String) {
			return (String) cat;
		} else {
			return null;
		}
	}

	/**
	 * Set the category for the label.
	 * 
	 * @param category
	 *            the category for the label
	 */
	public void setCategory(final String category) {
		map.put(CATEGORY_KEY, category);
	}

	// HASWORD METHODS

	/**
	 * Return the word of the label, stored in the map under the key
	 * <code>WORD_KEY</code>.
	 * 
	 * @return The word for this label
	 */
	public String word() {
		return (String) map.get(WORD_KEY);
	}

	/**
	 * Set the word for the label.
	 * 
	 * @param word
	 *            the head word for the label
	 */
	public void setWord(String word) {
		map.put(WORD_KEY, word);
	}

	/**
	 * The span of this node as begin and end positions if it exists
	 * 
	 * @return The span
	 */
	public IntPair span() {
		return (IntPair) map.get(SPAN_KEY);
	}

	public void setSpan(String span) {
		map.put(SPAN_KEY, span);
	}

	/**
	 * Return the head word of the label (or <code>null</code> if none), which
	 * is stored in the map under the key {@link AbstractMapLabel#HEAD_WORD_KEY
	 * <code>HEAD_WORD_KEY</code>}.
	 * 
	 * @return the head word for the label
	 */
	public Object headWord() {
		return map.get(HEAD_WORD_KEY);
	}

	/**
	 * Set a pointer to the head-word for the label.
	 */
	public void setHeadWord(Object headWordPtr) {
		map.put(HEAD_WORD_KEY, headWordPtr);
		if (headWordPtr instanceof HasWord) {
			setWord(((HasWord) headWordPtr).word());
		} else if (headWordPtr instanceof Label) {
			setWord(((Label) headWordPtr).value());
		}
	}

	/**
	 * Returns the semantic head of the phrase if it exists, and null otherwise
	 */
	public String getSemanticWord() {
		Object word = map.get(SEMANTIC_HEAD_WORD_KEY);
		return word != null ? word.toString() : null;
	}

	/**
	 * Set the semantic head of the phrase
	 */
	public void setSemanticWord(final String hWord) {
		map.put(SEMANTIC_HEAD_WORD_KEY, hWord);
	}

	/**
	 * Returns the semantic head pos of the phrase if it exists, and null
	 * otherwise
	 */
	public String getSemanticTag() {
		Object word = map.get(SEMANTIC_HEAD_POS_KEY);
		return word != null ? word.toString() : null;
	}

	/**
	 * Set the semantic head pos of the phrase
	 */
	public void setSemanticTag(final String hTag) {
		map.put(SEMANTIC_HEAD_POS_KEY, hTag);
	}

	/**
	 * Return the head tag of the label (or <code>null</code> if none), which is
	 * stored in the map under the key {@link AbstractMapLabel#TAG_KEY
	 * <code>TAG_KEY</code>}.
	 * 
	 * @return the head tag for the label
	 */
	public String tag() {
		Object tag = map.get(TAG_KEY);
		if (tag != null && tag instanceof String) {
			return (String) tag;
		} else {
			return null;
		}
	}

	/**
	 * Set the head tag for the label by storing it in the map under the key
	 * {@link AbstractMapLabel#HEAD_TAG_KEY <code>HEAD_TAG_KEY</code>}.
	 * 
	 * @param tag
	 *            the head tag for the label
	 */
	public void setTag(final String tag) {
		map.put(TAG_KEY, tag);
	}

	public Object headTag() {
		return map.get(HEAD_TAG_KEY);
	}

	/**
	 * Set a pointer to the head-word for the label.
	 */
	public void setHeadTag(Object headTagPtr) {
		map.put(HEAD_TAG_KEY, headTagPtr);
		if (headTagPtr instanceof HasTag) {
			setTag(((HasTag) headTagPtr).tag());
		} else if (headTagPtr instanceof Label) {
			setTag(((Label) headTagPtr).value());
		}
	}

	/**
	 * Return the NER type of the word, which is stored in the map under the key
	 * {@link AbstractMapLabel#NER_KEY <code>NER_KEY</code>}.
	 * 
	 * @return The NER label of the word
	 */
	public String ner() {
		Object ner = map.get(NER_KEY);
		return (String) ner;
	}

	/**
	 * Set the NER label for the word, using the key
	 * {@link AbstractMapLabel#NER_KEY <code>NER_KEY</code>}.
	 * 
	 * @param ner
	 *            The String NER label of the word
	 */
	public void setNER(String ner) {
		map.put(NER_KEY, ner);
	}

	/**
	 * Return the shape attribute of the word, which is stored in the map under
	 * the key {@link AbstractMapLabel#SHAPE_KEY <code>SHAPE_KEY</code>}.
	 * 
	 * @return The shape of the word.
	 */
	public String shape() {
		return (String) map.get(SHAPE_KEY);
	}

	/**
	 * Set the shape property for the word, using the key
	 * {@link AbstractMapLabel#SHAPE_KEY <code>SHAPE_KEY</code>}.
	 * 
	 * @param shape
	 *            A String giving the "shape" of the word.
	 */
	public void setShape(String shape) {
		map.put(SHAPE_KEY, shape);
	}

	/**
	 * Return the index of the label (or -1 if none), which is stored in the map
	 * under the key {@link AbstractMapLabel#INDEX_KEY <code>INDEX_KEY</code>}.
	 * 
	 * @return the index for the label
	 */
	public int index() {
		Object index = map.get(INDEX_KEY);
		if (index != null && index instanceof Integer) {
			return ((Integer) index).intValue();
		} else {
			return -1;
		}
	}

	/**
	 * Set the index for the label by storing it in the contained map under the
	 * key {@link AbstractMapLabel#INDEX_KEY <code>INDEX_KEY</code>}.
	 * 
	 * WARNING: do NOT call this if the vertice is already in a SemanticGraph.
	 * Doing so will disrupt the equality criteria for the map, and will throw
	 * off routines that check to see if this vertice is in the SemanticGraph.
	 */
	public void setIndex(int index) {
		map.put(INDEX_KEY, new Integer(index));
	}

	/**
	 * Return the beginning character offset of the label (or -1 if none). This
	 * is stored in the map under the key
	 * {@link AbstractMapLabel#BEGIN_POSITION_KEY} <code>INDEX_KEY</code>.
	 * 
	 * @return the beginning position for the label
	 */
	public int beginPosition() {
		Object index = map.get(BEGIN_POSITION_KEY);
		if (index != null && index instanceof Integer) {
			return ((Integer) index).intValue();
		} else {
			return -1;
		}
	}

	/**
	 * Set the beginning character offset for the label by storing it in the
	 * contained map under the key {@link AbstractMapLabel#BEGIN_POSITION_KEY}
	 * <code>INDEX_KEY</code>. Setting this key to "-1" can be used to indicate
	 * no valid value.
	 * 
	 * @param beginPos
	 *            The beginning position
	 */
	public void setBeginPosition(int beginPos) {
		map.put(BEGIN_POSITION_KEY, new Integer(beginPos));
	}

	/**
	 * Return the ending character offset of the label (or -1 if none). This is
	 * stored in the map under the key {@link AbstractMapLabel#END_POSITION_KEY}
	 * <code>INDEX_KEY</code>.
	 * 
	 * @return the end position for the label
	 */
	public int endPosition() {
		Object index = map.get(END_POSITION_KEY);
		if (index != null && index instanceof Integer) {
			return ((Integer) index).intValue();
		} else {
			return -1;
		}
	}

	/**
	 * Set the ending character offset for the label by storing it in the
	 * contained map under the key {@link AbstractMapLabel#END_POSITION_KEY}
	 * <code>INDEX_KEY</code>. Setting this key to "-1" can be used to indicate
	 * no valid value.
	 * 
	 * @param endPos
	 *            The end position
	 */
	public void setEndPosition(int endPos) {
		map.put(END_POSITION_KEY, new Integer(endPos));
	}

	// HAS CONTEXT METHODS

	/**
	 * Return the String before the word, which is stored in the map under the
	 * key {@link AbstractMapLabel#BEFORE_KEY <code>BEFORE_KEY</code>}.
	 * 
	 * @return the String before the word
	 */
	public String before() {
		Object before = map.get(BEFORE_KEY);
		if (before == null) {
			before = "";
		}
		return (String) before;
	}

	/**
	 * Set the String before the word by storing it in the map under the key
	 * {@link AbstractMapLabel#BEFORE_KEY <code>BEFORE_KEY</code>}.
	 * 
	 * @param before
	 *            the String before the word
	 */
	public void setBefore(String before) {
		map.put(BEFORE_KEY, before);
	}

	/**
	 * Prepend this String to the current before String
	 * 
	 * @param before
	 *            the String to be prepended
	 */
	public void prependBefore(String before) {
		String oldBefore = before();
		if (oldBefore == null) {
			oldBefore = "";
		}
		setBefore(before + oldBefore);
	}

	/**
	 * Return the String which is the unmangled word, which is stored in the map
	 * under the key {@link AbstractMapLabel#CURRENT_KEY
	 * <code>CURRENT_KEY</code>}.
	 * 
	 * @return the unmangled word
	 */
	public String current() {
		Object current = map.get(CURRENT_KEY);
		if (current == null) {
			current = "";
		}
		return (String) current;
	}

	/**
	 * Set the String which is the unmangled word, which is stored in the map
	 * under the key {@link AbstractMapLabel#CURRENT_KEY
	 * <code>CURRENT_KEY</code>}.
	 * 
	 * @param current
	 *            the unmangled word
	 */
	public void setCurrent(String current) {
		map.put(CURRENT_KEY, current);
	}

	/**
	 * Return the String after the word, which is stored in the map under the
	 * key {@link AbstractMapLabel#AFTER_KEY <code>AFTER_KEY</code>}.
	 * 
	 * @return the String after the word
	 */
	public String after() {
		Object after = map.get(AFTER_KEY);
		if (after == null) {
			after = "";
		}
		return (String) after;
	}

	/**
	 * Set the String after the word by storing it in the map under the key
	 * {@link AbstractMapLabel#AFTER_KEY <code>AFTER_KEY</code>}.
	 * 
	 * @param after
	 *            The String after the word
	 */
	public void setAfter(String after) {
		map.put(AFTER_KEY, after);
	}

	/**
	 * Append this String to the current after String
	 * 
	 * @param after
	 *            The String to be prepended
	 */
	public void appendAfter(String after) {
		String oldAfter = after();
		if (oldAfter == null) {
			oldAfter = "";
		}
		setAfter(oldAfter + after);
	}

	/**
	 * convenience method for getting answer *
	 */
	public String answer() {
		return (String) get(ANSWER_KEY);
	}

	/**
	 * convenience method for setting answer *
	 */
	public void setAnswer(String answer) {
		put(ANSWER_KEY, answer);
	}

	/**
	 * convenience method for getting gold answer *
	 */
	public String goldAnswer() {
		return (String) get(GOLDANSWER_KEY);
	}

	/**
	 * convenience method for setting gold answer *
	 */
	public void setGoldAnswer(String goldAnswer) {
		put(GOLDANSWER_KEY, goldAnswer);
	}

	public Collection getFeatures() {
		return (Collection) map.get(FEATURES_KEY);
	}

	public void setFeatures(Collection features) {
		map.put(FEATURES_KEY, features);
	}

	public Object interpretation() {
		return map.get(INTERPRETATION_KEY);
	}

	public void setInterpretation(Object interpretation) {
		map.put(INTERPRETATION_KEY, interpretation);
	}

	public String getLemma() {
		return (String) map.get(LEMMA_KEY);
	}

	public void setLemma(String lemma) {
		map.put(LEMMA_KEY, lemma);
	}

	public String getRole() {
		return (String) map.get(ROLE_KEY);
	}

	public void setRole(String role) {
		map.put(ROLE_KEY, role);
	}

	private static final long serialVersionUID = -980833749513621054L;

}
