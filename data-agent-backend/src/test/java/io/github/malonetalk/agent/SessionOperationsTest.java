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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class SessionOperationsTest {
    @Test
    void serializesSameSessionWhileOtherSessionsRun() throws Exception {
        SessionOperations operations = new SessionOperations();
        Sinks.One<Integer> first = Sinks.one();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        var a =
                operations.stream(
                                "same",
                                () -> {
                                    calls.incrementAndGet();
                                    entered.countDown();
                                    return first.asMono().flux();
                                })
                        .collectList()
                        .toFuture();
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        var b =
                operations.stream(
                                "same",
                                () -> {
                                    calls.incrementAndGet();
                                    return Flux.just(2);
                                })
                        .collectList()
                        .toFuture();
        assertEquals(
                3,
                operations.stream("other", () -> Flux.just(3)).blockFirst(Duration.ofSeconds(3)));
        assertEquals(1, calls.get());
        first.tryEmitValue(1);
        assertEquals(1, a.get(3, TimeUnit.SECONDS).getFirst());
        assertEquals(2, b.get(3, TimeUnit.SECONDS).getFirst());
    }

    @Test
    void cancellationAndFailureReleaseTheSession() throws Exception {
        SessionOperations operations = new SessionOperations();
        CountDownLatch entered = new CountDownLatch(1);
        var running =
                operations.stream(
                                "s",
                                () -> {
                                    entered.countDown();
                                    return Flux.never();
                                })
                        .subscribe();
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        running.dispose();
        assertEquals(
                1, operations.stream("s", () -> Flux.just(1)).blockFirst(Duration.ofSeconds(3)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        operations.stream("s", () -> Flux.error(new IllegalStateException()))
                                .blockLast());
        assertEquals(
                2, operations.stream("s", () -> Flux.just(2)).blockFirst(Duration.ofSeconds(3)));
    }
}
