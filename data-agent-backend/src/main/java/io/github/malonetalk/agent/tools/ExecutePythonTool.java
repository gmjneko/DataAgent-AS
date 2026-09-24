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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxAcquireResult;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.github.malonetalk.agent.E2bSandboxProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutePythonTool implements MarkAgentTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int CLEANUP_TIMEOUT_SECONDS = 15;
    private static final int MAX_CONCURRENT = 5;
    private static final int MAX_CODE_CHARS = 200_000;
    private static final int CHUNK_CHARS = 4000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final E2bSandboxProperties e2b;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);

    public ExecutePythonTool(E2bSandboxProperties e2b) {
        this.e2b = e2b;
    }

    @Tool(
            concurrencySafe = true,
            name = "execute_python",
            description =
                    """
                    Execute Python code for data analysis. \
                    Available libraries: pandas, numpy, scipy. \
                    SQL query results have already been obtained in the conversation; \
                    include the data directly in the Python code. \
                    Print analysis results to stdout using print(). \
                    Only use this when statistical computation \
                    (correlation, regression, distribution tests, etc.) cannot be done in SQL.\
                    """)
    public String executePython(
            @ToolParam(
                            name = "code",
                            description =
                                    """
                                    Python code to execute for data analysis. \
                                    Must be self-contained and include any data inline.\
                                    """)
                    String code,
            RuntimeContext ctx) {

        if (code == null || code.isBlank() || code.length() > MAX_CODE_CHARS) {
            return "Error: code must contain 1 to 200000 characters.";
        }
        if (!semaphore.tryAcquire()) {
            return String.format(
                    "Error: too many concurrent Python executions (max %d), try again later.",
                    MAX_CONCURRENT);
        }
        try {
            return executeInSandbox(code, ctx);
        } finally {
            semaphore.release();
        }
    }

    private String executeInSandbox(String code, RuntimeContext ctx) {
        Sandbox sandbox = boundSandbox(ctx);
        if (sandbox == null) {
            return "Error: E2B sandbox is not active for this call.";
        }
        try {
            return runOnce(sandbox, ctx, code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: execution interrupted.";
        } catch (Exception e) {
            log.error("Python sandbox execution failed", e);
            return "Error: " + e.getMessage();
        }
    }

    private static Sandbox boundSandbox(RuntimeContext ctx) {
        if (ctx == null) {
            return null;
        }
        SandboxAcquireResult acquired = ctx.get(SandboxAcquireResult.class);
        return acquired != null ? acquired.getSandbox() : null;
    }

    private String runOnce(Sandbox sandbox, RuntimeContext ctx, String code) throws Exception {
        String script = e2b.workspaceRoot() + "/pyexec-" + UUID.randomUUID() + ".py";
        String encodedPath = script + ".b64";
        try {
            writeScript(sandbox, ctx, script, encodedPath, code);
            try {
                ExecResult result =
                        sandbox.exec(
                                ctx, "python3 -I " + shellQuote(script), TIMEOUT_SECONDS);
                return formatResult(result);
            } catch (SandboxException.ExecException e) {
                return formatExec(e);
            } catch (SandboxException.ExecTimeoutException e) {
                return "Error: execution timed out after " + TIMEOUT_SECONDS + " seconds.";
            }
        } finally {
            deleteQuietly(sandbox, ctx, script, encodedPath);
        }
    }

    private void writeScript(
            Sandbox sandbox, RuntimeContext ctx, String script, String encodedPath, String code)
            throws Exception {
        String encoded = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        sandbox.exec(ctx, "rm -f " + shellQuote(encodedPath), CLEANUP_TIMEOUT_SECONDS);
        for (int offset = 0; offset < encoded.length(); offset += CHUNK_CHARS) {
            String chunk =
                    encoded.substring(offset, Math.min(encoded.length(), offset + CHUNK_CHARS));
            String append =
                    "open(" + json(encodedPath) + ",'a').write(" + json(chunk) + ")";
            sandbox.exec(ctx, "python3 -c " + shellQuote(append), WRITE_TIMEOUT_SECONDS);
        }
        String decode =
                "import base64,pathlib; pathlib.Path("
                        + json(script)
                        + ").write_bytes(base64.b64decode(pathlib.Path("
                        + json(encodedPath)
                        + ").read_text()))";
        sandbox.exec(ctx, "python3 -c " + shellQuote(decode), WRITE_TIMEOUT_SECONDS);
    }

    private void deleteQuietly(
            Sandbox sandbox, RuntimeContext ctx, String script, String encodedPath) {
        try {
            sandbox.exec(
                    ctx,
                    "rm -f " + shellQuote(script) + " " + shellQuote(encodedPath),
                    CLEANUP_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.debug("Failed to delete sandbox script {}: {}", script, e.getMessage());
        }
    }

    private static String formatResult(ExecResult result) {
        String output = combine(result.stdout(), result.stderr());
        if (result.truncated()) {
            output = output + "\n[Output truncated at 512 KB]";
        }
        return output;
    }

    private static String formatExec(SandboxException.ExecException error) {
        return "Error (exit "
                + error.getExitCode()
                + "):\n"
                + combine(error.getStdout(), error.getStderr());
    }

    private static String combine(String stdout, String stderr) {
        String out = stdout == null ? "" : stdout;
        String err = stderr == null ? "" : stderr;
        if (err.isBlank()) {
            return out;
        }
        if (out.isBlank()) {
            return err;
        }
        return out + "\n" + err;
    }

    private static String json(String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode sandbox script chunk", e);
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
