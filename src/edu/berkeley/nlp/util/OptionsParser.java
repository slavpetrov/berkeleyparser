package edu.berkeley.nlp.util;

import static edu.berkeley.nlp.util.LogInfo.stderr;
import static edu.berkeley.nlp.util.LogInfo.stdout;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

class OptInfo {
	public String group, name, gloss;
	public String condReq;
	public boolean required;
	public boolean specified;
	public Object obj;
	// For serialization: sometimes the obj doesn't have enough information,
	// so we need to use the string that was used to construct the object
	public String stringRepn; // Used when obj is Random or BufferedReader (hard
								// to get string)

	// One of the following two are set
	public Field field;
	public Method setMethod, getMethod;

	public String fullName() {
		return group + "." + name;
	}

	// Return "" if field is not an enum type
	public String getEnumStr() {
		return getEnumStr(field != null ? field.getType() : getMethod
				.getReturnType());
	}

	public static String getEnumStr(Class c) {
		return StrUtils.join(c.getEnumConstants(), "|");
	}

	public Object getValue() {
		try {
			return field != null ? field.get(obj) : getMethod.invoke(obj);
		} catch (InvocationTargetException e) {
			stderr.println("Can't access method: " + e);
			return null;
		} catch (IllegalAccessException e) {
			stderr.println("Can't access field: " + e);
			return null;
		}
	}

	// Important to format properly in a way that we can read it and parse it
	// again.
	public String getValueString() {
		if (stringRepn != null)
			return stringRepn;
		Object o = getValue();
		// System.out.println("GOT " + fullName() + " " + o);
		if (o == null)
			return "";
		if (o instanceof ArrayList)
			return StrUtils.join((ArrayList) o);
		if (o instanceof Pair)
			return ((Pair) o).getFirst() + "," + ((Pair) o).getSecond();

		// Array
		if (objIsArray(o)) {
			StringBuilder buf = new StringBuilder();
			for (int i = 0; i < Array.getLength(o); i++) {
				if (i > 0)
					buf.append(' ');
				buf.append(Array.get(o, i));
			}
			return buf.toString();
		}
		if (o instanceof Random) // Argh, can't get the seed, just assume it's 1
			return "1";
		return o.toString();
	}

	@Override
	public String toString() {
		String valueStr = getValueString();
		String s = String.format("%-30s <%5s> : %s [%s]", fullName(),
				typeStr(), gloss, valueStr);
		String t = getEnumStr();
		if (!t.equals(""))
			s += " " + t;
		return s;
	}

	public void print() {
		stdout.println("  " + toString());
	}

	private Type getGenericType() {
		return field != null ? field.getGenericType() : getMethod
				.getGenericReturnType();
	}

	private String typeStr() {
		return typeStr(getGenericType());
	}

	private static boolean isEnum(Type type) {
		return type instanceof Class && ((Class) type).isEnum();
	}

	// Array detectors
	static boolean objIsArray(Object o) {
		return typeIsArray(o.getClass());
	}

	static boolean typeIsArray(Type t) {
		return t instanceof Class && ((Class) t).getComponentType() != null;
	}

	static Class arrayTypeOfObj(Object o) {
		return arrayTypeOfType(o.getClass());
	}

	static Class arrayTypeOfType(Type t) {
		return ((Class) t).getComponentType();
	}

	private static boolean isBool(Type type) {
		return type.equals(boolean.class) || type.equals(Boolean.class);
	}

