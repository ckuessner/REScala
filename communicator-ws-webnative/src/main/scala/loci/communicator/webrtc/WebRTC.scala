package loci
package communicator
package webrtc

import org.scalajs.dom

import scala.scalajs.js.Array

object WebRTC extends WebRTCUpdate {

  trait Connector {
    def use(update: IncrementalUpdate): Unit
    def set(update: CompleteUpdate): Unit
    def connection: dom.RTCPeerConnection
  }

  trait ConnectorFactory {
    def incremental(update: IncrementalUpdate => Unit): Connector
    def complete(update: CompleteSession => Unit): Connector
  }

  def apply(channel: dom.RTCDataChannel): WebRTCChannelConnector =
    new WebRTCChannelConnector(channel)

  def offer(
      configuration: dom.RTCConfiguration =
        new dom.RTCConfiguration { iceServers = Array[dom.RTCIceServer]() },
      options: dom.RTCOfferOptions =
        new dom.RTCOfferOptions {}
  ): ConnectorFactory =
    new ConnectorFactory {
      def incremental(update: IncrementalUpdate => Unit) =
        new WebRTCOffer(configuration, options, Left(update))
      def complete(update: CompleteSession => Unit) =
        new WebRTCOffer(configuration, options, Right(update))
    }

  def answer(
      configuration: dom.RTCConfiguration =
        new dom.RTCConfiguration { iceServers = Array[dom.RTCIceServer]() }
  ): ConnectorFactory =
    new ConnectorFactory {
      def incremental(update: IncrementalUpdate => Unit) =
        new WebRTCAnswer(configuration, Left(update))
      def complete(update: CompleteSession => Unit) =
        new WebRTCAnswer(configuration, Right(update))
    }
}
