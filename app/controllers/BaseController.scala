package controllers

import _root_.play.api.i18n.I18nSupport
import _root_.play.api.libs.Files
import _root_.play.api.mvc._


trait MyBaseController extends I18nSupport {

  def referer()(implicit request: Request[_]) = request.headers.get("referer").getOrElse("")


  def reUrlMulti()(implicit listPage: String, request: Request[MultipartFormData[Files.TemporaryFile]]) = request.body.asFormUrlEncoded.getOrElse("reUrl", Seq(listPage)).head


  val defaultPageSize = 50

  def stringOpt(value: String) = Option(value).filter(_.trim.nonEmpty)

  def longOpt(value: String) = Option(value).filter(_.trim.nonEmpty).map(_.toLong)

  def intOpt(value: String) = Option(value).filter(_.trim.nonEmpty).map(_.toInt)


  //def forbidden(implicit flash: Flash) = Forbidden(views.html.forbidden())

  //def notFound(implicit flash: Flash) = NotFound(views.html.notFound())
}

trait MyBaseControllerOpt extends I18nSupport {


  //def forbidden(implicit flash: Flash) = Forbidden(views.html.forbidden())

  //def notFound(implicit flash: Flash) = NotFound(views.html.notFound())
}


class StaleObjectStateException
  extends RuntimeException("Optimistic locking error - object in stale state")

