package com.samir.pricecomparator.config;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;

public class ElasticsearchConfig {

    public ElasticsearchClient createClient() {
        Header[] defaultHeaders = new Header[]{
            new BasicHeader("Accept", "application/json"),
            new BasicHeader("Content-Type", "application/json")
        };

        RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200, "http"))
            .setDefaultHeaders(defaultHeaders)
            .build();

        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());

        return new ElasticsearchClient(transport);
    }
}