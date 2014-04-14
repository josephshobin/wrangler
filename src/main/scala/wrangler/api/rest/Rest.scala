package wrangler.api
package rest

import scalaz._, Scalaz._

import com.ning.http.client.{Response, ProxyServer}

import dispatch._, Defaults._
import dispatch.as.json4s._

import org.json4s.JValue
import org.json4s.native.JsonMethods.{compact, render}

object Rest {
  val ProxyUrl = "http://([^:]*)(?::([0-9]+))?".r

  def post(uri: String, body: JValue, user: String, password: String): Rest[JValue] = {
    Http(http(uri, user, password).addHeader("Content-Type", "application/json") << compact(render(body)))
      .either
      .apply
      .disjunction
      .leftMap(Error.apply(uri, _))
      .flatMap(responseHandler(uri))
  }

  def get(uri: String, user: String, password: String): Rest[JValue] =
    Http((http(uri, user, password)))(executor)
      .either
      .apply
      .disjunction
      .leftMap(Error.apply(uri, _))
      .flatMap(responseHandler(uri))

  def postText(uri: String, body: String, user: String, password: String): Rest[JValue] = {
    Http(http(uri, user, password).addHeader("Content-Type", "text/plain").addHeader("Accept", "application/json") << body)
      .either
      .apply
      .disjunction
      .leftMap(Error.apply(uri, _))
      .flatMap(responseHandler(uri))
  }

  def putText(uri: String, body: String, user: String, password: String): Rest[JValue] = {
    Http(http(uri, user, password).addHeader("Content-Type", "text/plain").addHeader("Accept", "application/json").PUT << body)
      .either
      .apply
      .disjunction
      .leftMap(Error.apply(uri, _))
      .flatMap(responseHandler(uri))
  }

  def http(uri: String, user: String, password: String) = {
    val init = url(uri).as_!(user, password)

    Option(System.getenv("http_proxy")).orElse(Option(System.getenv("HTTP_PROXY")))
      .map { proxy =>
        val ProxyUrl(host, optPort) = proxy
        val port = Option(optPort).cata(_.toInt, 80)
        init.setProxyServer(new ProxyServer(host, port))

    }.getOrElse(init)
  }

  def responseHandler(uri: String)(response: Response): Rest[JValue] = response.getStatusCode match {
    case c if c >= 200 && c < 300      => Json(response).right
    case c if Set(401, 403) contains c => Unauthorized(uri).left
    case 404                           => NotFound(uri, response.getResponseBody).left
    case c                             => RequestError(uri, c, response).left
  }
}
