package blended.itest.runner

trait TestSelector {
  def selectTest(f : List[TestTemplate], s : List[TestSummary]) : Option[TestTemplate]
}
