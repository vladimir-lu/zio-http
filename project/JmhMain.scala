import BuildHelper.{JmhVersion, Scala213}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhMain {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""

  val jmhBenchmark = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = s"runJmhMain",
      name = "JmhBenchmarks",
      oses = List("centos"),
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", s"main"),
          Map(
            "ref" -> "main",
          ),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", s"v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8"
          ),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt"),
          id = Some("jmh_main"),
          name = Some("jmh_main"),
        )
      ),
    ),
  )


  def apply(): Seq[WorkflowJob] = jmhBenchmark

}