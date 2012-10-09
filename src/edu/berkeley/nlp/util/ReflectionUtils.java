package edu.berkeley.nlp.util;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by IntelliJ IDEA. User: aria42 Date: Dec 7, 2008
 */
public class ReflectionUtils {

	public static List<Class> getClassesForPackage(String pckgname) {
		List<Class> classes = new ArrayList<Class>();
		ArrayList<File> directories = new ArrayList<File>();
		char fileSep = System.getProperty("file.separator").charAt(0);
		try {
			ClassLoader cld = Thread.currentThread().getContextClassLoader();
			if (cld == null) {
				throw new ClassNotFoundException("Can't get class loader.");
			}
			Enumeration<URL> resources = cld.getResources(pckgname.replace('.',
					fileSep));
			while (resources.hasMoreElements()) {
				URL res = resources.nextElement();
				if (res.getProtocol().equalsIgnoreCase("jar")) {
					JarURLConnection conn = (JarURLConnection) res
							.openConnection();
					JarFile jar = conn.getJarFile();
					for (JarEntry e : Collections.list(jar.entries())) {
						if (e.getName().startsWith(
								pckgname.replace('.', fileSep))
								&& e.getName().endsWith(".class")
								&& !e.getName().contains("$")) {
							String className = e.getName()
									.replace("" + fileSep, ".")
									.substring(0, e.getName().length() - 6);
							classes.add(Class.forName(className));
						}
					}
				} else {
					directories.add(new File(URLDecoder.decode(res.getPath(),
							"UTF-8")));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		for (File directory : directories) {
			if (directory.exists()) {
				String[] files = directory.list();
				for (String file : files) {
					if (file.endsWith(".class")) {
						String className = file.substring(0, file.length() - 6);
						try {
							classes.add(Class.forName(pckgname + '.'
									+ className));
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		return classes;
	}

	public static List<Class> getClassessOfInterface(String thePackage,
			Class theInterface) {
		List<Class> classList = new ArrayList<Class>();
		try {
			for (Class discovered : getClassesForPackage(thePackage)) {
				if (Arrays.asList(discovered.getInterfaces()).contains(
						theInterface)) {
					classList.add(discovered);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return classList;
	}

}
