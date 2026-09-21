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

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;

import java.util.List;

/**
 * Compatibility for 2.0.2, which emits terminal result events but stores default RUNNING blocks.
 */
final class SessionMessages {
    private SessionMessages() {
    }

    static Msg normalize(Msg message) {
        List<ContentBlock> content = message.getContent().stream()
                .map(block -> {
                    if (block instanceof ToolResultBlock result
                            && result.getState() == ToolResultState.RUNNING
                            && !Boolean.TRUE.equals(
                            result.getMetadata()
                                    .get(ToolResultBlock.METADATA_SUSPENDED))) {
                        return result.withState(ToolResultState.SUCCESS);
                    }
                    return block;
                })
                .toList();
        return message.withContent(content);
    }
}
