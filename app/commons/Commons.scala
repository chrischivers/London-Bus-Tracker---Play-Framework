package commons

import java.util.{Calendar, GregorianCalendar}
import org.json4s.native.JsonMethods._
import streaming.PackagedStreamObject
import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._


object Commons {

  implicit class DayTimeOperations(timeInMillis: Long) {
    /**
     * Retrieves the day code from time in millis
     * @return the day code (MON, TUE... etc)
     */
    def getDayCode: String = {
      val cal: Calendar = new GregorianCalendar()

      cal.setTimeInMillis(timeInMillis)
      cal.get(Calendar.DAY_OF_WEEK) match {
        case Calendar.MONDAY => "MON"
        case Calendar.TUESDAY => "TUE"
        case Calendar.WEDNESDAY => "WED"
        case Calendar.THURSDAY => "THU"
        case Calendar.FRIDAY => "FRI"
        case Calendar.SATURDAY => "SAT"
        case Calendar.SUNDAY => "SUN"
      }
    }

    /**
     * Retrieves the time offset from 00:00 in seconds from time in millis
     * @return seconds since 00:00 (e.g. 1:00am = 3600 seconds)
     */
    def getTimeOffset: Int = {
      val existingTime: Calendar = new GregorianCalendar()
      existingTime.setTimeInMillis(timeInMillis)

      val beginningOfDayTime: Calendar = new GregorianCalendar(existingTime.get(Calendar.YEAR), existingTime.get(Calendar.MONTH), existingTime.get(Calendar.DAY_OF_MONTH))
      ((existingTime.getTimeInMillis - beginningOfDayTime.getTimeInMillis) / 1000).toInt
    }
  }

  /**
   * Gets the movement data array sent to the clients from an encoded polyLine
   * @param encodedPolyLine The encoded polyLine
   * @return An String Array of Latitude, Longitude, Rotation and Proportional Distance
   */
  def getMovementDataArray(encodedPolyLine: String):Array[(String,String,String,String)] = {
    val decodedPolyLine = decodePolyLine(encodedPolyLine)

    var arrayBuild: Array[(Double,Double, Int, Double)] = Array()
    arrayBuild = arrayBuild :+ (decodedPolyLine(0)._1, decodedPolyLine(0)._2, 0, 0.0) //Initial entry for first point

    for(i <- 1 until decodedPolyLine.length) {
      val prevLat = decodedPolyLine(i - 1)._1
      val prevLng = decodedPolyLine(i - 1)._2
      val thisLat = decodedPolyLine(i)._1
      val thisLng = decodedPolyLine(i)._2
      val rotationToHere = getRotation(prevLat, prevLng, thisLat, thisLng)
      val distanceToHere = getDistance(prevLat, prevLng, thisLat, thisLng)
      arrayBuild = arrayBuild :+ (thisLat, thisLng, rotationToHere, distanceToHere) //Initial entry for first point

    }
    val sumOfDistances = arrayBuild.foldLeft(0.0) {(total, n) =>
      total + n._4
    }

    arrayBuild.map { case (lat, lng, rot, dist) =>
      (BigDecimal(lat).setScale(6, RoundingMode.HALF_UP).toString(),
        BigDecimal(lng).setScale(6, RoundingMode.HALF_UP).toString(),
        rot.toString,
        try {
          BigDecimal(dist / sumOfDistances).setScale(2, RoundingMode.HALF_UP).toString()
        } catch {
          case e: NumberFormatException => "0" // returns 0 as default
        })
    }
  }

  /**
   * Decodes polyLine
   * The code in this method was adapted from the Java Decode Method of Google's PolyUtil Class from Android Map Utils
   * https://github.com/googlemaps/android-maps-utils/blob/master/library/src/com/google/maps/android/PolyUtil.java
   * @param encodedPolyLine The Encoded PolyLine
   * @return The decoded polyLine as an Array series of Latitudes and Longitudes
   */
  def decodePolyLine(encodedPolyLine: String): Array[(Double, Double)] = {

    val len: Int = encodedPolyLine.length
    var latLngList: Array[(Double,Double)] = Array()

    var index: Int = 0
    var lat: Int = 0
    var lng: Int = 0

    while (index < len) {
      var result: Int = 1
      var shift: Int = 0
      var b: Int = 0

      do {
        b = encodedPolyLine.charAt(index) - 63 - 1
        index += 1
        result += b << shift
        shift += 5
      } while (b >= 0x1f)


      lat += (if ((result & 1) != 0) ~(result >> 1) else result >> 1)

      result = 1
      shift = 0

      do {
        b = encodedPolyLine.charAt(index) - 63 - 1
        index += 1
        result += b << shift
        shift += 5
      } while (b >= 0x1f)

      lng += (if ((result & 1) != 0) ~(result >> 1) else result >> 1)

      val x = (lat * 1e-5, lng * 1e-5)
      latLngList = latLngList :+ x
    }
    latLngList
  }

