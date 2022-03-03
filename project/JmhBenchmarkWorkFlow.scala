import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkFlow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files =
    FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources).map(_._1.toString)
  val batchSize = files.size/3
  val list = files.map( s => {
    val str = s.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
    s"""sbt -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" """
  }).grouped(batchSize).toList

  def apply(): Seq[WorkflowJob] = list.map( l =>
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = s"runJmhBenchMarks ${l.take(1).hashCode()}.",
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



}