package aia

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.{BusSlaveFactory, AllMapping, SingleMapping}
import scala.collection.Seq

case class IMSICMapping(
  seteipLE            : Int,
  seteipBE            : Int,
  ipiGenBE            : Boolean,
  iprioGen            : Boolean
)

object IMSICMapping {
  def full = IMSICMapping(
    seteipLE    = 0x000,
    seteipBE    = 0x004,
    ipiGenBE    = true,
    iprioGen    = true,
  )

  def light = IMSICMapping(
    seteipLE    = 0x000,
    seteipBE    = 0x004,
    ipiGenBE    = false,
    iprioGen    = false,
  )
}

object IMSICMapper {
  def apply(bus: BusSlaveFactory, mapping: IMSICMapping)(target: IMSICTarget, priorityWidth: Int) = new Area {
    import mapping._

    val idWidth = log2Up((target.gateways.map(_.id) ++ Seq(0)).max + 1)
    val claim = bus.createAndDriveFlow(UInt(idWidth bits), address = seteipLE)
    when(claim.valid) {
      switch(claim.payload) {
        for (gateway <- target.gateways) {
          is(gateway.id) {
            gateway.ip := True
          }
        }
      }
    }

    /* ignore read event and always return 0 */
    val ignore = Reg(UInt(32 bits))
    ignore := 0
    bus.read(ignore, address = seteipLE)
    bus.read(ignore, address = seteipBE)

    ;
  }
}
