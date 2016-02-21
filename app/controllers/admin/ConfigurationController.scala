package controllers.admin

import javax.inject.Inject

import controllers.{BaseController, InlineUpdate}
import datomisca.Datomic
import datomiscadao.PageFilter
import models.Configuration
import models.User.Role.Administrator
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import util.DatomicService._

class ConfigurationController @Inject()(implicit val messagesApi: MessagesApi) extends BaseController with InlineUpdate[Configuration] {

  val configurationForm = Form(
    mapping(
      "id" -> optional(longNumber),
      "configKey" -> nonEmptyText,
      "configValue" -> optional(text),
      "notes" -> optional(text)
    ) { (id, configKey, configValue, notes) => {
      id match {
        case Some(configId) => Configuration.get(configId, Datomic.database()).copy(configKey = configKey, configValue = configValue, notes = notes)
        case None => Configuration(configKey = configKey, configValue = configValue, notes = notes)
      }
    }
    }
      // Unbinding
    { configuration => Some((Some(configuration.id), configuration.configKey, configuration.configValue, configuration.notes)) }
      .verifying("A configuration with this name already exists.",
        c => !Configuration.exists(c.configKey, if (c.id < 0) None else Some(c.id)))
  )


  def list(page: Int) = StackAction(AuthorityKey -> Administrator) { implicit request =>
    val pageFilter = PageFilter(page, 20)
    val configurations = Configuration.list(pageFilter)
    Ok(views.html.admin.configuration.listConfiguration(configurations))
  }

  def create = StackAction(AuthorityKey -> Administrator) { implicit request =>

    Ok(views.html.admin.configuration.editConfiguration(configurationForm))
  }

  def edit(id: Long) = StackAction(AuthorityKey -> Administrator) { implicit request =>

    Configuration.find(id, Datomic.database) match {
      case None => NotFound
      case Some(c) =>
        Ok(views.html.admin.configuration.editConfiguration(configurationForm.fill(c)))
    }
  }

  def save = StackAction(AuthorityKey -> Administrator) { implicit request =>

    val user = loggedIn
    configurationForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.configuration.editConfiguration(formWithErrors)),
      configuration => {
        if (configuration.id > 0) {
          Configuration.update(configuration.id, configuration)
        } else {
          Configuration.create(configuration)
        }
        Redirect(routes.ConfigurationController.list()).flashing("success" -> "Configuration added.")
      }
    )
  }


  override protected def updateAndSave(id: Long, fieldName: String, value: Option[String])(implicit user: User): Configuration = {

    val oldConfiguration = Configuration.get(id, Datomic.database())
    val newConfiguration = fieldName match {
      case "configKey" => oldConfiguration.copy(configKey = value.get)
      case "configValue" => oldConfiguration.copy(configValue = value)
      case "notes" => oldConfiguration.copy(notes = value)
      case _ => oldConfiguration
    }
    Configuration.update(id, newConfiguration)

  }


  def delete(id: Long) = StackAction(AuthorityKey -> Administrator) { implicit request =>

    Configuration.find(id, Datomic.database()) map { configuration =>
      Configuration.delete(configuration.id)
      Redirect(routes.ConfigurationController.list()).flashing("success" -> "Configuration removed.")
    } getOrElse BadRequest("Invalid Configuration ID")

  }

}