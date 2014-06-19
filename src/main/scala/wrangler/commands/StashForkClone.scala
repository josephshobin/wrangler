package wrangler
package commands

import scalaz._, Scalaz._

import com.quantifind.sumac.ArgMain
import com.quantifind.sumac.validation._

import wrangler.api.{Stash, Repo, Git}
import wrangler.commands.args.StashArgs

/** Arguments for `StashForkClone`.*/
class StashForkCloneArgs extends StashArgs {
  val stash = new StashArgs{}

  /** Name of the repo.*/
  @Required
  var repo: String = _

  addValidation(require(stash.apiUrl != null, "Need to provide stash configuration"))
}

/**
  * Given an existing repo it forks the repo, enables fork sync, clones the repo and configures the
  * remotes.
  */
object StashForkClone extends ArgMain[StashForkCloneArgs] {
  case class Error(msg: String)

  type Result[T] = Error \/ T

  def liftRepo[T](result: Repo[T]): Result[T] = result.leftMap(e => Error(e.msg))
  def liftGit[T](result: Git[T]): Result[T] = result.leftMap(e => Error(e.toString))

  /** Runs the command.*/
  def main(args: StashForkCloneArgs): Unit = {
    implicit val apiUrl  = args.stash.tapiUrl
    implicit val user    = args.stash.tuser
    implicit val project = args.stash.tproject

    val repo   = args.repo
    val gitUrl = args.stash.gitUrl

    println(s"Forking $repo")
    val (initial, pass)  = Stash.retryUnauthorized(
      args.stash.tpassword,
      p => Stash.fork(repo)(project, apiUrl, user, p)
    )

    implicit val password = pass

    val result: Result[String] = for {
      _   <- initial |> liftRepo
      _   <- Stash.forkSync(repo) |> liftRepo
      _    = println(s"Git cloning $repo")
      git <- Git.clone(s"$gitUrl/~$user/$repo.git", repo) |> liftGit
      _   <- Git.addRemote("upstream", s"$gitUrl/$project/$repo")(git) |> liftGit
    } yield s"Forked and cloned $repo"

    println(result.fold(_.msg, identity))

    //Exit manually since dispatch hangs.
    sys.exit(0)
  }
}
