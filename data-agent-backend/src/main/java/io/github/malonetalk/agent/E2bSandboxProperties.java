/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.agent;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "agentscope.e2b")
public class E2bSandboxProperties {

    /** Blank until a self-hosted E2B API key is configured. */
    private String apiKey = "";

    /** Blank until a template with python3, pandas, numpy, and scipy is configured. */
    private String templateId = "";

    /** Placeholder API address. Replace with the self-hosted E2B control plane. */
    private String apiBaseUrl = "http://e2b.example.local";

    /**
     * Fallback domain used when the create-sandbox response omits one. Replace with the
     * self-hosted wildcard domain.
     */
    private String domain = "e2b.example.local";

    @Min(1)
    private int sandboxTimeoutSeconds = 300;

    private String workspaceRoot = "/home/user";

    public boolean isConfigured() {
        return apiKey != null
                && !apiKey.isBlank()
                && templateId != null
                && !templateId.isBlank();
    }

    public String workspaceRoot() {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return "/home/user";
        }
        return workspaceRoot.endsWith("/")
                ? workspaceRoot.substring(0, workspaceRoot.length() - 1)
                : workspaceRoot;
    }
}
