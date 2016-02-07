package datasource

trait SourceLineFormatter {

  def apply(sourceLineString: String):SourceLine

}
