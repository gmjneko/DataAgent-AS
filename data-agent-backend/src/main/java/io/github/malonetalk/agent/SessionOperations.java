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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/** Coordinates input validation, binding changes and deletion with each session's agent calls. */
@Component
public class SessionOperations {
    private final Map<String, Slot> slots = new HashMap<>();

    public <T> Flux<T> stream(String sessionId, Supplier<Flux<T>> action) {
        return Flux.using(() -> acquire(sessionId), ignored -> action.get(), this::release)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public void run(String sessionId, Runnable action) {
        Slot slot = acquire(sessionId);
        try {
            action.run();
        } finally {
            release(slot);
        }
    }

    private Slot acquire(String id) {
        Slot slot;
        synchronized (slots) {
            slot = slots.computeIfAbsent(id, Slot::new);
            slot.references++;
        }
        try {
            slot.permit.acquire();
            return slot;
        } catch (InterruptedException e) {
            dereference(slot);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Session operation interrupted", e);
        }
    }

    private void release(Slot slot) {
        slot.permit.release();
        dereference(slot);
    }

    private void dereference(Slot slot) {
        synchronized (slots) {
            if (--slot.references == 0) {
                slots.remove(slot.id, slot);
            }
        }
    }

    private static final class Slot {
        final String id;
        final Semaphore permit = new Semaphore(1, true);
        int references;

        Slot(String id) {
            this.id = id;
        }
    }
}
