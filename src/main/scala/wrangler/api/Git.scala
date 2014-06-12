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

object Git {
  class CustomConfigSessionFactory extends JschConfigSessionFactory {
    override protected def configure(host: Host, session: Session) {
      session.setConfig("StrictHostKeyChecking", "no")
    }

    override protected def getJSch( hc : Host, fs : FS ) = {
      val jsch = super.getJSch(hc, fs)
      val con  =ConnectorFactory.getDefault().createConnector()
      jsch.setIdentityRepository(new RemoteIdentityRepository(con))
      jsch
    }
  }

  SshSessionFactory.setInstance(new CustomConfigSessionFactory)

  def clone(src: String, dst: String): Throwable \/ JGit = clone(src, new File(dst))
  def clone(src: String, dst: File): Throwable \/ JGit = \/.fromTryCatch {
    JGit
      .cloneRepository()
      .setURI(src)
      .setDirectory(dst)
      .call()
  }

  def update(repo: JGit): Throwable \/ JGit = \/.fromTryCatch {
    repo
      .fetch
      .setRemote("origin")
      .call()
    
    repo
      .reset()
      .setMode(HARD)
      .setRef("origin/master")
      .call()

    repo
  }

  def open(path: String): Throwable \/ JGit = \/.fromTryCatch {
    new JGit(new RepositoryBuilder().setWorkTree(new File(path)).build)
  }

  def push(repo: JGit, branch: String, remote: String = "origin"): Throwable \/ JGit = \/.fromTryCatch {
    repo
      .push
      .add(branch)
      .setRemote(remote)
      .call

    repo
  }

  def createBranch(repo: JGit, branch: String): Throwable \/ JGit = \/ fromTryCatch {
    repo
      .checkout
      .setCreateBranch(true)
      .setName(branch)
      .setStartPoint("master")
      .call

    repo
  }

  def addRemote(repo: JGit, name: String, url: String): Throwable \/ JGit = \/ fromTryCatch {
    val conf = repo.getRepository.getConfig
    val remoteConf = new RemoteConfig(conf, name)
    remoteConf.addURI(new URIish(url))
    remoteConf.update(conf)
    conf.save

    repo
  }

  def add(repo: JGit, pattern: String): Throwable \/ JGit = \/ fromTryCatch {
    repo.add.addFilepattern(pattern).call
    repo
  }

  def commit(repo: JGit, message: String): Throwable \/ JGit = \/ fromTryCatch {
    repo.commit.setMessage(message).call
    repo
  }
}
