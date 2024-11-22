package controllers

import org.apache.pekko.actor.ActorSystem
import datomisca.Connection
import javax.inject.Inject
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Configuration, Logging}
import util.{CacheUtil, DatomicService}

import scala.concurrent.ExecutionContext


class Application @Inject()(implicit components: ControllerComponents, ec: ExecutionContext, messagesApi: MessagesApi,
                            ws: WSClient, actorSystem: ActorSystem, environment: play.api.Environment,
                            config: Configuration, myDatomisca: DatomicService) extends AbstractController(components) with MyBaseControllerOpt with I18nSupport with Logging {

  implicit val conn: Connection = myDatomisca.conn
  protected[this] val e: Connection = conn

  def home = Action { implicit request =>

    Ok(views.html.dashboard()).withHeaders(CACHE_CONTROL -> "no-cache, no-store, must-revalidate",
      PRAGMA -> "no-cache",
      EXPIRES -> "0")
  }

  def logging = Action(parse.anyContent) { implicit request =>
    request.body.asJson.foreach { msg =>
      println(s"CLIENT - $msg")
    }
    Ok("")
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

    javascriptErrorForm.bindFromRequest().fold(
      formWithErrors => BadRequest.withHeaders(CacheUtil.noCache: _*),
      success = errorForm => {
        logger.debug("error: " + errorForm.error)
        logger.debug("file: " + errorForm.file)
        logger.debug("location: " + errorForm.location)
        logger.debug("lineNumber: " + errorForm.lineNumber)
        logger.debug("documentReady: " + errorForm.documentReady)
        logger.debug("ua: " + errorForm.ua)
        Ok.withHeaders(CacheUtil.noCache: _*)


      })

  }


}
