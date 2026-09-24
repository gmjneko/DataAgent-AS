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

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxClient;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import java.io.InputStream;

/**
 * E2B client whose sandboxes ignore the framework's eager {@code start()} and connect on the first
 * command issued after {@link SandboxDemand} is set.
 */
public final class LazySandboxClient implements SandboxClient<E2bSandboxClientOptions> {

    private final E2bSandboxClient delegate;

    public LazySandboxClient(E2bSandboxClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public Sandbox create(
            WorkspaceSpec workspaceSpec,
            SandboxSnapshotSpec snapshotSpec,
            E2bSandboxClientOptions options) {
        return new LazyConnectingSandbox(delegate.create(workspaceSpec, snapshotSpec, options));
    }

    @Override
    public Sandbox resume(SandboxState state) {
        return new LazyConnectingSandbox(delegate.resume(state));
    }

    @Override
    public void delete(Sandbox sandbox) {
        delegate.delete(unwrap(sandbox));
    }

    @Override
    public String serializeState(SandboxState state) {
        return delegate.serializeState(state);
    }

    @Override
    public SandboxState deserializeState(String json) {
        return delegate.deserializeState(json);
    }

    @Override
    public SandboxState deserializeState(String json, SandboxSnapshotSpec snapshotSpec) {
        return delegate.deserializeState(json, snapshotSpec);
    }

    private static Sandbox unwrap(Sandbox sandbox) {
        return sandbox instanceof LazyConnectingSandbox lazy ? lazy.delegate() : sandbox;
    }

    static final class LazyConnectingSandbox implements Sandbox {
        private final Sandbox delegate;
        private boolean connected;

        LazyConnectingSandbox(Sandbox delegate) {
            this.delegate = delegate;
        }

        Sandbox delegate() {
            return delegate;
        }

        @Override
        public void start() {
            // Harness acquires a sandbox at the beginning of every call. Connecting happens in
            // exec, after a sandbox tool has been requested.
        }

        @Override
        public ExecResult exec(
                RuntimeContext runtimeContext, String command, Integer timeoutSeconds)
                throws Exception {
            if (!demanded(runtimeContext)) {
                return new ExecResult(1, "", "E2B sandbox is not connected for this tool", false);
            }
            connect();
            return delegate.exec(runtimeContext, command, timeoutSeconds);
        }

        @Override
        public InputStream persistWorkspace() throws Exception {
            if (!connected) {
                return InputStream.nullInputStream();
            }
            return delegate.persistWorkspace();
        }

        @Override
        public void hydrateWorkspace(InputStream archive) throws Exception {
            if (!connected) {
                return;
            }
            delegate.hydrateWorkspace(archive);
        }

        @Override
        public void stop() throws Exception {
            if (connected) {
                delegate.stop();
            }
        }

        @Override
        public void shutdown() throws Exception {
            if (connected) {
                delegate.shutdown();
            }
        }

        @Override
        public void close() throws Exception {
            if (connected) {
                delegate.close();
            }
        }

        @Override
        public boolean isRunning() {
            return connected && delegate.isRunning();
        }

        @Override
        public SandboxState getState() {
            return connected ? delegate.getState() : null;
        }

        private static boolean demanded(RuntimeContext runtimeContext) {
            return runtimeContext != null && runtimeContext.get(SandboxDemand.class) != null;
        }

        private void connect() throws Exception {
            synchronized (this) {
                if (connected) {
                    return;
                }
                delegate.start();
                connected = true;
            }
        }
    }
}
