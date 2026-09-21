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

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Only application-registered tools are allowed; disabled MCP tools lose their rules. */
@Component
public class ToolPermissions {
    private final Set<String> allowed = ConcurrentHashMap.newKeySet();

    public void allow(String name) {
        allowed.add(name);
    }

    public void remove(String name) {
        allowed.remove(name);
    }

    public PermissionContextState snapshot() {
        var builder = PermissionContextState.builder().mode(PermissionMode.DONT_ASK);
        allowed.stream()
                .sorted()
                .forEach(
                        name ->
                                builder.addAllowRule(
                                        name,
                                        new PermissionRule(
                                                name,
                                                null,
                                                PermissionBehavior.ALLOW,
                                                "data-agent")));
        return builder.build();
    }
}
