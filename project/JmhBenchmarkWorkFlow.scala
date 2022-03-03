import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}



object JmhBenchmarkWorkFlow {


  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""

  val scalaSources: PathFilter = ** / "*.scala"

  val files =
    FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources).map(_._1.toString)

  val classes = files.map(_.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala",""))
  val c = classes.map(f => s"""sbt -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $f" """)
  val batchSize = c.size/3
  val lists = c.grouped(batchSize).toList
  def apply(): Seq[WorkflowJob] = lists.map( list =>
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = s"runJmhBenchMarks ${list.take(1)}.",
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
          commands = List("cd zio-http") ++ list,
          id = Some("jmh"),
          name = Some("jmh"),
        ),
      ),
    ),
  )



}