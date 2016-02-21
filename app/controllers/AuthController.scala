package controllers

import javax.inject.Inject

import jp.t2v.lab.play2.auth.{AsyncIdContainer, AuthConfig, LoginLogout, _}
import models.User
import models.User.Role.{Administrator, Member, Role, Tech}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc.{Action, Controller, _}
import util.CacheUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}

/**
  * Flows for login/logout and security definitions.
  */
class AuthController @Inject()(val messagesApi: MessagesApi) extends Controller with LoginLogout with AuthConfigImpl with I18nSupport {


  /** Your application's login form.  Alter it to fit your application */
  val loginForm = Form {
    mapping("email" -> email, "password" -> nonEmptyText)(User.authenticate)(_.map(u => (u.email, "")))
      .verifying("Invalid email or password", result => result.isDefined)
  }

  def login = Action { implicit request =>
    Ok(views.html.auth.login(loginForm)).withHeaders(CacheUtil.noCache: _*)
  }

  /**
    * Return the `gotoLogoutSucceeded` method's result in the logout action.
    *
    * Since the `gotoLogoutSucceeded` returns `PlainResult`,
    * you can add a procedure like the following.
    *
    * gotoLogoutSucceeded.flashing(
    * "success" -> "You've been logged out"
    * )
    */
  def logout = Action.async { implicit request =>
    import scala.concurrent.ExecutionContext.Implicits.global
    gotoLogoutSucceeded
  }

  /**
    * Return the `gotoLoginSucceeded` method's result in the login action.
    *
    * Since the `gotoLoginSucceeded` returns `PlainResult`,
    * you can add a procedure like the `gotoLogoutSucceeded`.
    */
  def authenticate = Action.async { implicit request =>

    import scala.concurrent.ExecutionContext.Implicits.global
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.login(formWithErrors)).withHeaders(CacheUtil.noCache: _*)),
      user => gotoLoginSucceeded(user.get.id)
    )
  }

}

trait AuthConfigImpl extends AuthConfig {

  /**
    * A type that is used to identify a user.
    * `String`, `Int`, `Long` and so on.
    */
  type Id = Long

  /**
    * A type that represents a user in your application.
    * `User`, `User` and so on.
    */
  type User = models.User

  /**
    * A type that is defined by every action for authorization.
    * This sample uses the following trait:
    *
    * sealed trait Role
    */
  type Authority = Role

  /**
    * A `ClassManifest` is used to retrieve an id from the Cache API.
    * Use something like this:
    */
  val idTag: ClassTag[Id] = classTag[Id]

  /**
    * The session timeout in seconds
    */
  val sessionTimeoutInSeconds: Int = 3600

  /**
    * A function that returns a `User` object from an `Id`.
    * You can alter the procedure to suit your application.
    */
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = Future {
    User.findById(id)
  }


  /**
    * Where to redirect the user after a successful login.
    */
  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    val uri = request.session.get("access_uri").getOrElse(admin.routes.UserController.list().url)
    Future.successful(Redirect(uri).withSession(request.session - "access_uri"))
  }


  /**
    * Where to redirect the user after logging out
    */
  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.Application.home()))
  }


  /**
    * If the user is not logged in and tries to access a protected resource then redirect them as follows:
    */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    // Check for request type. If AJAX request return an Unauthorized.
    // Otherwise redirect to login page.
    Future.successful {
      request.headers.get("X-Requested-With") match {
        case Some("XMLHttpRequest") => Unauthorized("Unauthorized. User session may have expired.").withHeaders(CacheUtil.noCache: _*)
        case _ => Redirect(routes.AuthController.login()).withSession("access_uri" -> request.uri)
      }
    }
  }


  /**
    * If authorization failed (usually incorrect password) redirect the user as follows:
    */
  def authorizationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Forbidden("no permission").withHeaders(CacheUtil.noCache: _*))
  }

  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Forbidden("no permission").withHeaders(CacheUtil.noCache: _*))
  }

  /**
    * A function that determines what `Authority` a user has.
    * You should alter this procedure to suit your application.
    */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    (user.role, authority) match {
      case (Tech, _) => true
      case (Administrator, Administrator) => true
      case (Administrator, Member) => true
      case (Member, Member) => true
      case _ => false
    }
  }


  //  /**
  //   * Whether use the secure option or not use it in the cookie.
  //   * However default is false, I strongly recommend using true in a production.
  //   */
  //  override lazy val cookieSecureOption: Boolean = play.api.Play.current.configuration.getBoolean("auth.cookie.secure").getOrElse(true)

  /**
    * Overriding for "Stateless" implementation. See https://github.com/t2v/play20-auth for more info.
    */
  override lazy val idContainer: AsyncIdContainer[Id] = AsyncIdContainer(new CookieIdContainer[Id])

  override lazy val tokenAccessor: TokenAccessor = new CookieTokenAccessor(
    cookieName = "YOURSITE_SESS_ID",
    cookieSecureOption = false, // play.api.Play.isProd(play.api.Play.current), // need to be on SSL in production
    cookieHttpOnlyOption = false,
    cookieDomainOption = None,
    cookiePathOption = "/",
    cookieMaxAge = None
  )

}
