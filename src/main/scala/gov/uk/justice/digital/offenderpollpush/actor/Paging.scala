package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.data.{BuildRequest, PushResult, TargetOffender}

class Paging @Inject() (@Named("pageSize") pageSize: Int) extends Actor with ActorLogging {

  private case class State(currentPage: Int, waitingPages: Seq[Seq[BuildRequest]])

  private def poller = context.actorSelection("/user/Poller")
  private def builder = context.actorSelection("/user/Builder")

  override def receive: Receive = process(State(0, Seq()))

  private def process(state: State): Receive = {

    case request @ BuildRequest(_, _) =>

      context become process(
        if (state.waitingPages.isEmpty) {    // Current page only and nothing queued

          if (state.currentPage < pageSize) {   // Under max concurrent page, so send immediately

            builder ! request
            State(state.currentPage + 1, Seq())

          } else {  // Page limit reached, now queue all new requests until existing batch is completed

            log.info("Creating the first waiting page of build requests")

            State(state.currentPage, Seq(Seq(request))) // Add to waitingPages - first one!
          }

        } else {  // Already have waiting pages (state.waitingPages.nonEmpty), so all requests are now paged until no paged requests

          State(state.currentPage,
            if (state.waitingPages.last.length < pageSize) { // Add to existing last non-full waiting page

              state.waitingPages.init :+ (state.waitingPages.last :+ request)

            } else { // All waiting pages full, start a new one

              log.info(s"Creating an new waiting page in addition to ${state.waitingPages.length} pages of $pageSize requests")

              state.waitingPages :+ Seq(request)
            }
          )
        }
      )

    case result @ PushResult(TargetOffender(id, _, cohort), _, _, _) =>

      poller ! result

      context become process(
        if (state.currentPage > 1) {  // Others of current page still in flight, so let them complete

          State(state.currentPage - 1, state.waitingPages)

        } else { // Was only one outstanding, so current page has completed. Now send all of next page if it exists

          if (state.waitingPages.nonEmpty) {

            log.info(s"Sending a page of ${state.waitingPages.head.length} build requests with ${state.waitingPages.length - 1} pages remaining")

            for (request <- state.waitingPages.head) builder ! request

            State(state.waitingPages.head.length, state.waitingPages.tail)

          } else {  // No current page left and none waiting

            State(0, Seq())
          }
        }
      )
  }
}

//@TODO: Add paging TESTS - make page size 5 in tests, block 1, send 7, check only 5 pulls made until pull replies
//@TODO: Also pull all with paging pulls into internal paging

