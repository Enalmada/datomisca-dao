package controllers.airbrake

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import play.api.mvc.RequestHeader
import play.api.{PlayException, UnexpectedException, _}

import scala.collection.JavaConversions._
import scala.concurrent.Future

object Airbrake {
  private lazy val app = play.api.Play.current
  private lazy val enabled = app.configuration.getBoolean("airbrake.enabled") getOrElse {
    Play.isProd
  }
  private lazy val apiKey = app.configuration.getString("airbrake.apiKey") getOrElse {
    throw UnexpectedException(Some("Could not find airbrake.apiKey in settings"))
  }
  private lazy val ssl = app.configuration.getBoolean("airbrake.ssl").getOrElse(false)
  private lazy val endpoint = app.configuration.getString("airbrake.endpoint") getOrElse "api.airbrake.io/notifier_api/v2/notices"

  /**
    * Scala API
    *
    * {{{
    * // app/Global.scala
    * override def onError(request: RequestHeader, ex: Throwable) = {
    *   Airbrake.notify(request, ex)
    *   super.onError(request, ex)
    * }
    * }}}
    */
  def notify(request: RequestHeader, th: Throwable): Unit = if (enabled) _notify(request.method, request.uri, request.session.data, th)

  def notify(error: String, file: String, location: String, lineNumber: String, documentReady: String, ua: String): Unit = if (enabled) _notify(error, file, location, lineNumber, documentReady, ua)

  /**
    * Java API
    *
    * {{{
    * // app/Global.java
    * @Override
    * public Result onError(RequestHeader request, Throwable t) {
    * Airbrake.notify(request, t);
    * return super.onError(request, t);
    * }
    * }}}
    */
  def notify(request: play.mvc.Http.RequestHeader, th: Throwable): Unit = if (enabled) {
    val data = request.headers.toMap.mapValues(_.toList.toString())
    _notify(request.method, request.uri, data, th)
  }

  protected def _notify(method: String, uri: String, data: Map[String, String], th: Throwable): Unit =
    Future {
      val scheme = if (ssl) "https" else "http"
      WS.url(scheme + "://" + endpoint).post(formatNotice(app, apiKey, method, uri, data, liftThrowable(th))).onComplete { response =>
        Logger.info("Exception notice sent to Airbrake")
      }
    }

  protected def _notify(error: String, file: String, location: String, lineNumber: String, documentReady: String, ua: String): Unit =
    Future {
      val scheme = if (ssl) "https" else "http"
      Logger.debug(formatJavascript(app, apiKey, error, file, location, lineNumber, documentReady, ua).toString())
      WS.url(scheme + "://" + endpoint).post(formatJavascript(app, apiKey, error, file, location, lineNumber, documentReady, ua)).onComplete { response =>
        Logger.info("Exception notice sent to Airbrake")
      }
    }

  def js = if (enabled) {
    /*
    """
    <script src="/assets/javascripts/airbrake-notifier.min.js"></script>
                          """.format(apiKey, app.mode)
                          */ "" // fix no apiKey or app.mode validation format warnings
  } else ""

  protected def liftThrowable(th: Throwable) = th match {
    case e: PlayException => e
    case e => UnexpectedException(unexpected = Some(e))
  }


  protected def formatJavascript(app: Application, apiKey: String, error: String, file: String, location: String, lineNumber: String, documentReady: String, ua: String) = {
    <notice version="2.3">
      <api-key>
        {apiKey}
      </api-key>
      <notifier>
        <name>Airbrake Notifier</name>
        <version>3.1.6</version>
        <url>http://api.airbrake.io</url>
      </notifier>
      <error>
        <class>Javascript Error</class>
        <message>
          {error}
        </message>
        <backtrace>
          <line method="public" file={file} number={lineNumber}/>
        </backtrace>
      </error>
      <request>
        <url>
          {location}
        </url>
        <component/>
        <action/>
        <cgi-data>
          <var key="SERVER_NAME">mentormyself.com</var>
          <var key="HTTP_USER_AGENT">
            {ua}
          </var>
        </cgi-data>
      </request>
      <server-environment>
        <project-root>/</project-root>
        <environment-name>
          {app.mode}
        </environment-name>
        <app-version>1.0.0</app-version>
      </server-environment>
    </notice>
  }


  protected def formatNotice(app: Application, apiKey: String, method: String, uri: String, data: Map[String, String], ex: UsefulException) = {
    <notice version="2.2">
      <api-key>
        {apiKey}
      </api-key>
      <notifier>
        <name>play-airbrake</name>
        <version>0.1.0</version>
        <url>http://github.com/teamon/play-airbrake</url>
      </notifier>
      <error>
        <class>
          {ex.title}
        </class>
        <message>
          {ex.description}
        </message>
        <backtrace>
          {ex.cause.getStackTrace.flatMap(e => formatStacktrace(e))}
        </backtrace>
      </error>
      <request>
        <url>
          {method + " " + uri}
        </url>
        <component/>
        <action/>{formatSession(data)}
      </request>
      <server-environment>
        <environment-name>
          {app.mode}
        </environment-name>
      </server-environment>
    </notice>
  }

  protected def formatSession(vars: Map[String, String]) =
    if (vars.nonEmpty) <session>
      {formatVars(vars)}
    </session>
    else Nil

  protected def formatVars(vars: Map[String, String]) = vars.map { case (key, value) =>
    <var key={key}>
      {value}
    </var>
  }

  protected def formatStacktrace(e: StackTraceElement) = {
    val line = "%s.%s(%s)" format(e.getClassName, e.getMethodName, e.getFileName)
      <line file={line} number={e.getLineNumber.toString}/>
  }

}