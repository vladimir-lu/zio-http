import BuildHelper.{JmhVersion, Scala213}
import JmhBenchmarkWorkFlow.jmhPlugin
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkFlow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources)

  def lists(batchSize: Int) = files.map(file => {
    val path = file._1.toString
    val str = path.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
    s"""sbt -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" """
  }).sorted.grouped(batchSize).toList

  def jmhBenchmark(batchSize: Int) = lists(batchSize).map(l =>
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = s"runJmhBenchMarks${l.head.hashCode}",
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
          commands = List("cd zio-http") ++ l,
          id = Some("jmh"),
          name = Some("jmh"),
        ),
      ),
    ),
  )

  def jmhBenchmarkMain(batchSize: Int) = lists(batchSize).map(l =>
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = s"runJmhBenchMarksMain${l.head.hashCode}",
      name = "JmhBenchmarks main",
      oses = List("centos"),
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", s"v2"),
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
          id = Some("add_plugin"),
          name = Some("Add jmh plugin"),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List("cd zio-http") ++ l,
          id = Some("jmh"),
          name = Some("jmh"),
        ),
      ),
    )
  )

  def apply(batchSize: Int): Seq[WorkflowJob] = jmhBenchmark(batchSize) ++ jmhBenchmarkMain(batchSize)

}