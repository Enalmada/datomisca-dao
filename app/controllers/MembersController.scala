package controllers

import javax.inject.Inject

import models.User.Role.Member
import play.api.i18n.MessagesApi
import util.CacheUtil

class MembersController @Inject()(implicit val messagesApi: MessagesApi) extends BaseController {

  def auth(path: String) = StackAction(AuthorityKey -> Member) { implicit request =>

    if (path.isEmpty)
      Redirect("/")
    else
      Redirect(path)
  }

  def dashboard = StackAction(AuthorityKey -> Member) { implicit request =>
    implicit val localUser = Some(loggedIn)
    Ok(views.html.dashboard()).withHeaders(CacheUtil.noCache: _*)
  }

}