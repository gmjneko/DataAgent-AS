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
package io.github.malonetalk.convertor;

import io.github.malonetalk.dto.McpServerRequest;
import io.github.malonetalk.dto.McpServerResponse;
import io.github.malonetalk.entity.McpServer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = McpJson.class)
public interface McpServerConverter {
    @Mapping(target = "args", expression = "java(McpJson.encode(request.args()))")
    @Mapping(target = "env", expression = "java(McpJson.encode(request.env()))")
    @Mapping(target = "headers", expression = "java(McpJson.encode(request.headers()))")
    @Mapping(target = "queryParams", expression = "java(McpJson.encode(request.queryParams()))")
    @Mapping(target = "enableTools", expression = "java(McpJson.encode(request.enableTools()))")
    @Mapping(target = "disableTools", expression = "java(McpJson.encode(request.disableTools()))")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    McpServer toEntity(McpServerRequest request);

    @Mapping(target = "args", expression = "java(McpJson.list(server.getArgs()))")
    @Mapping(target = "env", expression = "java(McpJson.masked(server.getEnv()))")
    @Mapping(target = "headers", expression = "java(McpJson.masked(server.getHeaders()))")
    @Mapping(target = "queryParams", expression = "java(McpJson.masked(server.getQueryParams()))")
    @Mapping(target = "enableTools", expression = "java(McpJson.list(server.getEnableTools()))")
    @Mapping(target = "disableTools", expression = "java(McpJson.list(server.getDisableTools()))")
    McpServerResponse toResponse(McpServer server);
}
