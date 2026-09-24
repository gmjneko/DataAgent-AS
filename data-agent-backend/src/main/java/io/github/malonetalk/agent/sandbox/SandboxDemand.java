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
package io.github.malonetalk.agent.sandbox;

import java.util.Set;

/** Marker placed on a call when the model invokes a tool that needs the E2B sandbox. */
public final class SandboxDemand {

    public static final SandboxDemand ACTIVE = new SandboxDemand();

    public static final Set<String> TOOLS =
            Set.of(
                    "read_file",
                    "write_file",
                    "edit_file",
                    "grep_files",
                    "glob_files",
                    "list_files",
                    "execute_python");

    private SandboxDemand() {}
}
