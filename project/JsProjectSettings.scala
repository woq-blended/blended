import sbt._

case class JsProjectSettings() {

  def libDependencies : Def.Initialize[Seq[librarymanagement.ModuleID]] = Def.setting(Seq.empty)
}
