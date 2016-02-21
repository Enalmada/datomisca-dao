package util.mail

import play.api.Play.current
import play.api.i18n.Messages
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.duration._
import scala.language.postfixOps

object MailService {

  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: String, bodyText: String = "")(implicit mailerClient: MailerClient, messages: Messages, from: String) = {
    Akka.system.scheduler.scheduleOnce(100 milliseconds) {
      sendEmail(recipients: _*)(subject, bodyHtml, bodyText)
    }
  }

  def sendEmail(recipients: String*)(subject: String, bodyHtml: String, bodyText: String = "")(implicit mailerClient: MailerClient, messages: Messages, from: String) = {

    val email = Email(
      subject,
      from,
      Seq(recipients: _*),
      bodyText = Some(bodyText),
      bodyHtml = Some(bodyHtml)
    )
    mailerClient.send(email)
  }

}