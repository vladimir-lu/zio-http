package zhttp.service.client.domain

import io.netty.channel.Channel
import zhttp.service.client.domain.ConnectionData.ReqKey
import zio.Ref
import zio.duration.Duration

import java.net.InetSocketAddress
import scala.collection.immutable

/**
 * Defines ClientData / Request Key and other types for managing connection data
 * TODO: Likely to change
 *
 * @param channel
 *   the low level connection abstraction from netty
 * @param isReuse
 *   is channel newly created or being re-used.
 */
case class Connection(channel: Channel, isReuse: Boolean) {
  override def canEqual(that: Any): Boolean = that match {
    case that: Connection => this.channel.id() == that.channel.id()
    case _                => false
  }
}

/**
 * timeouts defined will be manipulated in tandem atomically, using zio.Ref
 */
case class Timeouts(
  connectionTimeout: Duration = Duration.Infinity,
  idleTimeout: Duration = Duration.Infinity,
  requestTimeout: Duration = Duration.Infinity,
)

/**
 * The overall state of connection pool
 *
 * @param currentAllocatedChannels
 *   Channels currently allocated (in-use)
 * @param idleConnectionsMap
 *   Channels which are idle and can be allocated to next request.
 */
case class ConnectionPoolState(
  currentAllocatedChannels: Map[Channel, ReqKey],
  idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]],
)

/**
 * Required for state transition in functional manner (oldState) =>
 * (newState,oldState)
 *
 * The newConnection will act as a place holder for next available idle channel.
 * This is required to manipulate connection pool state in a a deadlock free /
 * thread safe way.
 *
 * @param newConnection
 * @param connectionPoolState
 */
case class NewConnectionData(newConnection: Option[Connection] = None, connectionPoolState: ConnectionPoolState)

case class ConnectionData(connectionData: Ref[NewConnectionData]) {

  def nextIdleChannel(reqKey: ReqKey) = ???

  def getIdleChannel(
    reqKey: ReqKey,
    currentAllocatedChannels: Map[Channel, ReqKey],
    idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]],
  ) = ???

  def setConnectionIdle(connection: Connection, reqKey: ReqKey): zio.Task[Unit] = ???

  def addIdleChannel(
    connection: Connection,
    reqKey: ReqKey,
    currentAllocatedChannels: Map[Channel, ReqKey],
    idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]],
  ) = ???

  def getTotalConnections = for {
    connectionData <- connectionData.get
    allocConnections = connectionData.connectionPoolState.currentAllocatedChannels.size
    idleConnections  = connectionData.connectionPoolState.idleConnectionsMap.valuesIterator
      .foldLeft(0) { case (acc, queue) => acc + queue.size }
  } yield (allocConnections + idleConnections)

  // TBD thready safety and appropriate namespace
  var currMaxTotalConnections: Int     = 0
  var currMaxConnectionPerRequest: Int = 0
  var currMaxWaitingReq: Int           = 0

}

object ConnectionData {
  type ReqKey = InetSocketAddress
  def emptyIdleConnectionMap = Map.empty[ReqKey, immutable.Queue[Connection]]
}
