package aia

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import scala.collection.Seq

case class IMSICGateway(val id : Int, priorityWidth : Int) extends Area{
  /** map to eip[id] */
  val ip : Bool = Bool()
  /** map to iprio[id] */
  val priority = UInt(priorityWidth bits)
}

case class IMSICTarget(gateways : Seq[IMSICGateway], priorityWidth : Int) extends Area{
  val idWidth = log2Up((gateways.map(_.id) ++ Seq(0)).max + 1)

  assert(gateways.map(_.id).distinct.length == gateways.length, "IMSIC gateways have duplicated ID")
  assert(priorityWidth >= idWidth, "priorityWidth is not enough to hold all IMSIC gateway id")
  assert(priorityWidth <= 8, "IMSIC only support max 8 bit for priority")

  /**
   * map to eie[id]. Unlike real eie register, this vector only contain vaild
   * bits and need padding if interrupt id is not continuous.
   */
  val ies = Vec.fill(gateways.length)(Bool())
  /** map to eithreshold */
  val threshold = UInt(priorityWidth bits)
  /* map to eidelivery */

  def Request(priority : UInt, id : UInt, valid : Bool) = {
    val ret = new Request
    ret.priority := priority
    ret.id := id
    ret.valid := valid
    ret
  }

  case class Request() extends Bundle{
    val priority = UInt(priorityWidth bits)
    val id = UInt(idWidth bits)
    val valid = Bool()
  }

  val requests = Request(U(0),U(0), True) +: gateways.zipWithIndex.sortBy(_._1.id).map(g =>
    Request(
      priority = g._1.priority,
      id       = U(g._1.id),
      valid    = g._1.ip && ies(g._2)
    )
  )

  val bestRequest = RegNext(requests.reduceBalancedTree((a, b) => {
    val takeA = !b.valid || (a.valid && a.priority >= b.priority)
    takeA ? a | b
  }))

  val iep = bestRequest.priority > threshold
  val claim = iep ? bestRequest.id | 0
  val claimprio = iep ? bestRequest.priority | 0
}
