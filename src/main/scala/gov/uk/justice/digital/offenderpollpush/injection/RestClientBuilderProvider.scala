package gov.uk.justice.digital.offenderpollpush.injection

import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import org.apache.http.HttpHost
import org.elasticsearch.client.{RestClient, RestClientBuilder}

class RestClientBuilderProvider @Inject() (@Named("searchScheme") searchScheme: String,
                                           @Named("searchHost") searchHost: String,
                                           @Named("searchPort") searchPort: Int) extends Provider[RestClientBuilder] {

  override def get: RestClientBuilder = RestClient.builder(new HttpHost(searchHost, searchPort, searchScheme))
}
