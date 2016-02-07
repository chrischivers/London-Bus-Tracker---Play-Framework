package datasource

import java.io.{BufferedReader, InputStreamReader}
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClientBuilder}
import play.api.Logger

/**
 * Code adapted from Stack Overflow: http://stackoverflow.com/questions/6024376/apache-httpcomponents-httpclient-timeout
 * @param ds The DataSource
 */

class HttpDataStreamImpl(ds: DataSource) extends DataStream {
  var response: Option[CloseableHttpResponse] = None
  override val WAIT_TIME_AFTER_CLOSE: Int = 10000


  def getStream: Stream[String] = {
    if (streamOpened) {
      Logger.info("Closing opened stream")
      closeStream()
      Thread.sleep(WAIT_TIME_AFTER_CLOSE)
    }
    response = Option(getResponse)
    streamOpened = true
    Logger.debug("Opening Stream")

    if (checkHttpStatusValid(response.get.getStatusLine.getStatusCode)) {
      val br = new BufferedReader(new InputStreamReader(response.get.getEntity.getContent))
      Stream.continually(br.readLine()).takeWhile(_ != null)
    } else {
      Logger.debug("HTTP status not 200. Unable to retrieve input stream")
      throw new IllegalStateException("Unable to retrieve input stream (http status not 200")
    }
  }

  private def getResponse: CloseableHttpResponse = {

    val client = HttpClientBuilder.create()
    client.setDefaultRequestConfig(getRequestBuilder.build())
    client.setDefaultCredentialsProvider(getCredentialsProvider)
    val httpGet = new HttpGet(ds.URL)
    val response = client.build().execute(httpGet)
    response
  }

  private def getCredentialsProvider = {
    val credentialsProvider = new BasicCredentialsProvider()
    val authScope = ds.AUTHSCOPE
    val credentials = new UsernamePasswordCredentials(ds.USERNAME, ds.PASSWORD)
    credentialsProvider.setCredentials(authScope, credentials)
    credentialsProvider
  }

  private def getRequestBuilder = {
    val requestBuilder = RequestConfig.custom()
    requestBuilder.setConnectionRequestTimeout(ds.CONNECTION_TIMEOUT)
    requestBuilder.setConnectTimeout(ds.CONNECTION_TIMEOUT)
  }

  def closeStream() = {
    Logger.debug("Closing Stream")
    response.get.close()
    response = None
    streamOpened = false
  }

  private def checkHttpStatusValid(httpStatusCode: Int): Boolean = httpStatusCode == 200

  override def getNumberLinesToDisregard: Int = ds.NUMBER_LINES_TO_DISREGARD
}
