package wrangler.commands.args

import com.quantifind.sumac.validation._

/** Arguments to use either Github or Stash repo.*/
trait StashOrGithubArgs extends WranglerArgs with RepoArgs {
  /** Use Github.*/
  var useGithub:     Boolean = false

  /** Use Stash.*/
  var useStash:      Boolean = false

  addValidation {
    require(!useGithub || ogithub.isDefined, "Need to provide github configuration to use github")
    require(!useStash || ostash.isDefined, "Need to provide stash configuration to use stash")
    require(useStash || useGithub, "Need to specify to either use github or stash")
    require(!(useGithub && useStash), s"Can only use github OR stash repos. Not both at the same time")
  }
}
