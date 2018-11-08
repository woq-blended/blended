package blended.testsupport

import java.lang.annotation.{ElementType, Retention, RetentionPolicy, Target}

import org.scalatest.TagAnnotation

@TagAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD, ElementType.TYPE)
trait RequiresForkedJVM {
}
