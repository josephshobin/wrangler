//   Copyright 2014 Commonwealth Bank of Australia
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package wrangler.api

import java.io.File

import scala.util.control.NonFatal

import scalaz._, Scalaz._

import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.api.ResetCommand.ResetType._
import org.eclipse.jgit.api.errors._
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport._
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.util.FS

import com.jcraft.jsch.Session

import com.jcraft.jsch.agentproxy.Connector
import com.jcraft.jsch.agentproxy.AgentProxyException
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository
import com.jcraft.jsch.agentproxy.ConnectorFactory

/** API around git.*/
object Git {
  /** SSH Configuration for git that uses the ssh agent credentials.*/
  class CustomConfigSessionFactory extends JschConfigSessionFactory {
    override protected def configure(host: Host, session: Session) {
      session.setConfig("StrictHostKeyChecking", "no")
    }

    override protected def getJSch(hc: Host, fs: FS ) = {
      val jsch = super.getJSch(hc, fs)
      val con  = ConnectorFactory.getDefault().createConnector()
      jsch.setIdentityRepository(new RemoteIdentityRepository(con))
      jsch
    }
  }

  //Use ssh agent
  SshSessionFactory.setInstance(new CustomConfigSessionFactory)

  // Use .netrc credentials
  val netrccp = new NetrcCredentialsProvider()

  /** Clones the given repo to the specified destination.*/
  def clone(src: String, dst: String): Git[JGit] = clone(src, new File(dst))

  /** Clones the given repo to the specified destination.*/
  def clone(src: String, dst: File): Git[JGit] = {
    val correctSrc = if (!src.endsWith(".git")) src ++ ".git" else src

    \/.fromTryCatch {
    JGit
      .cloneRepository()
      .setCredentialsProvider(netrccp)
      .setURI(correctSrc)
      .setDirectory(dst)
      .call()
    }
  }

  /** Force updates the master branch with the changes on orgin/master.*/
  def update(repo: JGit): Git[JGit] = \/.fromTryCatch {
    repo
      .fetch
      .setCredentialsProvider(netrccp)
      .setRemote("origin")
      .call()
    
    repo
      .reset()
      .setMode(HARD)
      .setRef("origin/master")
      .call()

    repo
  }

  /** Opens an existing on disk repo.*/
  def open(path: String): Git[JGit] = \/.fromTryCatch {
    new JGit(new RepositoryBuilder().setWorkTree(new File(path)).build)
  }

  /** Pushs the specified branch to a specified remote.*/
  def push(branch: String, remote: String = "origin")(repo: JGit): Git[JGit] = \/.fromTryCatch {
    repo
      .push
      .setCredentialsProvider(netrccp)
      .add(branch)
      .setRemote(remote)
      .call

    repo
  }

  /** Creates a new branch with the specified parent and checks it out.*/
  def createBranch(branch: String, parent: String)(repo: JGit): Git[JGit] =
    \/.fromTryCatch {
      repo
        .checkout
        .setCreateBranch(true)
        .setName(branch)
        .setStartPoint("origin/" + parent)
        .call

      repo
    }

  /** Adds a new named remote repo.*/
  def addRemote(name: String, url: String)(repo: JGit): Git[JGit] = \/ fromTryCatch {
    val conf = repo.getRepository.getConfig
    val remoteConf = new RemoteConfig(conf, name)
    val correctUrl = if (url.startsWith("ssh://")) url ++ ".git" else url
    remoteConf.addURI(new URIish(correctUrl))
    remoteConf.update(conf)
    conf.save

    repo
  }

  /** Does a `git add` for the given pattern.*/
  def add(pattern: String)(repo: JGit): Git[JGit] = \/ fromTryCatch {
    repo.add.addFilepattern(pattern).call
    repo
  }

  /** Commits changes.*/
  def commit(message: String)(repo: JGit): Git[JGit] = \/ fromTryCatch {
    repo.commit.setMessage(message).call
    repo
  }
}
