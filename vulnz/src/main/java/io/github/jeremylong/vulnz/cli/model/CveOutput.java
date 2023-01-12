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
package io.github.jeremylong.vulnz.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.jeremylong.nvdlib.nvd.CveItem;
import io.github.jeremylong.nvdlib.nvd.DefCveItem;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "reason", "lastModifiedDate", "count"})
public class CveOutput {

    @JsonProperty("success")
    private boolean success;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("count")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private int count;
    @JsonProperty("lastModifiedDate")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private LocalDateTime lastModifiedDate;

    public void setLastModifiedDate(long lastModifiedDate) {
        LocalDateTime utcDate = LocalDateTime.ofEpochSecond(lastModifiedDate, 0, ZoneOffset.UTC);
        this.lastModifiedDate = utcDate;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public int getCount() {
        return count;
    }

    public void addCount(int size) {
        count += size;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
