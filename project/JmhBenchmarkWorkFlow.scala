import java.nio.file.Path

import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.{FileAttributes, FileTreeView}
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkFlow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files: Seq[(Path, FileAttributes)] = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources)

  def sortedlist = files.map((file: (Path, FileAttributes)) => {
    val path = file._1.toString
   val str = path.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
    str
  }).sorted

  def lists(string: String) = sortedlist.map(str => {
    val l = List(s"""sbt -no-colors -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | tee ${str}""",
      s"""${str}=$$(echo $$(grep "thrpt" ${str}))""",
      s"""echo "$$${str}"""",
      s"""IFS=' ' read -ra PARSED_RESULT <<< "$$${str}"""",
      s"""echo ::set-output name=benchmark_${str}::$$(echo $${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]})""")

    WorkflowStep.Run(
      env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
      commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt") ++ l,
      id = Some(s"${string}_${str}"),
      name = Some(s"${string}_${str}"),
    )
  })

  def output(string: String): String = sortedlist.map(str => {
    s"""$${{steps.${string}_${str}.outputs.benchmark_${str}}}"""
  }).mkString("\n|")



  def jmhBenchmark() = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = s"run_Jmh_BenchMarks",
      name = "Jmh_Benchmarks",
      oses = List("centos"),
      scalas = List(Scala213),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", s"v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8"
          ),
        )) ++ lists("current") ++ List(
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          id = Some("clean_up"),
          name = Some("Clean up"),
          commands = List("sudo rm -rf *"),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", s"main"),
          Map(
            "ref" -> "main",
            "path" -> "zio-http"
          ),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", s"v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8"
          ),
        )) ++ lists("main") ++ List(
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          params = Map(
            "sha"  -> "${{github.sha}}",
            "body" ->
              s"""
                 |**\uD83D\uDE80 Jmh Benchmark:**
                 |
                 |- Current Branch:
                 |${output("current")}
                 |- Main Branch:
                 |${output("main")}""".stripMargin,
          ),
        ),
      ),
    ),

  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()

}