import java.nio.file.Path

import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.{FileAttributes, FileTreeView}
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkFlow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "${JmhVersion}")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files: Seq[(Path, FileAttributes)] = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources)

  def lists = files.zipWithIndex.map((file: ((Path, FileAttributes), Int)) => {
    val path = file._1._1.toString
    val str = path.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
    (List(s"""sbt -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | tee result${file._2}""",
      s"""RESULT_REQUEST${file._2}=$$(echo $$(grep "thrpt" result${file._2}))""",
      s"""echo "$$RESULT_REQUEST${file._2}"""",
      s"""echo ::set-output name=benchmark_result${file._2}::$$(echo "$$RESULT_REQUEST${file._2}")"""),str)
  })

//   def jmh(b: Int) = lists.grouped(b).toList


  def jmhBenchmark() = lists.map((x: (List[String], String)) =>
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = s"run_Jmh_BenchMarks_${x._2}",
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
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt") ++ x._1,
          id = Some("result"),
          name = Some("result"),
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          params = Map(
            "sha"  -> "${{github.sha}}",
            "body" ->
              s"""
                |**\uD83D\uDE80 Jmh Benchmark:**
                |
                |$${{steps.result.outputs.benchmark_result_${x._2}}}""".stripMargin,
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