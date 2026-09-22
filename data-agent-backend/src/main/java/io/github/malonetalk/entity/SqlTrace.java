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
package io.github.malonetalk.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** execute_sql 工具的成功执行记录（表名与结果 JSON 冗余存储，避免数据源改名/删除后丢失上下文）。 */
@Data
public class SqlTrace {

    private Long id;
    private String sessionId;
    private Integer userId;
    private String traceId;
    private Integer datasourceId;
    private String datasourceName;
    private String databaseName;

    /** 逗号分隔的表名。 */
    private String tableNames;

    private String sqlText;
    private String resultJson;
    private Integer rowCount;
    private Boolean truncated;
    private Long durationMs;
    private LocalDateTime createTime;
}
