import sbt._

case class JSProjectSettings() {

  def libDependencies : Def.Initialize[Seq[librarymanagement.ModuleID]] = Def.setting(Seq.empty)
}
