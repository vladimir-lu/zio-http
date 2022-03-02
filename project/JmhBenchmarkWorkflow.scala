import BuildHelper.Scala213
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkflow {
  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runJmhBenchMarks",
      name = "JmhBenchmarks",
      oses = List("centos"),
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          id = Some("result"),
          commands = List(
            """echo new job"""
          ),
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
