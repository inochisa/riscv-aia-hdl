package aia

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.{BusSlaveFactory, AllMapping, SingleMapping}
import spinal.lib.bus.amba4.axilite._

case class AxiLite4IMSIC(sourceCount : Int) extends Component{
  val priorityWidth = 8
  val imsicMapping = IMSICMapping.full
  import imsicMapping._

  val io = new Bundle {
    val bus = slave(AxiLite4(AxiLite4Config(22, 32)))
    val sources = in Bits (sourceCount bits)
    val target = out Bool()
    val threshold = in UInt(priorityWidth bits)
    val ies = Vec.fill(sourceCount)(in Bool())
    val ips = Vec.fill(sourceCount)(in Bool())
    val iprios = Vec.fill(sourceCount)(in UInt(priorityWidth bits))
    val claim = out UInt(log2Up(sourceCount + 1) bits)
    val claimprio = out UInt(priorityWidth bits)
  }

  val gateways = (for ((source, id) <- (io.sources.asBools, 1 to sourceCount).zipped) yield IMSICGateway(
    id = id,
    priorityWidth = priorityWidth
  )).toSeq

  val target = IMSICTarget(
    gateways = gateways,
    priorityWidth = priorityWidth
  )

  io.target := target.iep
  io.claim := target.claim
  io.claimprio := target.claimprio

  target.threshold := io.threshold
  target.ies := io.ies

  for ((ioiprio, ioips, gateway) <- (io.iprios, io.ips, gateways).zipped) {
    gateway.priority := ioiprio
    gateway.ip := ioips
  }

  val bus = new AxiLite4SlaveFactory(io.bus)
  val mapping = IMSICMapper(bus, imsicMapping)(
    target = target,
    priorityWidth = priorityWidth
  )
}

object IMSICVerilog extends App {
  Config.spinal.generateVerilog(AxiLite4IMSIC(64)).printPruned().printPrunedIo()
}
