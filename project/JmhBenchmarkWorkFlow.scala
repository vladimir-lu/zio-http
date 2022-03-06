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

  def lists = sortedlist.map(str => {
    val l = List(s"""sbt -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | tee result_${str}""",
      s"""RESULT_${str}=$$(echo $$(grep "thrpt" result_${str})""",
      s"""echo "$$RESULT_REQUEST_${str}"""",
      """IFS=' ' read -ra PARSED_RESULT <<< "$RESULT"""",
      s"""echo ::set-output name=benchmark_result_${str}::$$(echo $${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]})""")

    WorkflowStep.Run(
      env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
      commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt") ++ l,
      id = Some(s"result_${str}"),
      name = Some(s"result_${str}"),
    )
  })

  def result: String = sortedlist.map(str => {
    s"""$${{steps.result_${str}.outputs.benchmark_result_${str}}}"""
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
        )) ++ lists ++ List(
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          params = Map(
            "sha"  -> "${{github.sha}}",
            "body" ->
              s"""
                |**\uD83D\uDE80 Jmh Benchmark:**
                |
                |${result}""".stripMargin,
          ),
        ),
      )
//        WorkflowStep.Run(
//          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
//          id = Some("clean_up"),
//          name = Some("Clean up"),
//          commands = List("sudo rm -rf *"),
//        ),
//        WorkflowStep.Use(
//          UseRef.Public("actions", "checkout", s"main"),
//          Map(
//            "ref" -> "main",
//            "path" -> "zio-http"
//          ),
//        ),
//        WorkflowStep.Use(
//          UseRef.Public("actions", "setup-java", s"v2"),
//          Map(
//            "distribution" -> "temurin",
//            "java-version" -> "8"
//          ),
//        ),
//        WorkflowStep.Run(
//          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
//          commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt") ++ l ++ output,
//          id = Some("jmh_main"),
//          name = Some("jmh_main"),
//        )
//      ),
//    ),

  )
  )

  def apply(): Seq[WorkflowJob] = jmhBenchmark()

}