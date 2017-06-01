package controllers

import javax.inject.Inject

import _root_.play.api.data.Form
import _root_.play.api.data.Forms._
import _root_.play.api.data.validation.Constraints._
import _root_.play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import _root_.play.api.i18n.{Messages, MessagesApi}
import _root_.play.api.libs.mailer.MailerClient
import _root_.play.api.mvc._
import jp.t2v.lab.play2.auth.LoginLogout
import models.User
import org.mindrot.jbcrypt.BCrypt
import util.mail.Mailer
import util.{CacheUtil, Util}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SignupController @Inject()(implicit val messagesApi: MessagesApi, mailerClient: MailerClient) extends BaseControllerOpt with LoginLogout {

  def userExists: Constraint[String] = Constraint("user.exits") { email =>
    if (userExistsWithEmail(email)) Valid else Invalid(Seq(ValidationError("error.not.exists", "user", "email")))
  }

  def userUnique: Constraint[String] = Constraint("user.unique") { email =>
    if (!userExistsWithEmail(email)) Valid else Invalid(Seq(ValidationError("error.not.unique", "user", "email")))
  }

  private def userExistsWithEmail(email: String): Boolean = User.findByEmail(email).isDefined

  case class UserForm(id: Option[Long] = None, email: String, emailConfirmed: Boolean, password: String)

  val signUpForm = Form(
    mapping(
      "id" -> ignored(None: Option[Long]),
      "email" -> email.verifying(maxLength(250), userUnique),
      "emailConfirmed" -> ignored(false),
      "password" -> nonEmptyText.verifying(minLength(6))
    )(UserForm.apply)(UserForm.unapply)
  )

  /**
    * Starts the sign up mechanism. It shows a form that the user have to fill in and submit.
    */
  def startSignUp = StackAction { implicit request =>
    loggedIn match {
      case Some(user) => Redirect(routes.MembersController.dashboard())
      case None => Ok(views.html.auth.signUp(signUpForm)).withHeaders(CacheUtil.noCache: _*)
    }
  }


  /**
    * Handles the form filled by the user. The user and its password are saved and it sends him an email with a link to confirm his email address.
    */
  def handleStartSignUp = Action.async { implicit request =>
    signUpForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.signUp(formWithErrors))),
      userForm => {
        val userTemp = User().copy(email = userForm.email, password = BCrypt.hashpw(userForm.password, BCrypt.gensalt()))
        User.create(userTemp).flatMap { user =>
          val token = userForm.email + ":" + Util.tokenHash(userForm.email)
          Mailer.welcome(userForm.email, link = routes.SignupController.signUp(token).absoluteURL())
          gotoLoginSucceeded(user.id)
        }
      }
    )
  }

  /**
    * Confirms the user's email address based on the token and authenticates him.
    */
  def signUp(tokenId: String) = Action.async { implicit request =>

    val (email, token) = (tokenId.split(":").head, tokenId.split(":").last)
    val userOpt: Option[User] = User.findByEmail(email)

    userOpt match {
      case Some(user) =>
        val freshToken = Util.tokenHash(user.email)
        if (token == freshToken) {
          if (!user.validated) {
            User.update(user.id, user.copy(validated = true))
          }
          gotoLoginSucceeded(user.id)
        } else
          Future.successful(BadRequest)

      case None => Future.failed(new AuthenticationException("Couldn't find user"))
    }


  }


  // FORGOT PASSWORD

  val emailForm = Form(single("email" -> email.verifying(userExists)))

  /**
    * Starts the reset password mechanism if the user has forgot his password. It shows a form to insert his email address.
    */
  def forgotPassword = StackAction { implicit request =>
    loggedIn match {
      case Some(user) => Redirect(routes.MembersController.dashboard())
      case None => Ok(views.html.auth.forgotPassword(emailForm)).withHeaders(CacheUtil.noCache: _*)
    }
  }

  /**
    * Sends an email to the user with a link to reset the password
    */
  def handleForgotPassword = Action.async { implicit request =>
    implicit val localUser = None
    emailForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.forgotPassword(formWithErrors))),
      email => {
        val token = email + ":" + Util.tokenHash(email)
        Mailer.forgotPassword(email, link = routes.SignupController.resetPassword(token).absoluteURL())
        Future.successful(Ok(views.html.auth.forgotPasswordSent(email)).withHeaders(CacheUtil.noCache: _*))
      }
    )
  }

  val passwordsForm = Form(tuple(
    "password1" -> nonEmptyText(minLength = 6),
    "password2" -> nonEmptyText
  ) verifying(Messages("passwords.not.equal"), passwords => passwords._2 == passwords._1))


  /**
    * Confirms the user's link based on the token and shows him a form to reset the password
    */
  def resetPassword(tokenId: String) = Action.async { implicit request =>
    val (email, token) = (tokenId.split(":").head, tokenId.split(":").last)
    val freshToken = Util.tokenHash(email)
    if (token == freshToken)
      Future.successful(Ok(views.html.auth.resetPassword(tokenId, passwordsForm)).withHeaders(CacheUtil.noCache: _*))
    else
      Future.successful(BadRequest.withHeaders(CacheUtil.noCache: _*))

  }

  /**
    * Saves the new password and authenticates the user
    */
  def handleResetPassword(tokenId: String) = Action.async { implicit request =>

    val (email, token) = (tokenId.split(":").head, tokenId.split(":").last)
    val freshToken = Util.tokenHash(email)
    if (token == freshToken)
      passwordsForm.bindFromRequest.fold(
        formWithErrors => Future.successful(BadRequest(views.html.auth.resetPassword(tokenId, formWithErrors))),
        passwords =>
          User.findByEmail(email) match {
            case Some(user) =>
              User.update(user.id, user.copy(password = BCrypt.hashpw(passwords._1, BCrypt.gensalt())))
              gotoLoginSucceeded(user.id)
            case None => Future.successful(BadRequest.withHeaders(CacheUtil.noCache: _*))
          }
      )
    else
      Future.successful(BadRequest.withHeaders(CacheUtil.noCache: _*))
  }

  def verifyEmail = StackAction { implicit request =>
    loggedIn match {
      case Some(user) =>
        val token = user.email + ":" + Util.tokenHash(user.email)
        Mailer.welcome(user.email, link = routes.SignupController.signUp(token).absoluteURL())
        Ok(views.html.auth.verifyEmailSent(user.email)).withHeaders(CacheUtil.noCache: _*)
      case None => Redirect(routes.MembersController.dashboard())
    }


  }
}

case class AuthenticationException(message: String) extends Exception(message)