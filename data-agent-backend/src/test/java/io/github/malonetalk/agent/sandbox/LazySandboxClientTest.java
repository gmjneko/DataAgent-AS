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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class LazySandboxClientTest {

    @Test
    void frameworkStartDoesNotConnect() throws Exception {
        RecordingSandbox delegate = new RecordingSandbox();
        Sandbox sandbox = new LazySandboxClient.LazyConnectingSandbox(delegate);

        sandbox.start();
        sandbox.stop();
        sandbox.shutdown();

        assertEquals(0, delegate.starts.get());
        assertEquals(0, delegate.shutdowns.get());
        assertNull(sandbox.getState());
    }

    @Test
    void execWithoutDemandDoesNotConnect() throws Exception {
        RecordingSandbox delegate = new RecordingSandbox();
        Sandbox sandbox = new LazySandboxClient.LazyConnectingSandbox(delegate);

        ExecResult result =
                sandbox.exec(RuntimeContext.builder().sessionId("s").build(), "ls", 30);

        assertEquals(1, result.exitCode());
        assertEquals(0, delegate.starts.get());
        assertEquals(0, delegate.execs.get());
    }

    @Test
    void sandboxToolConnectsOnce() throws Exception {
        RecordingSandbox delegate = new RecordingSandbox();
        Sandbox sandbox = new LazySandboxClient.LazyConnectingSandbox(delegate);
        RuntimeContext ctx = RuntimeContext.builder().sessionId("s").build();
        new SandboxDemandMiddleware()
                .onActing(
                        null,
                        ctx,
                        new ActingInput(
                                List.of(
                                        ToolUseBlock.builder()
                                                .id("1")
                                                .name("execute_python")
                                                .input(Map.of())
                                                .build())),
                        input -> Flux.empty())
                .blockLast();

        ExecResult first = sandbox.exec(ctx, "python3 -I script.py", 30);
        ExecResult second = sandbox.exec(ctx, "rm -f script.py", 15);

        assertEquals(0, first.exitCode());
        assertEquals(0, second.exitCode());
        assertEquals(1, delegate.starts.get());
        assertEquals(2, delegate.execs.get());
        assertSame(delegate.state, sandbox.getState());
    }

    @Test
    void sqlToolDoesNotMarkDemand() {
        RuntimeContext ctx = RuntimeContext.builder().sessionId("s").build();
        new SandboxDemandMiddleware()
                .onActing(
                        null,
                        ctx,
                        new ActingInput(
                                List.of(
                                        ToolUseBlock.builder()
                                                .id("1")
                                                .name("execute_sql")
                                                .input(Map.of())
                                                .build())),
                        input -> Flux.empty())
                .blockLast();

        assertNull(ctx.get(SandboxDemand.class));
    }

    private static final class TestState extends SandboxState {}

    private static final class RecordingSandbox implements Sandbox {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger execs = new AtomicInteger();
        private final AtomicInteger shutdowns = new AtomicInteger();
        private final SandboxState state = new TestState();

        @Override
        public void start() {
            starts.incrementAndGet();
        }

        @Override
        public ExecResult exec(
                RuntimeContext runtimeContext, String command, Integer timeoutSeconds) {
            execs.incrementAndGet();
            return new ExecResult(0, "ok", "", false);
        }

        @Override
        public void stop() {}

        @Override
        public void shutdown() {
            shutdowns.incrementAndGet();
        }

        @Override
        public void close() {
            shutdown();
        }

        @Override
        public boolean isRunning() {
            return starts.get() > 0;
        }

        @Override
        public SandboxState getState() {
            return state;
        }

        @Override
        public InputStream persistWorkspace() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void hydrateWorkspace(InputStream archive) {}
    }
}
