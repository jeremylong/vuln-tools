/*
 *  Copyright 2022-2023 Jeremy Long
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
package io.github.jeremylong.nvdlib;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Rate limited client for use with a rate limited API. The client contains two mechanisms to control the rate:
 * <ol>
 * <li>RateMeter - limits the number of calls over a given time period</li>
 * <li>Delay - the minimum delay between calls</li>
 * </ol>
 * <p>
 * The two mechanisms may appear redundant - but each have its purpose in making the calls as fast as can be done while
 * being kind to the endpoint. If one is calling an endpoint, such as the NVD vulnerability API which is limited to 5
 * calls in 30 seconds without an API Key, to retrieve 4 page of data as quickly as possible you could set a smaller
 * delay and still keep the Rate Meter to limit to 5 calls per 30 secnods. However, if you are retrieiving a large
 * number of pages you would want the delay to be slightly under the time period divided by the allowed number of calls
 * (e.g., if we allowed 5 calls over 30 seconds we would use 30/5=6 seconds).
 */
class RateLimitedClient implements AutoCloseable {

    /**
     * Reference to the logger.
     */
    private final static Logger LOG = LoggerFactory.getLogger(SimpleFutureResponse.class);
    /**
     * The underlying Async Client.
     */
    private final CloseableHttpAsyncClient client;
    /**
     * Executor service for asynch implementation.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /**
     * The epoch time of the last request.
     */
    private long lastRequest = 0;
    /**
     * The minimum delay in milliseconds between API calls.
     */
    private long delay = 0;
    /**
     * Rate limiting meter.
     */
    private RateMeter meter;

    /**
     * Construct a rate limited client without a delay or limiters.
     */
    RateLimitedClient() {
        this(0, 0, 0);
    }

    /**
     * Construct a rate limited client with a given delay and request window configuration. This allows callers to
     * configure 5 requests are allowed over a 30-second rolling window and we will delay at least 4 seconds between
     * calls to help more evenly distribute the calls across the request window.
     *
     * @param minimumDelay the number of milliseconds to wait between API calls
     * @param requestsCount the number of requests allowed during the rolling request window timespan
     * @param requestWindowMilliseconds the rolling request window size in milliseconds
     */
    RateLimitedClient(long minimumDelay, int requestsCount, long requestWindowMilliseconds) {
        this(minimumDelay,
                (requestsCount > 0 && requestWindowMilliseconds > 0)
                        ? new RateMeter(requestsCount, requestWindowMilliseconds)
                        : new RateMeter(100, 5));
    }

    /**
     * Construct a rate limited client with a given delay and request window configuration. This allows callers to
     * configure 5 requests are allowed over a 30-second rolling window and we will delay at least 4 seconds between
     * calls to help more evenly distribute the calls across the request window.
     *
     * @param minimumDelay the number of milliseconds to wait between API calls
     * @param meter the rate meter to limit the request rate
     */
    RateLimitedClient(long minimumDelay, RateMeter meter) {
        this.meter = meter;
        this.delay = minimumDelay;
        LOG.debug("rate limited call delay: {}", delay);
        client = HttpAsyncClients.createDefault();
        client.start();
    }

    @Override
    public void close() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Returns the delay between API calls in milliseconds.
     *
     * @return the delay between API calls in milliseconds
     */
    public long getDelay() {
        return delay;
    }

    /**
     * Sets the minimum delay between API calls.
     *
     * @param milliseconds the delay duration
     */
    void setDelay(long milliseconds) {
        this.delay = milliseconds;
        LOG.debug("rate limited call delay: {}", delay);
    }

    /**
     * Ensures the minimum delay has passed since the last call and asynchronously calls the API.
     *
     * @param request the request
     * @return the future response
     */
    Future<SimpleHttpResponse> execute(SimpleHttpRequest request) {
        return executor.submit(() -> {
            return delayedExecute(request);
        });
    }

    /**
     * Ensures the minimum delay has passed since the last call and calls the API.
     *
     * @param request the request
     * @return the future response
     * @throws ExecutionException thrown if there is an exception
     * @throws InterruptedException thrown if interrupted
     */
    private SimpleHttpResponse delayedExecute(SimpleHttpRequest request)
            throws ExecutionException, InterruptedException {
        if (lastRequest > 0 && delay > 0) {
            long now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
            long wait = delay - (now - lastRequest);
            if (wait > 0) {
                LOG.info("waiting for {}", wait);
                Thread.sleep(wait);
            }
        }
        try (RateMeter.Ticket t = meter.getTicket()) {
            if (LOG.isDebugEnabled()) {
                LocalTime time = LocalTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                LOG.debug("Requested At: {}; URI: {}", time.format(formatter), request.getRequestUri());
            }
            Future<SimpleHttpResponse> f = client.execute(request, new SimpleFutureResponse());
            lastRequest = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
            return f.get();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            } else if (e instanceof ExecutionException) {
                throw (ExecutionException) e;
            }
            throw new NvdApiException(e);
        }
    }

    /**
     * Future response.
     */
    class SimpleFutureResponse implements FutureCallback<SimpleHttpResponse> {
        /**
         * Reference to the logger.
         */
        private final Logger log = LoggerFactory.getLogger(SimpleFutureResponse.class);

        @Override
        public void completed(SimpleHttpResponse result) {
            // String response = result.getBodyText();
            // log.debug("response::{}", response);
        }

        @Override
        public void failed(Exception ex) {
            log.debug("request failed", ex);
        }

        @Override
        public void cancelled() {
            // do nothing
        }
    }
}
