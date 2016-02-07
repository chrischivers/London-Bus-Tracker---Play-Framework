package database

/**
 * Database Objects
 */
sealed trait Databases {
  val name: String
}

case object PREDICTION_DATABASE extends Databases {
  override val name: String = "PredictionDB"
}
