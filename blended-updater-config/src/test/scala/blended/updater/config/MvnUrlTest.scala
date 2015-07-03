package blended.updater.config

import org.scalatest.FreeSpec
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class MvnUrlTest extends FreeSpec {

  "toUrl" - {

    def ok(mvn: MvnGav, url: String) = {
      s"shold process ${mvn}" in {
        assert(mvn.toUrl("") === url)
        assert(mvn.toUrl("http://org.example/repo1") === s"http://org.example/repo1/${url}")
        assert(mvn.toUrl("http://org.example/repo1/") === s"http://org.example/repo1/${url}")
      }
    }

    ok(MvnGav("g", "a", "1"), "g/a/1/a-1.jar")
    ok(MvnGav("a.b.c", "d.e.f", "1"), "a/b/c/d.e.f/1/d.e.f-1.jar")
    ok(MvnGav("a.b.c", "d.e.f", "1", fileExt = "zip"), "a/b/c/d.e.f/1/d.e.f-1.zip")
    ok(MvnGav("a.b.c", "d.e.f", "1", Some("test")), "a/b/c/d.e.f/1/d.e.f-1-test.jar")
    ok(MvnGav("a.b.c", "d.e.f", "1", Some("etc"), "zip"), "a/b/c/d.e.f/1/d.e.f-1-etc.zip")
    ok(MvnGav("a.b.c", "d.e.f", "1", None, "war"), "a/b/c/d.e.f/1/d.e.f-1.war")
    ok(MvnGav("a.b.c", "d.e.f", "1", Some("jar"), "jar"), "a/b/c/d.e.f/1/d.e.f-1.jar")

  }

  "parse" - {
    def ok(gav: String, mvn: MvnGav) = {
      s"should parse ${gav} to ${mvn}" in {
        assert(MvnGav.parse(gav) === Success(mvn))
      }
    }

    def notOk(gav: String) = {
      s"should not parse ${gav}" in {
        assert(MvnGav.parse(gav).isInstanceOf[Failure[_]])
      }
    }

    ok("g:a:1", MvnGav("g", "a", "1", None, "jar"))
    ok("g:a:pom:1", MvnGav("g", "a", "1", Some("pom"), "pom"))
    ok("g:a:pom:1:pom", MvnGav("g", "a", "1", Some("pom"), "pom"))
    ok("g:a:jdk16:1", MvnGav("g", "a", "1", Some("jdk16"), "jar"))
    ok("g:a::1:war", MvnGav("g", "a", "1", None, "war"))
    ok("g:a:jar:1", MvnGav("g", "a", "1", None, "jar"))

    notOk("g:a")
    notOk("a")
    notOk("g:a:1:")
    notOk("a:b:c:d:e:")
  }

}