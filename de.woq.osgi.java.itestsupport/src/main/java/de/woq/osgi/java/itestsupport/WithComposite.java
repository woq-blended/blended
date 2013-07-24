package de.woq.osgi.java.itestsupport;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value = RetentionPolicy.RUNTIME)
public @interface WithComposite {
  String location() default "classpath:woq-common.composite";
  long delay() default 5000l;
}
