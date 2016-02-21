package controllers

import datomiscadao.IdEntity
import jp.t2v.lab.play2.auth.{AuthElement, OptionalAuthElement}
import jp.t2v.lab.play2.stackc.RequestWithAttributes
import models.User.Role.Administrator
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc._


trait ControllerHelpers extends Controller {

  def referer()(implicit request: Request[_]) = request.headers.get("referer").getOrElse("")

  def reUrl()(implicit listPage: String, request: RequestWithAttributes[AnyContent]) = request.body.asFormUrlEncoded.map {
    _.getOrElse("reUrl", Seq(listPage)).head
  }.getOrElse(listPage)

  def reUrlMulti()(implicit listPage: String, request: Request[MultipartFormData[Files.TemporaryFile]]) = request.body.asFormUrlEncoded.getOrElse("reUrl", Seq(listPage)).head


  def GO_HOME()(implicit listPage: String) = Redirect(listPage)

  val defaultPageSize = 50

  def stringOpt(value: String) = Option(value).filter(_.trim.nonEmpty)

  def longOpt(value: String) = Option(value).filter(_.trim.nonEmpty).map(_.toLong)

  def intOpt(value: String) = Option(value).filter(_.trim.nonEmpty).map(_.toInt)


  //def forbidden(implicit flash: Flash) = Forbidden(views.html.forbidden())

  //def notFound(implicit flash: Flash) = NotFound(views.html.notFound())

}

trait BaseController extends Controller with ControllerHelpers with AuthElement with AuthConfigImpl with I18nSupport

trait BaseControllerOpt extends Controller with ControllerHelpers with OptionalAuthElement with AuthConfigImpl with I18nSupport


class StaleObjectStateException
  extends RuntimeException("Optimistic locking error - object in stale state")


/**
  * Controller module to add instant support for inline updating to your controller. Simply mix in the trait to use.
  */
trait InlineUpdate[T <: IdEntity] extends BaseController {


  /**
    * Form for single results as key value pairs
    */
  private val updateFieldForm = Form(
    tuple(
      "pk" -> longNumber,
      "name" -> text,
      "value" -> optional(text),
      "minLength" -> optional(text),
      "maxLength" -> optional(text)
    )
  )

  /**
    * Override this method to update the field with the given name, to the given value. This is typically done using the
    * case class {{{copy}}} method and a pattern match. In the future this may be able to be implemented using
    * reflection.
    *
    * @param id        the ID of the entity that is to be updated
    * @param fieldName the name of the field to update
    * @param value     the value of the field to set it to
    * @return a copy of the entity with it's field set.
    */
  protected def updateAndSave(id: Long, fieldName: String, value: Option[String])(implicit user: models.User): T


  /**
    * Verifies the minimum and maximum length of the value and returns an error message if one of those criteria is
    * violated.
    *
    * @param valueOpt     the value to check
    * @param minLengthOpt the minimum length
    * @param maxLengthOpt the maximum length
    * @return an [[scala.Option]] containing the error message if one exists
    */
  private def verifyLength(valueOpt: Option[String],
                           minLengthOpt: Option[String],
                           maxLengthOpt: Option[String]): Option[String] = {

    valueOpt match {

      case Some(value) =>
        val maxLength = maxLengthOpt.map(_.toInt) getOrElse 400
        val minLength = minLengthOpt.map(_.toInt) getOrElse 0

        if (value.length > maxLength) {

          Some(s"The value entered is longer than the max length of $maxLength")
        } else if (value.length < minLength) {

          Some(s"The value entered is shorter than the min length of $minLength")
        } else {

          None
        }
      case None => None
    }
  }


  /**
    * Post to this action to update a single field of a single object
    *
    * @return Ok or BadRequest on error
    */
  def updateField() = StackAction(AuthorityKey -> Administrator) { implicit request =>

    try {

      val bindedForm = updateFieldForm.bindFromRequest
      bindedForm.fold(
        formWithErrors => {
          BadRequest("Bad data")
        },
        formData => {

          val (id, fieldName, value, minLength, maxLength) = formData
          verifyLength(value, minLength, maxLength) map {

            BadRequest(_)
          } getOrElse {

            updateAndSave(id, fieldName, value)
            val json = Json.toJson(Map("success" -> true))
            Ok(json)
          }
        }
      )
    } catch {
      case nfe: NumberFormatException =>
        // Will throw exception on binding string to number in some cases
        val json = Json.toJson(Map("success" -> Json.toJson(false), "msg" -> Json.toJson("Invalid value")))
        Ok(json)
      case e: Throwable =>

        if (e.getCause.getMessage.contains("Duplicate entry")) {
          val json = Json.toJson(Map("success" -> Json.toJson(false), "msg" -> Json.toJson("This name already exists.")))
          Ok(json)

        } else {
          Logger.error("Error during form binding", e)
          val json = Json.toJson(Map("success" -> Json.toJson(false), "msg" -> Json.toJson(e.getMessage)))
          Ok(json)

        }


    }
  }

}


/**
  * Controller module to add instant support for inline updating to your controller. Simply mix in the trait to use.
  */
trait InlineMultiselectUpdate[T <: IdEntity] extends BaseController {

  /**
    * For for multiple results. This is used for things like checkbox groups and multi-select widgets
    */
  private val updateMultiselectForm = Form(
    tuple(
      "pk" -> longNumber,
      "name" -> text,
      "value" -> list(text)
    )
  )


  /**
    * Override this method to update the field with the given name, to the given value. This is typically done using the
    * case class {{{copy}}} method and a pattern match. In the future this may be able to be implemented using
    * reflection.
    *
    * @param id        the ID of the entity that is to be updated
    * @param fieldName the name of the field to update
    * @param values    the multi-select values
    * @return a copy of the entity with it's field set.
    */
  protected def updateAndSaveMultiple(id: Long, fieldName: String, values: List[String])(implicit user: models.User): Unit

  /**
    * Post to this action to update a single field of a single object
    *
    * @return Ok or BadRequest on error
    */
  def updateMultiselectField() = StackAction(AuthorityKey -> Administrator) { implicit request =>

    try {

      val bindedForm = updateMultiselectForm.bindFromRequest
      bindedForm.fold(
        formWithErrors => BadRequest("Bad data"),
        formData => {

          val (id, fieldName, values) = formData
          updateAndSaveMultiple(id, fieldName, values)
          val json = Json.toJson(Map("success" -> true))
          Ok(json)
        }
      )
    } catch {
      case nfe: NumberFormatException =>
        // Will throw exception on binding string to number in some cases
        val json = Json.toJson(Map("success" -> Json.toJson(false), "msg" -> Json.toJson("Invalid value")))
        Ok(json)
      case e: Throwable =>

        Logger.error("Error during form binding", e)
        val json = Json.toJson(Map("success" -> Json.toJson(false), "msg" -> Json.toJson(e.getMessage)))
        Ok(json)
    }
  }
}
