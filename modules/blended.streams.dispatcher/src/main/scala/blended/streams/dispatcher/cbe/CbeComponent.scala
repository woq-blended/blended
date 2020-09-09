package blended.streams.dispatcher.cbe

case class CbeComponent(
  application : String,
  component : String,
  location : String,
  locationType : String,
  subComponent : String,
  threadId : String,
  componentType : String,
  instanceId : Int
) {

  val element : String =
    s"""
       | <sourceComponentId
       |   component="$component"
       |   componentIdType="$application"
       |   location="$location"
       |   locationType="$locationType"
       |   subComponent="$subComponent"
       |   instanceId="$instanceId"
       |   threadId="false"
       |   componentType="$componentType" />
      """.stripMargin
}
