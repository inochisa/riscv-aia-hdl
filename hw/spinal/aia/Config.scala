package aia

import spinal.core._
import spinal.core.sim._

object Config {
  def spinal = SpinalConfig(
    targetDirectory = "hw/gen",
    defaultConfigForClockDomains = ClockDomainConfig(
      resetActiveLevel = HIGH
    ),
    onlyStdLogicVectorAtTopLevelIo = true,
    removePruned = true,
  )

  def sim = SimConfig.workspacePath("hw/gen").withConfig(spinal).withVcdWave
}
