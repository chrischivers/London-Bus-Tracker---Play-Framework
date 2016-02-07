package datadefinitions

import scala.io.BufferedSource

/**
 * Created by chrischivers on 07/09/15.
 */
trait LoadResourceFromSource extends ResourceOperations{

  val bufferedSource: BufferedSource

}
