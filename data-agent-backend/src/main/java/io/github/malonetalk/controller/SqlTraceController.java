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
package io.github.malonetalk.controller;

import io.github.malonetalk.agent.SessionOwnership;
import io.github.malonetalk.common.Result;
import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.dto.SqlTraceResponse;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.service.SqlTraceService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/agent")
public class SqlTraceController {

    private final SqlTraceService sqlTraceService;
    private final SessionOwnership ownership;

    @GetMapping("/session/{sessionId}/sql-trace")
    public Result<PageResponse<SqlTraceResponse>> listSessionTraces(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Integer userId = UserContext.requireScopedUserId();
        ownership.requireOwnership(userId, sessionId);
        return Result.success(sqlTraceService.getSessionTracePage(sessionId, page, pageSize));
    }

    @GetMapping("/session/{sessionId}/sql-trace/{id}")
    public Result<SqlTraceResponse> getTraceDetail(
            @PathVariable String sessionId, @PathVariable long id) {
        Integer userId = UserContext.requireScopedUserId();
        ownership.requireOwnership(userId, sessionId);
        return Result.success(sqlTraceService.getSessionTraceDetail(sessionId, id));
    }
}