	private static String typeStr(Type type) {
		if (type.equals(boolean.class) || type.equals(Boolean.class))
			return "bool";
		if (type.equals(int.class) || type.equals(Integer.class))
			return "int";
		if (type.equals(short.class) || type.equals(Short.class))
			return "shrt";
		if (type.equals(double.class) || type.equals(Double.class))
			return "dbl";
		if (type.equals(String.class))
			return "str";
		if (type.equals(BufferedReader.class))
			return "read";
		if (type.equals(Random.class))
			return "rand";
		if (isEnum(type))
			return "enum";
		if (typeIsArray(type))
			return typeStr(arrayTypeOfType(type)) + "*";
		if (type instanceof ParameterizedType) {
			ParameterizedType ptype = (ParameterizedType) type;
			type = ptype.getRawType();
			Type[] childTypes = ptype.getActualTypeArguments();
			if (type.equals(ArrayList.class))
				return typeStr(childTypes[0]) + "*";
			if (type.equals(Pair.class))
				return typeStr(childTypes[0]) + "2";
		}
		return "unk";
	}

	private static boolean checkNumArgs(int want, int have, String fullName) {
		if (have != want) {
			stderr.printf(want + " arguments required for " + fullName
					+ ", but got " + have + "\n");
			return false;
		}
		return true;
	}

	// Return errorValue if there's an error (null is a valid value).
	// type: the data type of the variable
	// l: the command line arguments to interpret
	private static String errorValue = "ERROR";

	private static Object interpretValue(Type type, List<String> l,
			String fullName) {
		int n = l.size();
		String firstArg = n > 0 ? l.get(0) : null;

		if (type.equals(boolean.class) || type.equals(Boolean.class)) {
			boolean x = (n == 0 ? true : Boolean.parseBoolean(firstArg));
			return x;
		}
		if (type.equals(int.class) || type.equals(Integer.class)) {
			if (!checkNumArgs(1, n, fullName))
				return errorValue;
			int x;
			if (firstArg.equals("MAX"))
				x = Integer.MAX_VALUE;
			else if (firstArg.equals("MIN"))
				x = Integer.MIN_VALUE;
			else
				x = Integer.parseInt(firstArg);
			return x;
		}
		if (type.equals(short.class) || type.equals(Short.class)) {
			if (!checkNumArgs(1, n, fullName))
				return errorValue;
			short x;
			if (firstArg.equals("MAX"))
				x = Short.MAX_VALUE;
			else if (firstArg.equals("MIN"))
				x = Short.MIN_VALUE;
			else
				x = Short.parseShort(firstArg);
			return x;
		}
		if (type.equals(double.class) || type.equals(Double.class)) {
			if (!checkNumArgs(1, n, fullName))
				return errorValue;
			double x;
			if (firstArg.equals("MAX"))
				x = Double.POSITIVE_INFINITY;
			else if (firstArg.equals("MIN"))
				x = Double.NEGATIVE_INFINITY;
			else
				x = Double.parseDouble(firstArg);
			return x;
		}
		if (type.equals(double[].class)) {
			double[] x = new double[l.size()];
			for (int i = 0; i < l.size(); i++)
				x[i] = Double.parseDouble(l.get(i));
			return x;
		}
		if (type.equals(String[].class)) {
			String[] x = new String[l.size()];
			for (int i = 0; i < l.size(); i++)
				x[i] = l.get(i);
			return x;
		}
		if (type.equals(String.class)) { // Join many arguments using spaces
			String x = StrUtils.join(l);
			return x;
		}
		if (type.equals(BufferedReader.class)) {
			if (!checkNumArgs(1, n, fullName))
				return errorValue;
			BufferedReader x = "-".equals(firstArg) ? LogInfo.stdin : IOUtils
					.openInHard(firstArg);
			return x;
		}
		if (type.equals(Random.class)) {
			if (!checkNumArgs(1, n, fullName))
				return errorValue;
			// seed 0 means use the time
			int seed = Integer.parseInt(firstArg);
			Random x = seed == 0 ? new Random() : new Random(seed);
			return x;
		}
		if (type instanceof Class && ((Class) type).isEnum()) {
			if (n == 0)
				return null;
			if (!checkNumArgs(1, n, fullName))
				return errorValue;
			Object x = Utils.parseEnum((Class) type, firstArg);
			if (x == null) {
				stderr.println("Invalid enum: '" + firstArg
						+ "'; valid choices: " + getEnumStr((Class) type));
				return errorValue;
			}
			return x;
		}

		// Foo[], where Foo is any class
		if (typeIsArray(type)) {
			// Put the elements in the array
			Class childType = arrayTypeOfType(type);
			Object x = Array.newInstance(childType, l.size());
			int i = 0;
			for (String a : l) {
				Object o = interpretValue(childType, ListUtils.newList(a),
						fullName);
				if (o == errorValue)
					return errorValue;
				Array.set(x, i++, o);
			}
			return x;
		}

		// Pair or ArrayList
		if (type instanceof ParameterizedType) {
			// Types involving generics: pair, arraylist
			ParameterizedType ptype = (ParameterizedType) type;
			type = ptype.getRawType();
			Type[] childTypes = ptype.getActualTypeArguments();

			if (type.equals(Pair.class)) { // Delimited by comma
				if (!checkNumArgs(1, n, fullName))
					return errorValue;
				// Put the elements in the array
				String[] tokens = firstArg.split(",", 2);
				if (tokens.length != 2) {
					stderr.println("Invalid pair: '" + firstArg + "'");
					return errorValue;
				}
				Object o1 = interpretValue(childTypes[0],
						ListUtils.newList(tokens[0]), fullName);
				if (o1 == errorValue)
					return errorValue;
				Object o2 = interpretValue(childTypes[1],
						ListUtils.newList(tokens[1]), fullName);
				if (o2 == errorValue)
					return errorValue;
				return new Pair(o1, o2);
			} else if (type.equals(List.class) || type.equals(ArrayList.class)) {
				ArrayList x = new ArrayList();
				// Put the elements in the array
				for (String a : l) {
					Object o = interpretValue(childTypes[0],
							ListUtils.newList(a), fullName);
					if (o == errorValue)
						return errorValue;
					x.add(o);
				}
				return x;
			}
		}

		// Try to construct the weird type using the constructor
		// that takes one string argument.
		if (type instanceof Class) {
			try {
				Constructor con = ((Class) type).getConstructor(String.class);
				return con.newInstance(new Object[] { StrUtils.join(l) });
			} catch (Exception e) {
				stderr.println("Failed to construct " + type + ": " + e);
				e.printStackTrace();
				return errorValue;
			}
		}

		stderr.println("Can't handle weird field type: " + type);
		return errorValue;
	}

