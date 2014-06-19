package wrangler.api

import java.io.File

import scalaz._, Scalaz._

import sbt._, Path._

import org.eclipse.jgit.transport._
import org.eclipse.jgit.errors.UnsupportedCredentialItem

case class BasicLogin(id: String, password: String)

/**
  * Parses netrc credentials to use for authentication.
  * By default it will parse `~/.netrc`
  */
object Netrc {
    val defaultPath = s"""${System.getProperty("user.home")}/.netrc"""

  def getLogin(host: String, path: String = defaultPath) : Option[BasicLogin] = {
    val file = new File(path)

    if (!file.exists) {println("doesn't exist"); None}
    else {
      val lines = IO.readLines(file).toArray
      val pos = lines.indexWhere(_.startsWith(s"machine $host"))

      if (pos == -1 || pos + 2 > lines.length) None
      else {
        if (lines(pos + 1).startsWith("login")) {
          val login = lines(pos + 1).drop("login".length + 1)

          if (lines(pos + 2).startsWith("password"))
            Some(BasicLogin(login, lines(pos + 2).drop("password".length + 1)))
          else None
        } else None
      }
    }
  }
}

/** Netrc credential provider for JGit.*/
class NetrcCredentialsProvider(path: String = Netrc.defaultPath) extends CredentialsProvider {
  override def isInteractive = false

  override def supports(items: CredentialItem*) =
    items.forall(i => i.isInstanceOf[CredentialItem.Username] || i.isInstanceOf[CredentialItem.Password])

  override def get(uri: URIish, items: CredentialItem*) = {
    val login = Netrc.getLogin(uri.getHost, path)

    login.cata(l => {
      items.foreach {
        case i: CredentialItem.Username => i.setValue(l.id)
        case i: CredentialItem.Password => i.setValue(l.password.toArray)
        case i: CredentialItem.StringType if i.getPromptText == "Password: " =>
          i.setValue(l.password)
        case i => throw new UnsupportedCredentialItem(uri, s"${i.getClass.getName}:${i.getPromptText}")
        }
      true
      },
      false
    )
  }
}
