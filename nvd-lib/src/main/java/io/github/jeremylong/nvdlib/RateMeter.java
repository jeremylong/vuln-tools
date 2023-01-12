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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * A rate limiting meter that works by granting tickets. The tickets are closable - do not leak tickets. The tickets are
 * auto-closable so should be granted in a try with resources:
 *
 * <pre>
 * int duration = 30000;
 * int queueSize = 5;
 * RateMeter instance = new RateMeter(queueSize, duration);
 * try (RateMeter.Ticket t = instance.getTicket()) {
 *     // do something
 * }
 * </pre>
 */
public class RateMeter {
    /**
     * Reference to the logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(RateMeter.class);
    private final BlockingQueue<Ticket> queue = new DelayQueue<>();
    private int quantity;
    private long durationMilliseconds;

    /**
     * Creates a new rate meter to limit how quickly an operation can take place.
     *
     * @param quantity the number of tickets available
     * @param durationMilliseconds the duration of a ticket in milliseconds
     */
    public RateMeter(int quantity, long durationMilliseconds) {
        this.quantity = quantity;
        this.durationMilliseconds = durationMilliseconds;
    }

    /**
     * Grants a ticket to proceed given the rate limit. Tickets are closable - do not leak tickets; remember to close
     * them.
     *
     * @return a ticket
     * @throws InterruptedException thrown if interrupted
     */
    public synchronized Ticket getTicket() throws InterruptedException {
        if (queue.size() >= quantity) {
            Ticket ticket = queue.take();
            if (LOG.isDebugEnabled()) {
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                final LocalTime time = LocalTime.now();
                LOG.debug("Ticket taken At: {}; count: {}", time.format(formatter), queue.size() + 1);
            }
            return ticket;
        }
        if (LOG.isDebugEnabled()) {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            final LocalTime time = LocalTime.now();
            LOG.debug("Ticket taken At: {}; count: {}", time.format(formatter), queue.size() + 1);
        }
        return new Ticket(this);
    }

    synchronized void replaceTicket() throws InterruptedException {
        if (queue.size() < quantity) {
            if (LOG.isDebugEnabled()) {
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                final LocalTime time = LocalTime.now();
                LOG.debug("Ticket returned At: {}; count: {}", time.format(formatter), queue.size() + 1);
            }
            queue.put(new Ticket(this));
        }
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getDurationMilliseconds() {
        return durationMilliseconds;
    }

    public void setDurationMilliseconds(long durationMilliseconds) {
        this.durationMilliseconds = durationMilliseconds;
    }

    public static class Ticket implements Delayed, AutoCloseable {
        private final RateMeter meter;
        private final long startTime;

        Ticket(RateMeter meter) {
            this.meter = meter;
            this.startTime = System.currentTimeMillis() + meter.getDurationMilliseconds();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = startTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return (int) (this.startTime - ((Ticket) o).startTime);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Ticket ticket = (Ticket) o;
            return startTime == ticket.startTime;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startTime);
        }

        @Override
        public void close() throws Exception {
            meter.replaceTicket();
        }
    }
}