	private void setField(Object v) throws IllegalAccessException,
			InvocationTargetException {
		if (!tryToUseSetters(v)) {
			if (field != null)
				field.set(obj, v);
			else
				setMethod.invoke(obj, v);
		}
	}

	private boolean tryToUseSetters(Object v) {
		if (field == null)
			return false;
		String targetMethodName = "set" + field.getName();
		Method[] methods = obj.getClass().getMethods();
		for (Method m : methods) {
			String methodName = m.getName().toLowerCase();
			if (methodName.equalsIgnoreCase(targetMethodName)) {
				try {
					m.invoke(obj, v);
				} catch (Exception e) {
					return false;
				}
				return true;
			}
		}
		return false;
	}

	public boolean set(List<String> l, boolean append) {
		try {
			Object v = interpretValue(getGenericType(), l, fullName());
			if (v == errorValue)
				return false;
			// System.out.println(name + " " + stringRepn + " " + v);
			if (!append) {
				// Treat boolean case specially because -flag means true, and l
				// is empty
				if (isBool(getGenericType()))
					stringRepn = v.toString();
				else
					stringRepn = StrUtils.join(l);
				// field.set(obj, v);
				setField(v);
			} else {
				Object oldv = field.get(obj);
				// System.out.println("append " + l);
				// System.out.println((oldv == null ? "" : (String)oldv + " ") +
				// v);
				stringRepn = (stringRepn == null ? "" : stringRepn + " ")
						+ StrUtils.join(l);
				if (oldv instanceof ArrayList)
					((ArrayList) oldv).addAll((ArrayList) v);
				else if (oldv instanceof String)
					// field.set(obj, (oldv == null ? "" : (String)oldv + " ") +
					// v);
					setField((oldv == null ? "" : (String) oldv + " ") + v);
			}
		} catch (InvocationTargetException e) {
			stderr.println("Can't set method: " + e);
			return false;
		} catch (IllegalAccessException e) {
			stderr.println("Can't set field: " + e);
			return false;
		}

		specified = true;
		return true;
	}
}

