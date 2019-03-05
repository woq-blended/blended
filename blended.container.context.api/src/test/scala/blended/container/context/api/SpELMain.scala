package blended.container.context.api

import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

object SpELMain {

  def left(s : String, n : Int) : String = {
    if (s.length <= n) {
      s
    } else {
      s.takeRight(n)
    }
  }

  def main(args : Array[String]) : Unit = {

    val parser = new SpelExpressionParser()
    val context = new StandardEvaluationContext()

    val methods = classOf[SpelFunctions].getDeclaredMethods()

    methods.foreach { m =>
      println("Registering method " + m.getName())

      context.registerFunction(m.getName(), m)
    }

    val hello = parser.parseExpression("#capitalize('hello  andreas')")
    //val exp = parser.parseExpression("'Hallo Andreas'.length()")

    println(hello.getValue(context))
  }
}
