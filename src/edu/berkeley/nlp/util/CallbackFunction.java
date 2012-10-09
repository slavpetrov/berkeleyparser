package edu.berkeley.nlp.util;

public interface CallbackFunction {
	public void callback(Object... args);

	public static class NullCallbackFunction implements CallbackFunction {

		public void callback(Object... args) {
			// Do-Nothing
		}

	}
}
