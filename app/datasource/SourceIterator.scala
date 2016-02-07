package datasource

class SourceIterator(dataStream: DataStream) extends Iterable[String] {

  override def iterator: Iterator[String] = {
    val x= dataStream.getStream

    //disregards the first N lines from the Stream as specified in the variables
    x.drop(dataStream.getNumberLinesToDisregard).iterator
  }
}
