package edu.berkeley.nlp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Semaphore;

/**
 * Runs stuff in a shell
 * 
 * @author denero
 */
public class Shell {

	private static class StreamGobbler extends Thread {
		InputStream is;
		String output;
		Semaphore semaphore;
		String prefix;
		boolean echo;

		StreamGobbler(InputStream is, String prefix, boolean echo) {
			this.is = is;
			semaphore = new Semaphore(1);
			semaphore.acquireUninterruptibly();
			this.echo = echo;
			this.prefix = prefix;
		}

		public void run() {
			StringBuilder outputBuilder = new StringBuilder();
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null) {
					outputBuilder.append(line + "\n");
					if (echo)
						System.out.println(prefix + line);
				}
			} catch (IOException ioe) {
				throw new RuntimeException(ioe);
			}
			output = outputBuilder.toString();
			semaphore.release();
		}

		public String getOutput() {
			semaphore.acquireUninterruptibly();
			semaphore.release();
			return output;
		}
	}

	public static String execute(String cmd) {
		return execute(cmd, false);
	}

	public static String execute(String cmd, boolean echo) {
		try {
			Process proc = Runtime.getRuntime().exec(cmd);
			StreamGobbler errorGobbler = new StreamGobbler(
					proc.getErrorStream(), "ERR> ", echo);
			StreamGobbler outputGobbler = new StreamGobbler(
					proc.getInputStream(), "OUT> ", echo);

			// kick them off
			errorGobbler.start();
			outputGobbler.start();

			proc.waitFor();
			return outputGobbler.getOutput();

		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
