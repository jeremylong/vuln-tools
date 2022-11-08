/*
 *  Copyright 2022 Jeremy Long
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.jeremylong.nvdlib;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.jeremylong.nvdlib.nvd.CveApiJson20;
import dev.jeremylong.nvdlib.nvd.DefCveItem;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A simple client for the NVD CVE API. Use the NvdCveApiBuilder
 * with the desired filters to build the client and then iterate
 * over the results:
 *
 * <pre>
 * try (NvdCveApi api = NvdCveApiBuilder.aNvdCveApi().build()) {
 *   while (api.hasNext()) {
 *     Collection<DefCveItem> items = api.next();
 *   }
 * }
 * </pre>
 *
 * @author Jeremy Long
 * @see <a href="https://nvd.nist.gov/developers/vulnerabilities">NVD CVE API</a>
 */
public class NvdCveApi implements AutoCloseable, Iterator<Collection<DefCveItem>> {
    /**
     * The default endpoint for the NVD CVE API.
     */
    private final static String DEFAULT_ENDPOINT = "https://services.nvd.nist.gov/rest/json/cves/2.0";
    /**
     * The header name for the NVD API Key.
     */
    private static final String API_KEY_NAME = "apiKey";
    /**
     * The rate limited HTTP client for calling the NVD APIs.
     */
    private RateLimitedClient client;
    /**
     * The NVD API key; can be null if a key is not used.
     */
    private String apiKey;
    /**
     * The NVD API endpoint used to call the NVD CVE API.
     */
    private String endpoint;
    /**
     * The index to obtain the paged results from the NVD CVE.
     */
    private int index = 0;
    /**
     * The number of results per page.
     */
    private int resultsPerPage = 2000;
    /**
     * The total results from the NVD CVE API call.
     */
    private int totalResults = -1;

    /**
     * The epoch time of the last modified request date (i.e., the UTC epoch of
     * the last update of the entire NVD CVE Data Set).
     */
    private long lastModifiedRequest = 0;
    /**
     * A list of filters to apply to the request.
     */
    private List<NameValuePair> filters;
    /**
     * The asynch future for the HTTP responses.
     */
    private Future<SimpleHttpResponse> future;
    /**
     * Jackson object mapper.
     */
    private ObjectMapper objectMapper;

    /**
     * Constructs a new NVD CVE API client.
     *
     * @param apiKey the api key; can be null
     * @param endpoint the endpoint for the NVD CVE API; if null the default endpoint is used
     */
    NvdCveApi(String apiKey, String endpoint) {
        this.apiKey = apiKey;
        if (endpoint == null) {
            this.endpoint = DEFAULT_ENDPOINT;
        } else {
            this.endpoint = endpoint;
        }
        client = new RateLimitedClient();
        objectMapper = new ObjectMapper();
        //objectMapper.findAndRegisterModules();
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Limits how quickly requests can be sent ensuring a minimum delay between requests.
     *
     * @param milliseconds the number of milliseconds to wait between requests
     */
    void setDelay(long milliseconds) {
        this.client.setDelay(milliseconds);
    }

    /**
     * Gets the UTC epoch of the last request. Used in subsequent
     * calls to the API with the {@link dev.jeremylong.nvdlib.NvdCveApiBuilder#withLastModifiedFilter NvdCveApiBuilder#withLastModifiedFilter()}.
     *
     * @return the last modified datetime
     */
    public long getLastModifiedRequest() {
        return lastModifiedRequest;
    }

    /**
     * Set the filter parameters for the NVD CVE API calls.
     *
     * @param filters the list of parameters used to filter the results in the API call
     */
    void setFilters(List<NameValuePair> filters) {
        this.filters = filters;
    }

    /**
     * The number of results per page; the default is 2000.
     *
     * @param resultsPerPage the number of results per page
     */
    void setResultsPerPage(int resultsPerPage) {
        this.resultsPerPage = resultsPerPage;
    }

    /**
     * Asynchronously calls the NVD CVE API.
     *
     * @throws NvdApiException thrown if there is a problem calling the API
     */
    private void callApi() throws NvdApiException {
        try {
            URIBuilder uriBuilder = new URIBuilder(endpoint);
            if (filters != null) {
                uriBuilder.addParameters(filters);
            }
            uriBuilder.addParameter("resultsPerPage", Integer.toString(resultsPerPage));
            uriBuilder.addParameter("startIndex", Integer.toString(index));
            final SimpleRequestBuilder builder = SimpleRequestBuilder.get();
            if (apiKey != null) {
                builder.addHeader(API_KEY_NAME, apiKey);
            }
            final SimpleHttpRequest request = builder
                    .setUri(uriBuilder.build())
                    .build();
            future = client.execute(request);
        } catch (URISyntaxException e) {
            throw new NvdApiException(e);
        }
        index += 1;
    }

    @Override
    public void close() throws Exception {
        client.close();
        client = null;
    }

    @Override
    public boolean hasNext() {
        if (future == null || totalResults < 0) {
            return true;
        }
        return totalResults > ((index - 1) * resultsPerPage);
    }

    @Override
    public Collection<DefCveItem> next() {
        if (future == null) {
            callApi();
        }
        try {
            SimpleHttpResponse response = future.get();
            String json = response.getBodyText();
            CveApiJson20 current = objectMapper.readValue(json, CveApiJson20.class);
            this.totalResults = current.getTotalResults();
            this.lastModifiedRequest = current.getTimestamp().toEpochSecond(ZoneOffset.UTC);
            callApi();
            return current.getVulnerabilities();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NvdApiException(e);
        } catch (ExecutionException e) {
            throw new NvdApiException(e);
        } catch (JsonMappingException e) {
            throw new NvdApiException(e);
        } catch (JsonProcessingException e) {
            throw new NvdApiException(e);
        }
    }
}
