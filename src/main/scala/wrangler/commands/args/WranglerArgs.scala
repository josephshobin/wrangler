package wrangler.commands.args

import com.quantifind.sumac.{ConfigArgs, FieldArgs}

trait WranglerArgs extends FieldArgs with ConfigArgs {
  override val configPrefix = "wrangler"

  configFiles = List("wrangler.conf")
}
