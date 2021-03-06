package akkaguide

import akka.actor.{Props, Actor}
import java.util.UUID
import akka.routing.ScatterGatherFirstCompletedRouter
import scala.concurrent.duration._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

object BookmarkStore {
  case class AddBookmark(title: String, url: String)
  case class GetBookmark(uuid: UUID)
}

class BookmarkStore(database: Database[Bookmark, UUID], crawlerNodes: collection.immutable.Seq[String]) extends Actor {

  import BookmarkStore.{GetBookmark, AddBookmark}
  import Crawler.{RetrievePage, Page}

  val crawlerRouter =
    context.actorOf(Props.empty.withRouter(ScatterGatherFirstCompletedRouter(nrOfInstances = 10, routees = crawlerNodes,
      within = 30 seconds)), "crawlerRouter")

  def receive = {
    case AddBookmark(title, url) ⇒
      val bookmark = Bookmark(title, url)
      database.find(bookmark) match {
        case Some(found) ⇒ sender ! None
        case None ⇒
          val uuid = UUID.randomUUID
          database.create(uuid, bookmark)
          println(s"Use this URL to get results of bookmark http://localhost:8080?id=$uuid")
          sender ! Some(bookmark)
          import context.dispatcher
          implicit val timeout = Timeout(30 seconds)
          (crawlerRouter ? RetrievePage(bookmark)) pipeTo self
      }
    case GetBookmark(uuid) ⇒
      sender ! database.read(uuid)
    case Page(bookmark, pageContent) ⇒
      database.find(bookmark).map {
        found ⇒
          database.update(found._1, bookmark.copy(content = Some(pageContent)))
      }
  }
}
