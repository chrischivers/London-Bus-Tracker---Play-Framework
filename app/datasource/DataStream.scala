package datasource

trait DataStream {

  val WAIT_TIME_AFTER_CLOSE:Int
  @volatile var streamOpened = false
  def getStream: Stream[String]

  def getNumberLinesToDisregard: Int

}
