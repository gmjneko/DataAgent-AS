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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.Sandbox;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Writes a text file into a connected sandbox workspace. */
public final class SandboxFiles {

    private static final int CHUNK_CHARS = 4000;
    private static final int TIMEOUT_SECONDS = 30;
    private static final ObjectMapper JSON = new ObjectMapper();

    private SandboxFiles() {}

    public static void writeText(Sandbox sandbox, RuntimeContext ctx, String path, String content)
            throws Exception {
        String parent = parent(path);
        sandbox.exec(ctx, "mkdir -p " + shellQuote(parent), TIMEOUT_SECONDS);
        String encodedPath = path + ".b64";
        String encoded =
                Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        sandbox.exec(ctx, "rm -f " + shellQuote(encodedPath), TIMEOUT_SECONDS);
        for (int offset = 0; offset < encoded.length(); offset += CHUNK_CHARS) {
            String chunk =
                    encoded.substring(offset, Math.min(encoded.length(), offset + CHUNK_CHARS));
            String append = "open(" + json(encodedPath) + ",'a').write(" + json(chunk) + ")";
            sandbox.exec(ctx, "python3 -c " + shellQuote(append), TIMEOUT_SECONDS);
        }
        String decode =
                "import base64,pathlib; pathlib.Path("
                        + json(path)
                        + ").write_bytes(base64.b64decode(pathlib.Path("
                        + json(encodedPath)
                        + ").read_text()))";
        sandbox.exec(ctx, "python3 -c " + shellQuote(decode), TIMEOUT_SECONDS);
        sandbox.exec(ctx, "rm -f " + shellQuote(encodedPath), TIMEOUT_SECONDS);
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "." : path.substring(0, slash);
    }

    private static String json(String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode sandbox file chunk", e);
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
