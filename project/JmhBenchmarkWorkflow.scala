import BuildHelper.{JmhVersion, Scala213}
import sbtghactions.GenerativePlugin.autoImport.{WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkflow {

  val jmhPlugin        = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val jmhDirectivesBase = """(project in file("./zio-http"))"""
  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runJmhBenchMarks",
      name = "JmhBenchmarks",
      oses = List("centos"),
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Run(
          commands = List(s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt"),
          id = Some("add_plugin"),
          name = Some("Add jmh plugin"),
        ),
        WorkflowStep.Run(
          commands = List(
            s"\nsed -i -e 's+${jmhDirectivesBase}' build.sbt",
          ),
          id = Some("update_build_definition"),
          name = Some("Update Build Definition"),
        ),
        WorkflowStep.Sbt(
          commands = List(s"zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 HttpCombineEval"),
          id = Some("jmh"),
          name = Some("jmh"),
        ),
      ),
    ),
  )

}
