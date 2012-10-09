package edu.berkeley.nlp.PCFGLA;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OptionParser {

	private final Map<String, Option> nameToOption = new HashMap<String, Option>();

	private final Map<String, Field> nameToField = new HashMap<String, Field>();

	private final Set<String> requiredOptions = new HashSet<String>();

	private final Class optionsClass;

	private StringBuilder passedInOptions;

	public OptionParser(Class optionsClass) {
		this.optionsClass = optionsClass;
		for (Field field : optionsClass.getDeclaredFields()) {
			Option option = field.getAnnotation(Option.class);
			if (option == null) {
				continue;
			}
			nameToOption.put(option.name(), option);
			nameToField.put(option.name(), field);
			if (option.required()) {
				requiredOptions.add(option.name());
			}
		}
	}

	private void usage() {
		System.err.println();
		for (Option opt : nameToOption.values()) {
			System.err.printf("%-30s%s", opt.name(), opt.usage());
			if (opt.required()) {
				System.err.printf(" [required]");
			}
			System.err.println();
		}
		System.err.printf("%-30shelp message\n", "-h");
		System.err.println();
		System.exit(2);
	}

	public Object parse(String[] args) {
		return parse(args, false, false);
	}

	public Object parse(String[] args, boolean failOnUnrecognized) {
		return parse(args, failOnUnrecognized, false);
	}

	public Object parse(String[] args, boolean failOnUnrecognized,
			boolean parrot) {
		if (parrot)
			System.out.println("Calling with " + Arrays.deepToString(args));
		try {
			Set<String> seenOpts = new HashSet<String>();
			passedInOptions = new StringBuilder("{");
			Object options = optionsClass.newInstance();
			for (int i = 0; i < args.length; ++i) {
				if (args[i].equals("-h")) {
					usage();
				}
				Option opt = nameToOption.get(args[i]);
				if (opt == null) {
					if (failOnUnrecognized) {
						throw new RuntimeException("Did not recognize option "
								+ args[i]);
					} else {
						System.err.println("WARNING: Did not recognize option "
								+ args[i]);
					}
					continue;
				}
				seenOpts.add(args[i]);
				Field field = nameToField.get(args[i]);
				Class fieldType = field.getType();
				// If option is boolean type then
				// we set the associate field to true
				if (fieldType == boolean.class) {
					field.setBoolean(options, true);
					passedInOptions.append(String.format(" %s => true",
							opt.name()));
				}
				// Otherwise look at next arg and
				// set field to that value
				// this will automatically
				// convert String to double or
				// whatever
				else {
					String value = args[i + 1];
					if (value != null)
						value.trim();
					passedInOptions.append(String.format(" %s => %s",
							opt.name(), value));
					if (fieldType == int.class) {
						field.setInt(options, Integer.parseInt(value));
					} else if (fieldType == double.class) {
						field.setDouble(options, Double.parseDouble(value));
					} else if (fieldType == float.class) {
						field.setFloat(options, Float.parseFloat(value));
					} else if (fieldType == short.class) {
						field.setFloat(options, Short.parseShort(value));
					} else if (fieldType.isEnum()) {
						Object[] possibleValues = fieldType.getEnumConstants();
						boolean found = false;
						for (Object possibleValue : possibleValues) {

							String enumName = ((Enum) possibleValue).name();
							if (value.equals(enumName)) {
								field.set(options, possibleValue);
								found = true;
								break;
							}

						}
						if (!found) {
							if (failOnUnrecognized) {
								throw new RuntimeException(
										"Unrecognized enumeration option "
												+ value);
							} else {
								System.err
										.println("WARNING: Did not recognize option Enumeration option "
												+ value);
							}
						}
					} else if (fieldType == String.class) {
						field.set(options, value);
					} else {
						try {
							Constructor constructor = fieldType
									.getConstructor(new Class[] { String.class });
							field.set(
									options,
									constructor
											.newInstance((Object[]) (new String[] { value })));
						} catch (NoSuchMethodException e) {
							System.err
									.println("Cannot construct object of type "
											+ fieldType.getCanonicalName()
											+ " from just a string");
						}
					}
					++i;
				}
			}
			passedInOptions.append(" }");

			Set<String> optionsLeft = new HashSet<String>(requiredOptions);
			optionsLeft.removeAll(seenOpts);
			if (!optionsLeft.isEmpty()) {
				System.err.println("Failed to specify: " + optionsLeft);
				usage();
			}

			return options;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getPassedInOptions() {
		return passedInOptions.toString();
	}

}
