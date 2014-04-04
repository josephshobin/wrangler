package wrangler.api

import scalaz._, Scalaz._

object Giter8 {
  def deployTemplate(template: String, name: String, params: Map[String, String]): String \/ String = {
    val cmd = Seq("g8", template, s"--name=$name") ++ params.toList.map { case (k, v) => s"--$k=$v" }

    for {
      _ <- Util.run(cmd)
      _ <- Util.run(List("chmod" , "-x", s"$name/sbt"))
    } yield s"Created $name"
  }
}
