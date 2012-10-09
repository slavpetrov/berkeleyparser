package edu.berkeley.nlp.util.functional;

/**
 * Created by IntelliJ IDEA. User: aria42 Date: Oct 9, 2008 Time: 6:31:23 PM
 */
public interface Function<I, O> {
	public O apply(I input);

	public static class ConstantFunction<I, O> implements Function<I, O> {

		private O c;

		public ConstantFunction(O c) {
			this.c = c;
		}

		public O apply(I input) {
			return c;
		}
	}

	public static class IdentityFunction<I> implements Function<I, I> {

		public I apply(I input) {
			return input;
		}
	}

}
