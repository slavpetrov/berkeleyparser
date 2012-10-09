package edu.berkeley.nlp.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

public class MemoryUtils {

	public static double getHeapMemoryUsed() {
		MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
		MemoryUsage usage = bean.getHeapMemoryUsage();
		long bytes = usage.getUsed();
		return bytes / 1.0e6;
	}

}