  def rad(x:Double) = x * Math.PI / 180

  /**
   * Gets distance between two pairs of latitudes and longitudes
   * This method was adapted from code at : http://stackoverflow.com/questions/1502590/calculate-distance-between-two-points-in-google-maps-v3
   * @param lat1 laitude 1
   * @param lng1 longitude 1
   * @param lat2 latitude 2
   * @param lng2 longitude 2
   * @return The distance in metres
   */
  def getDistance(lat1:Double, lng1:Double, lat2:Double, lng2:Double): Double = {
    val R = 6378137 // Earth’s mean radius in meter
    val dLat = rad(lat2 - lat1)
    val dLong = rad(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(rad(lat1)) * Math.cos(rad(lat2)) *
        Math.sin(dLong / 2) * Math.sin(dLong / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    val d = R * c
    d
  }

  /**
   * Gets rotation between two pairs of latitudes and longitudes
   * This method was adapted from code at: http://stackoverflow.com/questions/2908892/get-degrees-0-360-from-one-latlng-to-another-in-javascript
   * @param lat1 latitude 1
   * @param lng1 longitude 1
   * @param lat2 latitude 2
   * @param lng2 longitude 2
   * @return The rotation in degrees
   */
  def getRotation(lat1:Double, lng1:Double, lat2:Double, lng2:Double): Int = {
    val lat1x = lat1 * Math.PI / 180
    val lat2x = lat2 * Math.PI / 180
    val dLon = (lng2 - lng1) * Math.PI / 180

    val y = Math.sin(dLon) * Math.cos(lat2x)
    val x = Math.cos(lat1x) * Math.sin(lat2x) -
      Math.sin(lat1x) * Math.cos(lat2x) * Math.cos(dLon)

    val brng = Math.atan2(y, x)
    (((brng * 180 / Math.PI) + 180) % 360).toInt
  }

  /**
   * Encodes a package of live bus movements to JSON
   * @param next The next object to be encoded
   * @return A string in JSON format
   */
  def encodePackageObject(next: PackagedStreamObject): String =
  {
    val streamFields = Array("reg", "nextArr", "movementData", "routeID", "directionID", "towards", "nextStopID", "nextStopName")

    val nextList = Map(
      streamFields(0) -> next.reg,
      streamFields(1) -> next.nextArrivalTime,
      streamFields(2) -> compact(render(formatMarkerMovementData(next.markerMovementData))),
      streamFields(3) -> next.route_ID,
      streamFields(4) -> next.direction_ID.toString,
      streamFields(5) -> next.towards,
      streamFields(6) -> next.nextStopID,
      streamFields(7) -> next.nextStopName)
    //val json = compact(render(nextList))
    compact(render(nextList))
    // val nextChunk = MessageChunk("data: " + json + "\n\n")}

  }
  def formatMarkerMovementData(mda: Array[(String, String, String, String)]):ListBuffer[String] = {
    var list:ListBuffer[String] = ListBuffer()
    var lastLat:Double = 0.0
    var lastLng: Double = 0.0
    mda.foreach({case (lat, lng, rot, propDis) =>
      if (lastLat == 0.0 && lastLng == 0.0) {
        list += lat + "," + lng + "," + rot + "," + propDis
        lastLat = lat.toDouble
        lastLng = lng.toDouble
      } else {
        val latDouble = lat.toDouble
        val lngDouble = lng.toDouble
        val latDif:Int = ((lastLat - latDouble) * 100000).toInt
        val lngDif:Int = ((lastLng - lngDouble) * 100000).toInt
        list += latDif + "," + lngDif + "," + rot + "," + propDis
        lastLat = latDouble
        lastLng = lngDouble
      }
    })
    list
  }

}
