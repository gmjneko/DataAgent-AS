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
package io.github.malonetalk.service;

import io.github.malonetalk.agent.mcp.McpClientFactory;
import io.github.malonetalk.agent.mcp.McpToolRegistryService;
import io.github.malonetalk.convertor.McpJson;
import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.mapper.McpServerMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;
    private final McpToolRegistryService registry;
    private final McpClientFactory factory;

    @Override
    public List<McpServer> findAll() {
        return mcpServerMapper.selectAll();
    }

    @Override
    public McpServer findById(Integer id) {
        return mcpServerMapper.selectById(id);
    }

    @Override
    public McpServer findByName(String name) {
        return mcpServerMapper.selectByName(name);
    }

    @Override
    public synchronized boolean save(McpServer mcpServer) {
        factory.validate(mcpServer);
        mcpServer.setCreateTime(LocalDateTime.now());
        mcpServer.setUpdateTime(LocalDateTime.now());
        boolean saved = mcpServerMapper.insert(mcpServer) > 0;
        if (saved) {
            registry.refresh(mcpServer);
        }
        return saved;
    }

    @Override
    public synchronized boolean update(McpServer mcpServer) {
        McpServer stored = mcpServerMapper.selectById(mcpServer.getId());
        if (stored == null) {
            return false;
        }
        mcpServer.setEnv(McpJson.preserveSecrets(mcpServer.getEnv(), stored.getEnv()));
        mcpServer.setHeaders(McpJson.preserveSecrets(mcpServer.getHeaders(), stored.getHeaders()));
        mcpServer.setQueryParams(
                McpJson.preserveSecrets(mcpServer.getQueryParams(), stored.getQueryParams()));
        if (Status.ACTIVE.getCode().equals(mcpServer.getStatus())) {
            factory.validate(mcpServer);
        }
        mcpServer.setUpdateTime(LocalDateTime.now());
        boolean saved = mcpServerMapper.update(mcpServer) > 0;
        if (saved) {
            registry.refresh(mcpServerMapper.selectById(mcpServer.getId()));
        }
        return saved;
    }

    @Override
    public synchronized boolean deleteById(Integer id) {
        boolean deleted = mcpServerMapper.deleteById(id) > 0;
        if (deleted) {
            registry.disconnect(id);
        }
        return deleted;
    }

    @Override
    public List<McpServer> findByStatus(String status) {
        return mcpServerMapper.selectByStatus(status);
    }
}
