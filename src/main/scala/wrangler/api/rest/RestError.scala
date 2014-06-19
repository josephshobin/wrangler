package wrangler.api.rest

import com.ning.http.client.Response

import org.json4s.JValue
import org.json4s.native.JsonMethods.{pretty, render}

sealed trait RestError {
  /** Nicely formatted error message.*/
  def msg: String
}

/** Request to URI could not be authorized.*/
case class Unauthorized(uri: String) extends RestError {
  val msg = s"Unauthorized access to $uri."
}

/** Invalid request.*/
case class RequestError(uri: String, code: Int, error: Response) extends RestError {
  val msg = s"Error making request to $uri. $code, ${error.getResponseBody}"
}

/** URI could not be found.*/
case class NotFound(uri: String, error: String) extends RestError {
  val msg = s"Couldn't find $uri. $error"
}

/** Error establishing connection. */
case class Error(uri: String, exception: Throwable) extends RestError {
  val msg =
    s"Failed to establish connection to $uri. Is your proxy correctly configured " ++
    s"(http_proxy|HTTP_PROXY) and do you have network connectivity. $exception"
}
