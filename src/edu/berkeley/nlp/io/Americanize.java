package edu.berkeley.nlp.io;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes a HasWord or String and returns an Americanized version of it.
 * Optionally, it does some month/day name normalization to capitalized. This is
 * deterministic spelling coversion, and so cannot deal with certain cases
 * involving complex ambiguities, but it can do most of the simple case of
 * English to American conversion.
 * <p>
 * <i>This list is still quite incomplete, but does some of the commenest cases
 * found when running our parser or doing biomedical processing. to expand this
 * list, we should probably look at:</i>
 * <code>http://wordlist.sourceforge.net/</code> or
 * <code>http://home.comcast.net/~helenajole/Harry.html</code>.
 * 
 * @author Christopher Manning
 */
public class Americanize /* implements Function */{

	private static boolean staticCapitalizeTimex = true;

	private boolean capitalizeTimex = true;

	public static final int DONT_CAPITALIZE_TIMEX = 1;

	public Americanize() {
	}

	/**
	 * Make an object for Americanizing spelling.
	 * 
	 * @param flags
	 *            An integer representing bit flags. At present the only
	 *            recognized flag is DONT_CAPITALIZE_TIMEX = 1 which suppresses
	 *            capitalization of days of the week and months
	 */
	public Americanize(int flags) {
		if ((flags & DONT_CAPITALIZE_TIMEX) != 0) {
			capitalizeTimex = false;
		}
	}

	/**
	 * Americanize the HasWord or String coming in.
	 * 
	 * @param in
	 *            A HasWord or String to covert to American if needed.
	 * @return Either the input or an Americanized version of it.
	 */
	public Object apply(Object in) {
		if (in instanceof HasWord) {
			HasWord w = (HasWord) in;
			String str = w.word();
			String outStr = americanize(str, capitalizeTimex);
			if (!outStr.equals(str)) {
				w.setWord(outStr);
			}
			return w;
		} else {
			// assume a String
			String str = (String) in;
			return americanize(str, capitalizeTimex);
		}
	}

	/**
	 * Convert the spelling of a word from British to American English. This is
	 * deterministic spelling coversion, and so cannot deal with certain cases
	 * involving complex ambiguities, but it can do most of the simple cases of
	 * English to American conversion. Month and day names will be capitalized
	 * unless you have changed the default setting.
	 * 
	 * @param str
	 *            The String to be Americanized
	 * @return The American spelling of the word.
	 */
	public static String americanize(String str) {
		return americanize(str, staticCapitalizeTimex);
	}

	/**
	 * Convert the spelling of a word from British to American English. This is
	 * deterministic spelling coversion, and so cannot deal with certain cases
	 * involving complex ambiguities, but it can do most of the simple cases of
	 * English to American conversion.
	 */
	public static String americanize(String str, boolean capitalizeTimex) {
		// System.err.println("str is |" + str + "|");
		// System.err.println("timexMapping.contains is " +
		// timexMapping.containsKey(str));
		if (capitalizeTimex && timexMapping.containsKey(str)) {
			return timexMapping.get(str);
		} else if (mapping.containsKey(str)) {
			return mapping.get(str);
		} else {
			for (int i = 0; i < pats.length; i++) {
				Pattern ex = excepts[i];
				if (ex != null) {
					Matcher me = ex.matcher(str);
					if (me.find()) {
						continue;
					}
				}
				Matcher m = pats[i].matcher(str);
				if (m.find()) {
					// System.err.println("Replacing " + word + " with " +
					// pats[i].matcher(word).replaceAll(reps[i]));
					return m.replaceAll(reps[i]);
				}
			}
			return str;
		}
	}

	private static Pattern[] pats = { Pattern.compile("haem(at)?o"),
			Pattern.compile("aemia$"), Pattern.compile("([lL]euk)aem"),
			Pattern.compile("programme(s?)$"),
			Pattern.compile("^([a-z]{3,})our(s?)$") };

	private static Pattern[] excepts = { null, null, null, null,
			Pattern.compile("glamour|de[tv]our") };

	private static String[] reps = { "hem$1o", "emia", "$1em", "program$1",
			"$1or$2" };

