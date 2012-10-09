package edu.berkeley.nlp.util.functional;

/**
 * Created by IntelliJ IDEA. User: aria42 Date: Oct 9, 2008 Time: 6:32:13 PM
 */
public class Predicates {
	public static <I> Predicate<I> getTruePredicate() {
		return new Predicate<I>() {
			public Boolean apply(I input) {
				return true;
			}
		};
	}

	public static <I> Predicate<I> getInversePredicate(final Predicate<I> pred) {
		return new Predicate<I>() {
			public Boolean apply(I input) {
				return !pred.apply(input);
			}
		};
	}

	public static <I> Predicate<I> getOrPredicate(final Predicate<I>... preds) {
		return new Predicate<I>() {
			public Boolean apply(I input) {
				for (Predicate<I> pred : preds) {
					if (pred.apply(input))
						return true;
				}
				return false;
			}
		};
	}

	public static Predicate getNonNullPredicate() {
		return new Predicate() {
			public Boolean apply(Object input) {
				return input != null;
			}
		};
	}

	public static <I> Predicate<I> getAndPredicate(final Predicate<I>... preds) {
		return new Predicate<I>() {
			public Boolean apply(I input) {
				for (Predicate<I> pred : preds) {
					if (!pred.apply(input))
						return false;
				}
				return true;
			}
		};
	}
}
