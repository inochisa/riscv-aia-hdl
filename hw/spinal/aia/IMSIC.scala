package aia

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.{BusSlaveFactory, AllMapping, SingleMapping}
import scala.collection.Seq

case class IMSICGateway(val id : Int) extends Area{
  /** map to eip[id] */
  val ip = RegInit(False)
  val waitCompletion = RegInit(False)

  when(!waitCompletion){
    ip := True
    waitCompletion := True
  }

  def doClaim(): Unit = ip := False
  def doCompletion(): Unit = waitCompletion := False
  def driveFrom(bus: BusSlaveFactory, offset: Int): Unit = {}
}

case class IMSICMapping(
  seteipLE            : Int,
  seteipBE            : Int,
  ipiGenBE            : Boolean,
)

object IMSICMapping {
  def full = IMSICMapping(
    seteipLE    = 0x000,
    seteipBE    = 0x004,
    ipiGenBE    = true,
  )

  def light = IMSICMapping(
    seteipLE    = 0x000,
    seteipBE    = 0x004,
    ipiGenBE    = false,
  )
}

case class IMSIC(sourceIds: Seq[Int]) extends Area {
  val maxSource = (sourceIds ++ Seq(0)).max + 1
  val idWidth = log2Up(maxSource)

  val interrupts = for (sourceId <- sourceIds) yield new Area {
    val id = sourceId
    val ie = RegInit(False)
    val ip = RegInit(False)
  }

  val threshold = UInt(idWidth bits)

  def Request(id : UInt, valid : Bool) = {
    val ret = new Request
    ret.id := id
    ret.valid := valid
    ret
  }

  case class Request() extends Bundle{
    val id = UInt(idWidth bits)
    val valid = Bool()
  }

  val requests = Request(U(maxSource), True) +: interrupts.sortBy(_.id).map(g =>
    Request(
      id       = U(g.id),
      valid    = g.ip && g.ie
    )
  )

  val bestRequest = RegNext(requests.reduceBalancedTree((a, b) => {
    val takeA = !b.valid || (a.valid && a.id <= b.id)
    takeA ? a | b
  }))

  val iep = (bestRequest.id < maxSource) && ((threshold === 0) || (bestRequest.id < threshold))
  val claim = iep ? bestRequest.id | 0

  def driveFrom(bus: BusSlaveFactory) = new Area{
    val SETEIPNUM_LE_ADDR = 0x000
    val SETEIPNUM_BE_ADDR = 0x004

    /* Main work, mapping the irq set */
    val target = Flow(UInt(idWidth bits))
    target.valid := False
    target.payload.assignDontCare()
    when(target.valid) {
      switch(target.payload) {
        for (interrupt <- interrupts) {
          is(interrupt.id) {
            interrupt.ip := True
          }
        }
      }
    }

    val targetDriveLE = bus.createAndDriveFlow(UInt(idWidth bits), address = SETEIPNUM_LE_ADDR)
    when(targetDriveLE.valid) {
      target.valid := True
      target.payload := targetDriveLE.payload
    }

    val targetDriveBE = bus.createAndDriveFlow(UInt(idWidth bits), address = SETEIPNUM_BE_ADDR)
    when(targetDriveBE.valid) {
      target.valid := True
      target.payload := targetDriveBE.payload
    }
  }
}