	/**
	 * Do some normalization and British -> American mapping! Notes:
	 * <ul>
	 * <li>in PTB, you get dialogue not dialog, 17 times to 1.
	 * </ul>
	 */
	private static final String[] converters = new String[] { "anaesthetic",
			"analogue", "analogues", "analyse", "analysed", "analysing", /*
																		 * not
																		 * analyses
																		 * NNS
																		 */
			"armoured", "cancelled", "cancelling", "candour", "capitalise",
			"capitalised", "capitalisation", "centre", "chimaeric", "clamour",
			"coloured", "colouring", "defence", "detour", /*
														 * "dialogue",
														 * "dialogues",
														 */"discolour",
			"discolours", "discoloured", "discolouring", "encyclopaedia",
			"endeavour", "endeavours", "endeavoured", "endeavouring",
			"fervour", "favour", "favours", "favoured", "favouring",
			"favourite", "favourites", "fibre", "fibres", "finalise",
			"finalised", "finalising", "flavour", "flavours", "flavoured",
			"flavouring", "glamour", "grey", "harbour", "harbours",
			"homologue", "homologues", "honour", "honours", "honoured",
			"honouring", "honourable", "humour", "humours", "humoured",
			"humouring", "kerb", "labelled", "labelling", "labour", "labours",
			"laboured", "labouring", "leant", "learnt", "localise",
			"localised", "manoeuvre", "manoeuvres", "maximise", "maximised",
			"maximising", "meagre", "minimise", "minimised", "minimising",
			"modernise", "modernised", "modernising", "misdemeanour",
			"misdemeanours", "neighbour", "neighbours", "neighbourhood",
			"neighbourhoods", "oestrogen", "oestrogens", "organisation",
			"organisations", "penalise", "penalised", "popularise",
			"popularised", "popularises", "popularising", "practise",
			"practised", "pressurise", "pressurised", "pressurises",
			"pressurising", "realise", "realised", "realising", "realises",
			"recognise", "recognised", "recognising", "recognises", "rumoured",
			"rumouring", "savour", "savours", "savoured", "savouring",
			"splendour", "splendours", "theatre", "theatres", "titre",
			"titres", "travelled", "travelling" };

	private static final String[] converted = new String[] { "anesthetic",
			"analog", "analogs", "analyze", "analyzed", "analyzing", "armored",
			"canceled", "canceling", "candor", "capitalize", "capitalized",
			"capitalization", "center", "chimeric", "clamor", "colored",
			"coloring", "defense", "detour", /* "dialog", "dialogs", */
			"discolor", "discolors", "discolored", "discoloring",
			"encyclopedia", "endeavor", "endeavors", "endeavored",
			"endeavoring", "fervor", "favor", "favors", "favored", "favoring",
			"favorite", "favorites", "fiber", "fibers", "finalize",
			"finalized", "finalizing", "flavor", "flavors", "flavored",
			"flavoring", "glamour", "gray", "harbor", "harbors", "homolog",
			"homologs", "honor", "honors", "honored", "honoring", "honorable",
			"humor", "humors", "humored", "humoring", "curb", "labeled",
			"labeling", "labor", "labors", "labored", "laboring", "leaned",
			"learned", "localize", "localized", "maneuver", "maneuvers",
			"maximize", "maximized", "maximizing", "meager", "minimize",
			"minimized", "minimizing", "modernize", "modernized",
			"modernizing", "misdemeanor", "misdemeanors", "neighbor",
			"neighbors", "neighborhood", "neighborhoods", "estrogen",
			"estrogens", "organization", "organizations", "penalize",
			"penalized", "popularize", "popularized", "popularizes",
			"popularizing", "practice", "practiced", "pressurize",
			"pressurized", "pressurizes", "pressurizing", "realize",
			"realized", "realizing", "realizes", "recognize", "recognized",
			"recognizing", "recognizes", "rumored", "rumoring", "savor",
			"savors", "savored", "savoring", "splendor", "splendors",
			"theater", "theaters", "titer", "titers", "traveled", "traveling" };

	private static final String[] timexConverters = new String[] { "january",
			"february", /* not "march" ! */
			"april", /* Not "may"! */"june", "july", "august", "september",
			"october", "november", "december", "monday", "tuesday",
			"wednesday", "thursday", "friday", "saturday", "sunday" };

	private static final String[] timexConverted = new String[] { "January",
			"February", /* not "march" ! */
			"April", /* Not "may"! */"June", "July", "August", "September",
			"October", "November", "December", "Monday", "Tuesday",
			"Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };

	private static final HashMap<String, String> mapping = new HashMap<String, String>();

	private static final HashMap<String, String> timexMapping = new HashMap<String, String>();

	// static initialization block
	static {
		if (converters.length != converted.length
				|| timexConverters.length != timexConverted.length
				|| pats.length != reps.length || pats.length != excepts.length) {
			throw new RuntimeException("Americanize: Bad initialization data");
		}
		for (int i = 0; i < converters.length; i++) {
			mapping.put(converters[i], converted[i]);
		}
		for (int i = 0; i < timexConverters.length; i++) {
			timexMapping.put(timexConverters[i], timexConverted[i]);
		}
	}

	public void setStaticCapitalizeTimex(boolean capitalizeTimex) {
		staticCapitalizeTimex = capitalizeTimex;
	}

	public String toString() {
		return "Americanize[capitalizeTimex is " + staticCapitalizeTimex + "; "
				+ "mapping has " + mapping.size() + " mappings; "
				+ "timexMapping has " + timexMapping.size() + " mappings]";
	}

	/**
	 * Americanize and print the command line arguments. This main method is
	 * just for debugging.
	 */
	public static void main(String[] args) {
		System.err.println(new Americanize());
		System.err.println();
		for (String arg : args) {
			System.out.println(arg + " --> " + americanize(arg));
		}
	}

}