/**
 * Due to historical reasons, all the member functions are prefixed with do, and
 * all the static functions (apply to the global theParser instance) are not.
 * 
 * 3/1/2007: static methods register and parse have been deprecated. Please
 * create an instance and use the doRegister and doParse counterparts.
 */
public class OptionsParser {
	public OptionsParser() {
	}

	public OptionsParser(Object... objects) {
		doRegisterAll(objects);
	}

	public OptionsParser doRegister(String group, Object o) {
		if (objects.containsKey(group))
			throw Exceptions.bad("Group name already exists: " + group);
		objects.put(group, o);

		// Recursively register its option sets
		for (Field field : classOf(o).getFields()) {
			OptionSet ann = field.getAnnotation(OptionSet.class);
			if (ann == null)
				continue;
			try {
				doRegister(group + "." + ann.name(), field.get(o));
			} catch (IllegalAccessException e) {
				throw Exceptions.bad("Can't access field: " + e);
			}
		}

		for (Method method : classOf(o).getMethods()) {
			OptionSet ann = (OptionSet) method.getAnnotation(OptionSet.class);
			if (ann == null)
				continue;
			try {
				doRegister(group + "." + ann.name(), method.invoke(o));
			} catch (InvocationTargetException e) {
				throw Exceptions.bad("Can't access method: " + e);
			} catch (IllegalAccessException e) {
				throw Exceptions.bad("Can't access method: " + e);
			}
		}

		return this;
	}

	public OptionsParser doRegisterAll(Object[] objects) {
		// Strings are interpreted as the key name for the next object.
		String name = null;
		for (Object o : objects) {
			if (o == null)
				continue;
			if (o instanceof String)
				name = (String) o;
			else {
				if (name == null) {
					if (o instanceof Class)
						name = ((Class) o).getSimpleName();
					else
						name = o.getClass().getSimpleName();
				}
				doRegister(name, o);
				name = null;
			}
		}
		return this;
	}

	@Deprecated
	// Don't use the static methods
	public static void register(String group, Object o) {
		theParser.doRegister(group, o);
	}

	@Deprecated
	// Don't use the static methods
	public static void registerAll(Object[] objects) {
		theParser.doRegisterAll(objects);
	}

	private static Class classOf(Object o) {
		return (o instanceof Class) ? (Class) o : o.getClass();
	}

	private List<OptInfo> matchOpt(ArrayList<OptInfo> options, String s,
			boolean allowMultipleMatches) {
		s = s.toLowerCase();

		ArrayList<OptInfo> completeMatches = new ArrayList<OptInfo>();
		ArrayList<OptInfo> partialMatches = new ArrayList<OptInfo>();
		for (OptInfo opt : options) {
			String t;

			// First try to match full name
			t = opt.fullName().toLowerCase();
			if (t.equals(s))
				completeMatches.add(opt);
			if (t.startsWith(s))
				partialMatches.add(opt);

			// Otherwise, match name (without the group)
			if (!mustMatchFullName) {
				t = opt.name.toLowerCase();
				if (t.equals(s))
					completeMatches.add(opt);
				if (t.startsWith(s))
					partialMatches.add(opt);
			}
		}

		if (completeMatches.size() + partialMatches.size() == 0) {
			if (!ignoreUnknownOpts)
				stderr.println("Unknown option: '" + s + "'; -help for usage");
			return ListUtils.newList();
		}

		if (allowMultipleMatches)
			return partialMatches;
		else {
			// Enforce one match
			if (completeMatches.size() == 1)
				return ListUtils.newList(completeMatches.get(0));
			if (completeMatches.size() == 0 && partialMatches.size() == 1)
				return ListUtils.newList(partialMatches.get(0));

			stderr.println("Ambiguous option: '" + s + "'; possible matches:");
			for (OptInfo opt : partialMatches)
				opt.print();
			return ListUtils.newList();
		}
	}

