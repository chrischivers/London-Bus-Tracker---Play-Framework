import filters.BasicAuthFilter
import play.api.mvc.WithFilters

/**
 * Created by chrischivers on 09/02/16.
 */
object Global extends WithFilters(BasicAuthFilter)
