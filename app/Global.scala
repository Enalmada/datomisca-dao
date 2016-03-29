import play.api.{Application, GlobalSettings, Logger, Play}
import util.DatomicService

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application started!")

    if (Play.isTest(app)) {
      DatomicService.connString = DatomicService.test(app)
      DatomicService.testStart(app)
    } else {
      DatomicService.connString = DatomicService.uri(app)
      DatomicService.normalStart(app)
    }

  }

  override def onStop(app: Application) {

    if (Play.isTest(app)) {
      DatomicService.testEnd(app)
    }

    Logger.info("Application stopped.")

  }


}