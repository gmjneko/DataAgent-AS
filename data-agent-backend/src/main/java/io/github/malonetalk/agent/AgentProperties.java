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

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "agentscope")
public class AgentProperties {
    private boolean mcpEnabled = true;
    @NotBlank private String workspace = ".agentscope/workspace";

    @NotBlank
    @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]{0,63}")
    private String stateTable = "agentscope_agent_state";

    @Min(1)
    private int maxIters = 10;

    @Min(2)
    private int triggerMessages = 30;

    @Min(1)
    private int keepMessages = 10;

    @Min(1)
    private int triggerTokens = 32000;

    @AssertTrue(message = "keepMessages 必须小于 triggerMessages")
    public boolean isCompactionWindowValid() {
        return keepMessages < triggerMessages;
    }

    @AssertTrue(message = "2.x 不可使用旧 agentscope_sessions 表")
    public boolean isStateTableIndependent() {
        return !"agentscope_sessions".equalsIgnoreCase(stateTable);
    }

    @Min(1000)
    private int maxToolResultChars = 80000;
}
