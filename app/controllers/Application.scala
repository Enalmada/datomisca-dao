package controllers

import javax.inject.Inject

import controllers.airbrake.Airbrake
import models.Configuration
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.ws.WSClient
import play.api.mvc._
import util.CacheUtil


class Application @Inject()(val messagesApi: MessagesApi, ws: WSClient, env: play.api.Environment, config: play.api.Configuration, webJarAssets: WebJarAssets) extends BaseControllerOpt {

  def home = Action {
    Ok("Ok")
  }

  /**
    * Health check handler for Amazon load balancer.  Hits the database since we once has servers up without db connections.
    *
    * @return OK
    */
  def health = Action {

    Ok("Healthy").withHeaders(CACHE_CONTROL -> "no-cache, no-store, must-revalidate",
      PRAGMA -> "no-cache",
      EXPIRES -> "0")
  }

  /**
    * Respond to head requests for health checking
    *
    * @return OK
    */
  def healthHead = Action {
    Ok.withHeaders(CACHE_CONTROL -> "no-cache, no-store, must-revalidate",
      PRAGMA -> "no-cache",
      EXPIRES -> "0")
  }

  def redirectUntrailed(path: String) = Action { implicit request =>
    Redirect("/" + path)
  }

  def letsEncrypt(id: String) = Action {
    val customText = Configuration.findValueByKey(s"LetsEncrypt").getOrElse("")

    Ok(s"${id}.$customText").withHeaders(CACHE_CONTROL -> "no-cache, no-store, must-revalidate",
      PRAGMA -> "no-cache",
      EXPIRES -> "0")
  }

  case class JavascriptError(error: String, file: String, location: String, lineNumber: String, documentReady: String, ua: String)

  val javascriptErrorForm = Form[JavascriptError](
    mapping(
      "error" -> text,
      "file" -> text,
      "location" -> text,
      "lineNumber" -> text,
      "documentReady" -> text,
      "ua" -> text
    )(JavascriptError.apply)(JavascriptError.unapply)
  )

  def javascriptError = Action { implicit request =>

    javascriptErrorForm.bindFromRequest.fold(
      formWithErrors => BadRequest.withHeaders(CacheUtil.noCache: _*),
      success = errorForm => {
        Logger.debug("error: " + errorForm.error)
        Logger.debug("file: " + errorForm.file)
        Logger.debug("location: " + errorForm.location)
        Logger.debug("lineNumber: " + errorForm.lineNumber)
        Logger.debug("documentReady: " + errorForm.documentReady)
        Logger.debug("ua: " + errorForm.ua)
        Airbrake.notify(errorForm.error, "\"" + errorForm.file + "\"", errorForm.location, "\"" + errorForm.lineNumber + "\"", errorForm.documentReady, errorForm.ua)
        Ok.withHeaders(CacheUtil.noCache: _*)

      })

  }


}