	private static void printHelp(List<OptInfo> options) {
		stdout.println("Usage:");
		for (OptInfo opt : options)
			opt.print();
	}

	public void printHelp() {
		printHelp(options);
	}

	private ArrayList<OptInfo> getOptInfos() {
		ArrayList<OptInfo> options = new ArrayList<OptInfo>();

		// For each group...
		for (String group : objects.keySet()) {
			Object obj = objects.get(group);

			// For each field that has an option annotation...
			// for(Field field : classOf(obj).getDeclaredFields()) {
			for (Field field : classOf(obj).getFields()) {
				Option ann = (Option) field.getAnnotation(Option.class);
				if (ann == null)
					continue;

				// Get the option
				OptInfo opt = new OptInfo();
				opt.group = group;
				opt.name = ann.name().equals("") ? field.getName() : ann.name();
				opt.gloss = ann.gloss();
				opt.condReq = ann.condReq();
				opt.required = ann.required();
				opt.obj = obj;
				opt.field = field;
				options.add(opt);

				// System.out.println("OPT " + opt.name);
			}

			// In Scala, "@Option var x" generates two methods
			// a setter and a getter
			// public int Options.x()
			// public void Options.x_$eq(int)

			// Map getter method name to the option
			HashMap<String, OptInfo> optMap = new HashMap();
			for (Method method : classOf(obj).getMethods()) {
				Option ann = (Option) method.getAnnotation(Option.class);
				if (ann == null)
					continue;

				// System.out.println("OPT " + method);
				String getterName = method.getName().replace("_$eq", "");
				OptInfo opt = optMap.get(getterName);
				if (opt == null) {
					opt = new OptInfo();
					opt.group = group;
					opt.name = ann.name().equals("") ? method.getName() : ann
							.name();
					opt.gloss = ann.gloss();
					opt.condReq = ann.condReq();
					opt.required = ann.required();
					opt.obj = obj;
					options.add(opt);
					optMap.put(getterName, opt);
				}

				// Get the option
				if (method.getName().endsWith("_$eq")) // setter
					opt.setMethod = method;
				else
					// getter
					opt.getMethod = method;
			}
		}

		for (OptInfo opt : options) {
			if (!(opt.field != null || (opt.getMethod != null && opt.setMethod != null)))
				System.err
						.printf("%s must have either field or a getter/setter pair (probably missing setter; use var instead of val in Scala)\n",
								opt.fullName());
		}

		return options;
	}

