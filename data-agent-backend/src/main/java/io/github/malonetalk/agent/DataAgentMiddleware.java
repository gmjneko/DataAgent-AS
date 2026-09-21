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

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.state.AgentStateStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class DataAgentMiddleware implements MiddlewareBase {
    private final SessionTranscript transcript;
    private final AgentStateStore store;

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .doOnError(
                        error -> {
                            // 2.0.2 persists successful calls automatically; retain recoverable
                            // state on failure too.
                            store.save(
                                    context.getUserId(),
                                    context.getSessionId(),
                                    "agent_state",
                                    context.getAgentState());
                        });
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext context,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        // Registered outside the compaction middleware: archive blocks before they are shortened.
        transcript.append(
                context.getUserId(), context.getSessionId(), context.getAgentState().getContext());
        return next.apply(input);
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String prompt) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        return Mono.just(
                prompt
                        + "\n当前时区 Asia/Shanghai；今天 %s，昨天 %s，明天 %s。相对日期先换算，日期计算使用 get_date_info。"
                                .formatted(today, today.minusDays(1), today.plusDays(1)));
    }
}
