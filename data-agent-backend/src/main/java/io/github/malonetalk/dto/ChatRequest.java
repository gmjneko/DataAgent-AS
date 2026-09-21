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
package io.github.malonetalk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatRequest(
        @NotBlank(message = "sessionId 不能为空")
                @Pattern(regexp = "[A-Za-z0-9_-]{1,128}", message = "sessionId 格式不正确")
                String sessionId,
        @Size(max = 200000) String message,
        @Valid @Size(max = 20) List<ToolResultInput> toolResults,
        Integer datasourceId) {

    public record ToolResultInput(
            @NotBlank String toolCallId,
            @NotBlank String toolName,
            @NotBlank @Size(max = 200000) String output) {}
}