	// Options file: one option per line
	// Key and value separated by tab (or spaces).
	private boolean readOptionsFile(ArrayList<OptInfo> options, String file) {
		if (new File(file).isDirectory())
			file = new File(file, defaultDirFileName).toString();
		boolean ignoreOpts = new File(file).getName()
				.equals(ignoreOptsFileName);

		try {
			// OrderedStringMap map = OrderedStringMap.fromFile(file);
			// {12/06/08}: Allow spaces

			BufferedReader in = IOUtils.openIn(file);
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0 || line.startsWith("#"))
					continue;
				String[] tokens = line.split("\\s+", 2);
				String key = tokens[0];
				String val = (tokens.length > 1 ? tokens[1] : "");

				boolean append = false;

				if (key.startsWith("+")) {
					append = true;
					key = key.substring(1);
				}

				if (key.equals("!include")) { // Include other file
					if (!readOptionsFile(options, val))
						return false;
				} else {
					for (OptInfo opt : matchOpt(options, key, false)) {
						if (ignoreOpts
								&& ignoreFileNameOpts.contains(opt.fullName()))
							continue;
						if (!opt.set(Arrays.asList(StrUtils.split(val)), append))
							return false;
					}
				}
			}
		} catch (IOException e) {
			stderr.println(e);
			return false;
		}
		return true;
	}

	public boolean parseOptionsFile(String path) {
		ArrayList<OptInfo> options = getOptInfos();
		return readOptionsFile(options, path);
	}

	// Return true iff x is a strict prefix of
	private static boolean isStrictPrefixOf(String x, String... ys) {
		for (String y : ys)
			if (x.startsWith(y) && x.length() > y.length())
				return true;
		return false;
	}

	private static String stripDashes(String s) {
		int i = 0;
		while (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+'))
			i++;
		return s.substring(i);
	}

	@Deprecated
	public static boolean parse(String[] args) {
		return theParser.doParse(args);
	}

	public void doParseHard(String[] args) {
		if (!doParse(args))
			throw new RuntimeException("Parsing '" + StrUtils.join(args)
					+ "' failed");
	}

	public boolean doParse(String[] args) {
		if (this.options == null)
			this.options = getOptInfos();

		// For each command-line argument...
		for (int i = 0; i < args.length;) {
			if (args[i].equals("-help")) { // Get usage help
				printHelp(options);
				i++;
				return false;
				// if(!ignoreUnknownOpts) continue;
				// else return false;
			} else if (isStrictPrefixOf(args[i], "++")) {
				if (!readOptionsFile(options, args[i++].substring(2))) {
					if (ignoreUnknownOpts)
						continue;
					else
						return false;
				}
			} else if (isStrictPrefixOf(args[i], "-", "+", "--")) {
				boolean append = args[i].startsWith("+");
				boolean allowMultipleMatches = args[i].startsWith("--");
				// System.err.println(allowMultipleMatches + " " + args[i]);
				List<OptInfo> opts = matchOpt(options, stripDashes(args[i++]),
						allowMultipleMatches);

				// Get the data values of this parameter
				ArrayList<String> l = new ArrayList<String>();
				boolean nextIsVerbatim = false;
				boolean allIsVerbatim = false;
				while (i < args.length) {
					if (args[i].equals("--"))
						nextIsVerbatim = true;
					else if (args[i].equals("---"))
						allIsVerbatim = !allIsVerbatim;
					else {
						if (!allIsVerbatim && !nextIsVerbatim
								&& (isStrictPrefixOf(args[i], "+", "-", "++")))
							break;
						l.add(args[i]);
						nextIsVerbatim = false;
					}
					i++;
				}

				if (opts.size() == 0 && !ignoreUnknownOpts)
					return false;
				for (OptInfo opt : opts) {
					if (!opt.set(l, append)) {
						if (ignoreUnknownOpts)
							continue;
						else
							return false;
					}
				}
			} else {
				stderr.println("Argument not part of an option: " + args[i]);
				if (!ignoreUnknownOpts)
					return false;
			}
		}

		// Check that all required options are specified
		if (!relaxRequired) {
			List<String> missingOptMsgs = new ArrayList<String>();
			for (OptInfo o : options) {
				String msg = isMissing(o, options);
				if (msg != null)
					missingOptMsgs.add(msg);
			}
			if (missingOptMsgs.size() > 0) {
				stderr.println("Missing required option(s):");
				for (String msg : missingOptMsgs)
					stderr.println(msg);
				return false;
			}
		}

		return true;
	}

	// Return the option info with the given name (which could be full or not).
	// If not, then prepend the given group.
	private OptInfo findOptInfo(List<OptInfo> optInfos, String name,
			String group) {
		for (OptInfo info : optInfos)
			if (info.fullName().equals(name))
				return info;
		name = group + "." + name;
		for (OptInfo info : optInfos)
			if (info.fullName().equals(name))
				return info;
		return null;
	}

	// If the option is missing, return the message (to be printed out) of why
	// Otherwise, return null
	private String isMissing(OptInfo o, List<OptInfo> optInfos) {
		if (o.specified)
			return null; // Specified, we're fine
		if (o.required)
			return o.toString(); // This option is required
		if (!StrUtils.isEmpty(o.condReq)) {
			// This option is conditionally required
			String[] tokens = o.condReq.split("=", 2);
			String name = tokens[0], value = tokens.length == 2 ? tokens[1]
					: null;
			OptInfo info = findOptInfo(optInfos, name, o.group);
			boolean missing;
			if (info == null) // Shouldn't happen, but if it does, the user will
								// be notified
				return o.toString() + ", " + name + " not found";
			else if (value == null) { // Just need to be specified
				if (info.specified)
					return o.toString() + ", " + name + " specified";
			} else {
				if (info.getValue() instanceof ArrayList) { // For an array,
															// suffices if just
															// one element
															// matches
					for (Object x : (ArrayList) info.getValue())
						if (x.toString().matches(value))
							return o.toString() + ", " + o.condReq + " holds";
				} else {
					if (info.getValueString().matches(value))
						return o.toString() + ", " + o.condReq + " holds";
				}
			}
		}
		return null;
	}

	// Return a list of options (verbose - human-readable)
	@Deprecated
	public static OrderedStringMap getOptionStrings() {
		return theParser.doGetOptionStrings();
	}

	public OrderedStringMap doGetOptionStrings() {
		if (this.options == null)
			this.options = getOptInfos();
		OrderedStringMap map = new OrderedStringMap();
		for (OptInfo opt : options)
			map.put(opt.toString());
		return map;
	}

	// Return a list of option pairs (mapping name to value)
	@Deprecated
	public static OrderedStringMap getOptionPairs() {
		return theParser.doGetOptionPairs();
	}

	public OrderedStringMap doGetOptionPairs() {
		if (this.options == null)
			this.options = getOptInfos();
		OrderedStringMap map = new OrderedStringMap();
		for (OptInfo opt : options)
			map.put(opt.fullName(), opt.getValueString());
		return map;
	}

	public boolean writeEasy(String path) {
		return doGetOptionPairs().printEasy(path);
	}

	public OptionsParser setDefaultDirFileName(String defaultDirFileName) {
		this.defaultDirFileName = defaultDirFileName;
		return this;
	}

	public OptionsParser setIgnoreOptsFromFileName(String ignoreOptsFileName,
			List<String> ignoreFileNameOpts) {
		this.ignoreOptsFileName = ignoreOptsFileName;
		this.ignoreFileNameOpts = ignoreFileNameOpts;
		return this;
	}

	public OptionsParser relaxRequired() {
		this.relaxRequired = true;
		return this;
	}

	public OptionsParser ignoreUnknownOpts() {
		this.ignoreUnknownOpts = true;
		return this;
	}

	public OptionsParser mustMatchFullName() {
		this.mustMatchFullName = true;
		return this;
	}

	// public String getHotSpec() { return hotSpec; }

	// Each object could either be a class or an object.
	private HashMap<String, Object> objects = new HashMap<String, Object>();
	private ArrayList<OptInfo> options;
	// private String hotSpec;

	// Settings for parsing
	private String defaultDirFileName; // If ++<dir> is specified, read from
										// <dir>/<defaultDirFileName>
	private String ignoreOptsFileName; // If reading a file with this file
										// name...
	private List<String> ignoreFileNameOpts; // ignore these options

	private boolean relaxRequired; // Forget about having to have all options
	private boolean ignoreUnknownOpts; // Don't stop parsing if have error
	private boolean mustMatchFullName; // Must include group and name

	@Deprecated
	public static final OptionsParser theParser = new OptionsParser();
}
