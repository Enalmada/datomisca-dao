package controllers.admin

import javax.inject.Inject

import controllers.{BaseController, WebJarAssets}
import datomiscadao.PageFilter
import datomiscadao.Sort.{Asc, Desc, SortBy}
import models.User
import models.User.Role
import models.User.Role._
import org.apache.commons.lang3.StringUtils
import org.mindrot.jbcrypt.BCrypt
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.ws.WSClient
import util.CacheUtil
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserController @Inject()(implicit val messagesApi: MessagesApi, ws: WSClient, config: play.api.Configuration, env: play.api.Environment, webJarAssets: WebJarAssets) extends BaseController {

  // TODO: for some reason I have to hardcode "/admin".  Play bug with route splitting?
  implicit val listPage = "/admin" + controllers.admin.routes.UserController.list().url


  /*
    // This is so we can show the category error on the category field.  Need access to id and parentId to compare
  val duplicateEmailFormatter = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      // "data" lets you access all form data values
      val id = data.getOrElse("id", "")
      val email = data.getOrElse("email", "")
      val otherEntity = User.findByEmail(email)
      if (data.get("email").isEmpty)
        Left(List(FormError("name", "Email must not be blank.")))
      else if (otherEntity.isDefined && id.toLong != otherEntity.get._1) {
        Left(List(FormError("name", "Email is already taken by " + routes.UserController.edit(otherEntity.get._1))))
      } else {
        Right(email)
      }
    }


    override def unbind(key: String, value: String): Map[String, String] = {
      Map(key -> value)
    }
  }
  */

  def userForm = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText.verifying("Password must be at least 8 characters", p => p.length() >= 8),
      "active" -> boolean,
      "role" -> nonEmptyText,
      "notes" -> optional(text)
    ) { (email, password, active, role, notes) => {

      val user = User.findByEmail(email).getOrElse(User())
      val finalPassword = if (!StringUtils.isEmpty(password)) {
        BCrypt.hashpw(password, BCrypt.gensalt())
      } else user.password


      val newUser = user.copy(
        email = email,
        password = finalPassword,
        active = active,
        role = Role.fromString(role).get,
        notes = notes
      )

      newUser

    }
    } { user: User =>
      Some((user.email, user.password, user.active, user.role.toString, user.notes))
    }
  )


  /**
    * Display the paginated list of Users.
    *
    * @param page      Current page number (starts from 0)
    * @param sortBy    Column to be sorted
    * @param sortOrder Sort order (either asc or desc)
    * @param query     Filter applied on User names
    */
  def list(page: Int, sortBy: String, sortOrder: String, query: String) = StackAction(AuthorityKey -> Administrator) { implicit request =>
    val form: Form[String] = Form("query" -> text).fill(query)
    val queryOpt = stringOpt(query)
    val dbSortOrder = if (sortOrder == "desc") Desc else Asc
    val pageFilter = PageFilter(page, 20)
    val list = User.list(queryOpt, SortBy(sortBy, dbSortOrder), pageFilter)
    Ok(views.html.admin.users.listUser(list, form, sortBy, sortOrder)).withHeaders(CacheUtil.noCache: _*)
  }

  /**
    * Display the 'create form' of a new user.
    *
    * @param reUrl the url to return to
    */
  def newForm(reUrl: Option[String] = None) = StackAction(AuthorityKey -> Administrator) { implicit request =>
    val form = userForm.fill(User())
    Ok(views.html.admin.users.createUserForm(form, reUrl.getOrElse(referer))).withHeaders(CacheUtil.noCache: _*)
  }

  /**
    * Display the 'edit form' of a existing User.
    *
    * @param id Id of the User to edit
    */
  def edit(id: Long, reUrl: Option[String] = None) = StackAction(AuthorityKey -> Administrator) { implicit request =>
    User.findById(id) match {
      case None => NotFound
      case Some(user) =>
        val form = userForm.fill(user)
        Ok(views.html.admin.users.editUserForm(id, form, reUrl.getOrElse(referer))).withHeaders(CacheUtil.noCache: _*)
    }
  }

  /**
    * Handle the creation of a new user
    */
  def create() = AsyncStack(AuthorityKey -> Administrator) { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.admin.users.createUserForm(formWithErrors, reUrl)).withHeaders(CacheUtil.noCache: _*)),
      success = userForm => {
        User.create(userForm).map { user =>
          val flash = "User <a href=\"" + controllers.admin.routes.UserController.edit(user.id).toString + "\" class=\"js-pjax\" data-pjax=\"#contentArea\">" + userForm.email + "</a> has been created"
          Redirect(reUrl).flashing("success" -> flash)
        }
      })
  }

  /**
    * Handle the 'edit form' submission
    *
    * @param id Id of the User to edit
    */
  def update(id: Long) = AsyncStack(AuthorityKey -> Administrator) { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.admin.users.editUserForm(id, formWithErrors, reUrl)).withHeaders(CacheUtil.noCache: _*)),
      success = userForm => {
        User.update(id, userForm).map { user =>
          val flash = "User <a href=\"" + controllers.admin.routes.UserController.edit(id).toString + "\" class=\"js-pjax\" data-pjax=\"#contentArea\">" + userForm.email + "</a> has been updated"
          Redirect(reUrl).flashing("success" -> flash)
        }
      })
  }

  /**
    * Handle user deletion
    */
  def delete(id: Long) = StackAction(AuthorityKey -> Administrator) { implicit request =>
    User.delete(id)
    Redirect(reUrl).flashing("success" -> "User has been deleted")
  }


}