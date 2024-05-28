import mill._, scalalib._

val spinalVersion = "1.10.1"

object aia extends SbtModule {
  def scalatestVersion = "3.2.14"
  def scalaVersion = "2.12.18"
  override def millSourcePath = os.pwd
  def sources = T.sources(
    millSourcePath / "hw" / "src"
  )
  def ivyDeps = Agg(
    ivy"com.github.spinalhdl::spinalhdl-core:$spinalVersion",
    ivy"com.github.spinalhdl::spinalhdl-lib:$spinalVersion"
  )
  def scalacPluginIvyDeps = Agg(ivy"com.github.spinalhdl::spinalhdl-idsl-plugin:$spinalVersion")
}
