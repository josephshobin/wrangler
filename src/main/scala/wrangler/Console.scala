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

package wrangler

import java.io.{InputStream, PrintStream, File}

import scalaz._

import jline.Terminal

import org.apache.felix.gogo.commands.{Action, Argument => argument, Command => command}
import org.apache.felix.gogo.runtime.CommandProcessorImpl
import org.apache.felix.service.command.CommandSession

import org.apache.karaf.shell.console.{jline, Main}

import org.fusesource.jansi.Ansi

object Console {
  def main(args: Array[String]) : Unit = {
    Ansi.ansi()
    new Console().run(args)
  }

  def ANSI(value:Any) =  "\u001B["+value+"m"
  val BOLD =  ANSI(1)
  val RESET = ANSI(0)
}

/**
 * Main command that initialises the console correctly (i.e. right prompt, loads the actions, etc).
 */
@command(scope = "wrangler", name = "wrangler", description = "Executes the wrangler command interpreter")
class Console extends Main with Action {
  import Console._

  setUser("me")
  setApplication("wrangler")

  override def getDiscoveryResource = "commands.index"
  override def isMultiScopeMode = false

  override def createConsole(commandProcessor: CommandProcessorImpl, in: InputStream, out: PrintStream, err: PrintStream, terminal: Terminal) = {
    new jline.Console(commandProcessor, in, out, err, terminal, "UTF-8", null) {
      protected override def getPrompt = BOLD+"wrangler> "+RESET
      protected override def welcome() = {
        session.getConsole.println("Welcome to the Wrangler Console")
      }
      protected override def setSessionProperties() = {}
    }
  }

  @argument(name = "args", description = "wrangler sub command arguments", multiValued = true)
  var args = Array[String]()

  def execute(session: CommandSession): AnyRef = {
    run(session, args)
    null
  }
}

trait FunctionalAction extends Action {
  /**
    * Executes the given function and outputs the result to console appropriately.
    * @param session the command session
    * @param thunk function to execute
    * @return null
    */
  def run(session: CommandSession)(thunk: => \/[String, String]): (CommandSession => Object) = thunk match {
    case \/-(exitMessage)   => {
      out(exitMessage)(session)
      System.exit(0)
      null
    }
    case -\/(error)  => {
      val console = session.getConsole
      out(s"There was an error when executing the command:\n$error")(session)
      System.exit(1)
      null
    }
  }

  def out(msg: String)(implicit session: CommandSession) =
    session.getConsole.println(msg)
}
