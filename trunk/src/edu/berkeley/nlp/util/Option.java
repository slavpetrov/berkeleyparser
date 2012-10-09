package edu.berkeley.nlp.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {
	String name() default "";

	String gloss() default "";

	boolean required() default false;

	// Conditionally required option, e.g.
	// - "main.operation": required only when main.operation specified
	// - "main.operation=op1": required only when main.operation takes on value
	// op1
	// - "operation=op1": the group of the option is used
	String condReq() default "";
}
