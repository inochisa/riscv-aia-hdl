package aia

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.{BusSlaveFactory, AllMapping, SingleMapping}

// case class AxiLite4IMSIC(sourceCount : Int) extends Component{
//   import spinal.lib.bus.amba4.axilite._
//   val priorityWidth = 8
//   val imsicMapping = IMSICMapping.light
//   import imsicMapping._

//   val io = new Bundle {
//     val bus = slave(AxiLite4(AxiLite4Config(22, 32)))
//     val sources = in Bits (sourceCount bits)
//     val ips = Vec.fill(sourceCount)(in Bool())
//     val iprios = Vec.fill(sourceCount)(in UInt(priorityWidth bits))
//   }

//   val gateways = (for ((source, id) <- (io.sources.asBools, 1 to sourceCount).zipped) yield IMSICGateway(
//     id = id,
//     priorityWidth = priorityWidth
//   )).toSeq

//   for ((ioiprio, ioips, gateway) <- (io.iprios, io.ips, gateways).zipped) {
//     gateway.priority := ioiprio
//     gateway.ip := ioips
//   }

//   val bus = new AxiLite4SlaveFactory(io.bus)
//   val mapping = IMSICMapper(bus, imsicMapping)(
//     gateways = gateways,
//     priorityWidth = priorityWidth
//   )

//   io.target := mapping.multiplexe.iep
//   io.claim := mapping.multiplexe.claim
//   io.claimprio := mapping.multiplexe.claimprio

//   mapping.multiplexe.threshold := io.threshold
//   mapping.multiplexe.ies := io.ies
// }

class MappedIMSIC[T <: spinal.core.Data with IMasterSlave](sources : Int,
                                                           busType : HardType[T],
                                                           factoryGen: T => BusSlaveFactory) extends Component{
  val idWidth = log2Up(sources + 1)

  val io = new Bundle {
    val bus = slave(busType())
    val eip = out Bits(sources bits)
    val eie = in Bits(sources bits) default(0)
    val claim = out UInt(idWidth bits)
    val threshold = in UInt(idWidth bits) default(0)
  }

  val sourceIds = for (i <- 1 to sources) yield i
  val factory = factoryGen(io.bus)
  val logic = IMSIC(sourceIds)

  logic.driveFrom(factory)

  for(sourceId <- sourceIds.indices){
    io.eip(sourceId) := logic.interrupts(sourceId).ip
    logic.interrupts(sourceId).ie := io.eie(sourceId)
  }

  io.claim := logic.claim
  logic.threshold := io.threshold
}

case class TilelinkIMSIC(sources : Int, p : bus.tilelink.BusParameter) extends MappedIMSIC[bus.tilelink.Bus](
  sources,
  new bus.tilelink.Bus(p),
  new bus.tilelink.SlaveFactory(_, true)
)

object IMSICSim extends App {
  import spinal.core.sim._
  import spinal.lib.bus.tilelink

  val sourceInterrupt = 4

  val compile = Config.sim.compile{
    val imsic = new TilelinkIMSIC(sourceInterrupt,
      tilelink.M2sParameters(
        sourceCount = 1,
        support = tilelink.M2sSupport(
          addressWidth = 16,
          dataWidth = 32,
          transfers = tilelink.M2sTransfers(
            get = tilelink.SizeRange(8),
            putFull = tilelink.SizeRange(8)
          )
        )
      ).toNodeParameters().toBusParameter()
    )

    imsic
  }

  compile.doSim{ dut =>
    dut.io.eie #= 0xf
    dut.io.threshold #= 0

    // dut.io.ips #= Array.fill(dut.io.ips.getWidth)(false)
    dut.clockDomain.forkStimulus(10)
    implicit val idAllocator = new tilelink.sim.IdAllocator(tilelink.DebugId.width)
    val agent = new tilelink.sim.MasterAgent(dut.io.bus, dut.clockDomain)

    print(agent.putFullData(0, 0x0, 4.toInt.toBytes.toSeq))
    print(agent.putFullData(0, 0x0, 3.toInt.toBytes.toSeq))

    dut.clockDomain.waitSampling(30)
    println("--- at end ---")
    println("eip: 0b" + dut.io.eip.toInt.binString())
    println("topei: " + dut.io.claim.toInt.toString())
  }
}

object IMSICVerilog extends App {
  import spinal.lib.bus.amba4.axilite._
  import spinal.lib.bus.tilelink
  // Config.spinal.generateVerilog(AxiLite4IMSIC(8)).printPruned().printPrunedIo()
  Config.spinal.generateVerilog(TilelinkIMSIC(8,
    tilelink.M2sParameters(
      sourceCount = 1,
      support = tilelink.M2sSupport(
        addressWidth = 16,
        dataWidth = 32,
        transfers = tilelink.M2sTransfers(
          get = tilelink.SizeRange(8),
          putFull = tilelink.SizeRange(8)
        )
      )
    ).toNodeParameters().toBusParameter()
  )).printPruned().printPrunedIo()
}
