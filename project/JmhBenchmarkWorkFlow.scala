import BuildHelper.{JmhVersion, Scala213}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}


object JmhBenchmarkWorkFlow {


  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""

  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runJmhBenchMarks",
      name = "JmhBenchmarks",
      oses = List("centos"),
      scalas = List(Scala213),
      steps = List(
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
          id = Some("add_plugin"),
          name = Some("Add jmh plugin"),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List("cd zio-http", s"""sbt -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 CookieDecodeBenchmark""""),
          id = Some("jmh"),
          name = Some("jmh"),
        ),
      ),
    ),
  )


}