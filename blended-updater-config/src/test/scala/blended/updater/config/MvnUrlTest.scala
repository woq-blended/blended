package blended.updater.config

import org.scalatest.FreeSpec

class MvnUrlTest extends FreeSpec {

  "toUrl works" in {
    assert(MvnGav("g", "a", "1").toUrl("") === "g/a/1/a-1.jar")
    assert(MvnGav("g", "a", "1").toUrl("http://org.example/repo1") === "http://org.example/repo1/g/a/1/a-1.jar")
    assert(MvnGav("g", "a", "1").toUrl("http://org.example/repo1/") === "http://org.example/repo1/g/a/1/a-1.jar")
    assert(MvnGav("a.b.c", "d.e.f", "1").toUrl("") === "a/b/c/d.e.f/1/d.e.f-1.jar")
    assert(MvnGav("a.b.c", "d.e.f", "1").toUrl("http://org.example/repo1") === "http://org.example/repo1/a/b/c/d.e.f/1/d.e.f-1.jar")
    assert(MvnGav("a.b.c", "d.e.f", "1").toUrl("http://org.example/repo1/") === "http://org.example/repo1/a/b/c/d.e.f/1/d.e.f-1.jar")
  }

}