package datasource.tfl

import datasource.SourceLineFormatter
import play.api.Logger


object TFLSourceLineFormatterImpl extends SourceLineFormatter {

  override def apply(sourceLineString: String): TFLSourceLineImpl = {
    val x = splitLine(sourceLineString)
    checkArrayCorrectLength(x)
    new TFLSourceLineImpl(x(0), x(1), x(2).toInt, x(4).replaceAll("[^a-zA-Z]", ""), x(5).toLong, x(3))
  }


  def splitLine(line: String) = line
    .substring(1,line.length-1) // remove leading and trailing square brackets,
    .split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")
    .map(_.replaceAll("\"","")) //takes out double quotations after split
    .map(_.trim) // remove trailing or leading white space
     .tail // discards the first element (always '1')

  def checkArrayCorrectLength(array: Array[String]) = {
    if (array.length != TFLDataSourceImpl.fieldVector.length) {
      Logger.debug("Source array has incorrect number of elements. Or invalid web page retrieved \n " + array)
      throw new IllegalArgumentException("Source array has incorrect number of elements. Or invalid web page retrieved \n " + array)
    }
  }
}
