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
package io.github.malonetalk.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxAcquireResult;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.github.malonetalk.agent.E2bSandboxProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutePythonToolTest {

    private final ExecutePythonTool tool = new ExecutePythonTool(new E2bSandboxProperties());

    @Test
    void writesScriptThenRunsIsolatedPython() throws Exception {
        FakeSandbox sandbox = new FakeSandbox();

        String result = tool.executePython("print(1)", context(sandbox));

        assertEquals("ok", result);
        assertTrue(commandContaining(sandbox, "python3 -c ").contains(base64("print(1)")));
        assertTrue(commandContaining(sandbox, "python3 -I ").contains("/home/user/pyexec-"));
        int writeAt = indexStartingWith(sandbox, "python3 -c ");
        int runAt = indexStartingWith(sandbox, "python3 -I ");
        assertTrue(writeAt < runAt);
        assertTrue(lastIndexStartingWith(sandbox, "rm -f ") > runAt);
    }

    @Test
    void nonzeroExitBecomesErrorText() throws Exception {
        FakeSandbox sandbox = new FakeSandbox();
        sandbox.pythonFailure = new SandboxException.ExecException(2, "partial", "traceback");

        String result = tool.executePython("raise SystemExit(2)", context(sandbox));

        assertEquals("Error (exit 2):\npartial\ntraceback", result);
    }

    @Test
    void timeoutBecomesErrorText() {
        FakeSandbox sandbox = new FakeSandbox();
        sandbox.pythonFailure = new SandboxException.ExecTimeoutException("python3 -I", 30);

        String result = tool.executePython("while True: pass", context(sandbox));

        assertEquals("Error: execution timed out after 30 seconds.", result);
    }

    @Test
    void truncatedOutputIsMarked() {
        FakeSandbox sandbox = new FakeSandbox();
        sandbox.pythonResult = new ExecResult(0, "big", "", true);

        String result = tool.executePython("print('big')", context(sandbox));

        assertEquals("big\n[Output truncated at 512 KB]", result);
    }

    @Test
    void missingSandboxDoesNotExecute() {
        RuntimeContext ctx = RuntimeContext.builder().sessionId("conv-1").build();

        String result = tool.executePython("print(1)", ctx);

        assertEquals("Error: E2B sandbox is not active for this call.", result);
    }

    private static RuntimeContext context(Sandbox sandbox) {
        return RuntimeContext.builder()
                .sessionId("conv-1")
                .put(SandboxAcquireResult.class, SandboxAcquireResult.selfManaged(sandbox))
                .build();
    }

    private static String commandContaining(FakeSandbox sandbox, String prefix) {
        return sandbox.commands.stream()
                .filter(command -> command.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    private static int indexStartingWith(FakeSandbox sandbox, String prefix) {
        for (int i = 0; i < sandbox.commands.size(); i++) {
            if (sandbox.commands.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexStartingWith(FakeSandbox sandbox, String prefix) {
        for (int i = sandbox.commands.size() - 1; i >= 0; i--) {
            if (sandbox.commands.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class FakeSandbox implements Sandbox {
        private final List<String> commands = new ArrayList<>();
        private RuntimeException pythonFailure;
        private ExecResult pythonResult = new ExecResult(0, "ok", "", false);

        @Override
        public ExecResult exec(
                RuntimeContext runtimeContext, String command, Integer timeoutSeconds)
                throws Exception {
            commands.add(command);
            if (command.startsWith("python3 -I ") && pythonFailure != null) {
                throw pythonFailure;
            }
            if (command.startsWith("python3 -I ")) {
                return pythonResult;
            }
            return new ExecResult(0, "", "", false);
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void shutdown() {}

        @Override
        public void close() {}

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public SandboxState getState() {
            return null;
        }

        @Override
        public InputStream persistWorkspace() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void hydrateWorkspace(InputStream archive) {}
    }
}
